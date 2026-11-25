#!/usr/bin/env python3
"""Build the Roundabout system image from a stock dump. Never touches the tablet.

Works on a copy of the raw ext4 system.img with debugfs (no root, no loop mount),
so file owners, modes and SELinux labels of everything else stay as they were.

use: rom/build.py --backup rom/backup/<date> --apk app/build/.../roundabout.apk [--bootanim file.zip] [--odex out/Roundabout.odex]
odex = dex2oat on the tablet for this exact apk (dex-location = system path) -> no "optimizing apps" @ first boot
  adb push apk /data/local/tmp/rb.apk ; su -c "dex2oat --dex-file=/data/local/tmp/rb.apk --dex-location=/system/priv-app/Roundabout/Roundabout.apk --oat-file=/data/local/tmp/Roundabout.odex --instruction-set=x86"
out: rom/out/system.img + rom/out/build.log
"""
import argparse, os, shutil, subprocess, sys, tempfile, zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
SYSTEM_LABEL = "u:object_r:system_file:s0"
# stock xattrs end with a NUL byte -> same bytes here ; debugfs reads it from a file
LABEL_FILE = None

def run(*cmd, **kw):
    return subprocess.run(cmd, check=True, text=True, capture_output=True, **kw).stdout

def debugfs(img, cmds, log):
    with tempfile.NamedTemporaryFile("w", delete=False, suffix=".cmd") as f:
        f.write("\n".join(cmds) + "\n")
    out = subprocess.run(["debugfs", "-w", "-f", f.name, img], text=True, capture_output=True)
    os.unlink(f.name)
    log.write(out.stdout + out.stderr)
    return out

def ls(img, path):
    out = subprocess.run(["debugfs", "-R", f"ls -p {path}", img], text=True, capture_output=True).stdout
    items = []
    for line in out.splitlines():
        parts = line.strip("/").split("/")
        # /inode/mode/uid/gid/name/size/
        if len(parts) >= 5 and parts[4] not in (".", ".."):
            items.append((parts[4], int(parts[1], 8) & 0o170000 == 0o040000))
    return items

def exists(img, path):
    out = subprocess.run(["debugfs", "-R", f"stat {path}", img], text=True, capture_output=True)
    return "Inode:" in out.stdout

def rm_tree(img, path):
    cmds = []
    for name, is_dir in ls(img, path):
        child = f"{path}/{name}"
        if is_dir:
            cmds += rm_tree(img, child)
        else:
            cmds.append(f"rm {child}")
    cmds.append(f"rmdir {path}")
    return cmds

def lines(name):
    with open(os.path.join(HERE, name)) as f:
        return [l.strip() for l in f if l.strip() and not l.startswith("#")]

def put(path, src, mode="0100644"):
    # owner root:root, label system_file
    return [f"write {src} {path}", f"sif {path} mode {mode}", f"sif {path} uid 0", f"sif {path} gid 0",
            f"ea_set -f {LABEL_FILE} {path} security.selinux"]

