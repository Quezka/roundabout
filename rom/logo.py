#!/usr/bin/env python3
# boot logo = partition `ImcPartID013` (nand0p11) ; format `!SPLASH!` :
#   text header "!SPLASH!\n800x1280\n300x300\n110x220 x7\n" ; pixels @ offset 512 ; 32 bit, alpha last
#   img 0 = 800x1280 boot logo (prestigio) ; then android robot + charge icons
# out = backup copy - img 0 swapped for bootanimation steady frame ; * other bytes same
# no checksum in partition (tail = more pixels) => in-place swap safe
# use: rom/logo.py rom/backup/<date> -> rom/out/splash.img
import hashlib, io, sys, zipfile
from pathlib import Path
from PIL import Image

HERE = Path(__file__).resolve().parent
PART = "ImcPartID013"
OFF, W, H = 512, 800, 1280


def frame() -> Image.Image:
    with zipfile.ZipFile(HERE / "assets/bootanimation.zip") as z:
        name = sorted(n for n in z.namelist() if n.startswith("part1/") and n.endswith(".png"))[0]
        return Image.open(io.BytesIO(z.read(name))).convert("RGB")


def main() -> int:
    backup = Path(sys.argv[1]) if len(sys.argv) > 1 else sys.exit("use: logo.py rom/backup/<date>")
    stock = (backup / f"{PART}.img").read_bytes()
    if not stock.startswith(b"!SPLASH!\n800x1280\n"):
        sys.exit(f"{PART} != splash 800x1280: refuse")
    img = frame()
    if img.size != (W, H):
        img = img.resize((W, H))
    # grey frame => r/b order no matter ; alpha last = 0xff like stock
    px = img.convert("RGBA").tobytes()
    out = bytearray(stock)
    out[OFF:OFF + len(px)] = px
    assert len(out) == len(stock) and out[:OFF] == stock[:OFF] and out[OFF + len(px):] == stock[OFF + len(px):]
    dst = HERE / "out/splash.img"
    dst.parent.mkdir(exist_ok=True)
    dst.write_bytes(out)
    print(f"{dst} {hashlib.sha256(out).hexdigest()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