def mkdir(path):
    return [f"mkdir {path}", f"sif {path} mode 040755", f"sif {path} uid 0", f"sif {path} gid 0",
            f"ea_set -f {LABEL_FILE} {path} security.selinux"]

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--backup", required=True)
    ap.add_argument("--apk", required=True)
    ap.add_argument("--bootanim")
    ap.add_argument("--odex")
    # saved tablet profile -> /system/etc/roundabout/profile.json ; get: settings -> tablet -> about -> save, then
    # adb exec-out su -c "cat /data/data/io.github.quezka.roundabout/files/tablet-profile.json" > rom/out/profile.json
    ap.add_argument("--profile")
    a = ap.parse_args()

    for tool in ("debugfs", "e2fsck"):
        if not shutil.which(tool):
            sys.exit(f"need {tool} (e2fsprogs)")
    src = os.path.join(a.backup, "system.img")
    if not os.path.exists(src):
        sys.exit(f"no {src}")
    if b"\x3a\xff\x26\xed" == open(src, "rb").read(4):
        sys.exit("system.img is sparse: run simg2img first")

    os.makedirs(os.path.join(HERE, "out"), exist_ok=True)
    img = os.path.join(HERE, "out", "system.img")
    shutil.copyfile(src, img)
    log = open(os.path.join(HERE, "out", "build.log"), "w")
    work = tempfile.mkdtemp()
    global LABEL_FILE
    LABEL_FILE = os.path.join(work, "label")
    with open(LABEL_FILE, "wb") as f:
        f.write(SYSTEM_LABEL.encode() + b"\0")

    keep = set(lines("keep.txt"))
    cmds = []
    for rel in lines("remove.txt"):
        if rel in keep:
            print(f"keep (safety list): {rel}")
            continue
        if exists(img, f"/{rel}"):
            print(f"rm /system/{rel}")
            cmds += rm_tree(img, f"/{rel}")
    # roundabout = priv-app ; native libs pre-extracted (5.x does not extract for system apps)
    base = "/priv-app/Roundabout"
    if exists(img, base):
        cmds += rm_tree(img, base)
    cmds += mkdir(base)
    cmds += put(f"{base}/Roundabout.apk", os.path.abspath(a.apk))
    # abi from the dump: intel sofia = x86 -> lib/x86 ; arm -> lib/arm
    abi = "x86"
    gp = os.path.join(a.backup, "info", "getprop.txt")
    if os.path.exists(gp) and "[ro.product.cpu.abi]: [x86]" not in open(gp, errors="replace").read():
        abi = "armeabi-v7a"
    libdir = "x86" if abi == "x86" else "arm"
    with zipfile.ZipFile(a.apk) as z:
        libs = [n for n in z.namelist() if n.startswith(f"lib/{abi}/") and n.endswith(".so")]
        if libs:
            cmds += mkdir(f"{base}/lib") + mkdir(f"{base}/lib/{libdir}")
            for n in libs:
                out = os.path.join(work, os.path.basename(n))
                with open(out, "wb") as f:
                    f.write(z.read(n))
                cmds += put(f"{base}/lib/{libdir}/{os.path.basename(n)}", out)
    print(f"add {base} + {len(libs)} libs")
    # prebuilt oat @ <dir>/<isa>/<name>.odex ; ! must match this apk (dex checksum) else art ignores it
    if a.odex:
        isa = "x86" if abi == "x86" else "arm"
        cmds += mkdir(f"{base}/{isa}")
        cmds += put(f"{base}/{isa}/Roundabout.odex", os.path.abspath(a.odex))
        print(f"add {base}/{isa}/Roundabout.odex")

    if a.profile:
        if exists(img, "/etc/roundabout"):
            cmds += rm_tree(img, "/etc/roundabout")
        cmds += mkdir("/etc/roundabout")
        cmds += put("/etc/roundabout/profile.json", os.path.abspath(a.profile))
        print("add /etc/roundabout/profile.json")

    if a.bootanim:
        if exists(img, "/media/bootanimation.zip"):
            cmds.append("rm /media/bootanimation.zip")
        cmds += put("/media/bootanimation.zip", os.path.abspath(a.bootanim))
        print("add bootanimation")

    # build.prop: no nav bar, no setup wizard
    prop = os.path.join(work, "build.prop")
    subprocess.run(["debugfs", "-R", f"dump /build.prop {prop}", img], check=True, capture_output=True)
    text = open(prop, encoding="utf-8", errors="surrogateescape").read().splitlines()
    # ! no hwrotation touch <- panel orientation
    want = {"qemu.hw.mainkeys": "1", "ro.setupwizard.mode": "DISABLED"}
    out_lines = [l for l in text if l.split("=", 1)[0] not in want]
    out_lines += ["", "# roundabout"] + [f"{k}={v}" for k, v in want.items()]
    open(prop, "w", encoding="utf-8", errors="surrogateescape").write("\n".join(out_lines) + "\n")
    cmds += ["rm /build.prop"] + put("/build.prop", prop, "0100644")

    r = debugfs(img, cmds, log)
    if "rror" in r.stderr:
        print(r.stderr)
        sys.exit("debugfs errors, see out/build.log")
    fsck = subprocess.run(["e2fsck", "-fn", img], text=True, capture_output=True)
    log.write(fsck.stdout + fsck.stderr)
    if fsck.returncode not in (0, 1):
        sys.exit("e2fsck found errors, see out/build.log")
    print(run("sha256sum", img).strip())
    print("ok: rom/out/system.img")

if __name__ == "__main__":
    main()
