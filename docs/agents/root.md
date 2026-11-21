## goal
root tablet `PMT3797_3G` (device `JU70A1G`, Prestigio) use Magisk -> then reinstall roundabout
user = owner ; user ok: full flash + wipe

## state 2026-11-19 (DONE)
root = ok (`su` -> `uid=0(root)` context `u:r:magisk:s0`)
magisk app = installed `25.2`
roundabout = installed + running ; notification listener = set (`...MediaListener`)
system = still `20160509` (old) <- `system.fls` no flash ; boot = `20161205` magisk patched
note: bootloader unlock = no need (patched boot boots)

## files
firmware zip: `~/Downloads/Prestigio-Multipad-PMT3797.zip` (build `20161205.6.0.00`)
extracted: `~/tablet-root/fw/PMT3797_3G.20161205.6.0.00(for_ASC)/FW/` (`boot.img`, `boot.fls`, `system.fls`, `psi_flash.fls`, `slb.fls`, ...)
flash tool: `~/tablet-root/pftl/opt/intel/platformflashtoollite/bin/` (`downloadTool`, `flsTool`) <- `pftl.deb` Platform Flash Tool Lite `5.8.9.0`
magisk: `~/tablet-root/magisk/Magisk-v25.2.apk` ; unzip `lib/x86/*` + `assets/*` (last ver for Android 5)
udev: `/etc/udev/rules.d/99-intel-flash.rules` = ok (vendor `8087`)
`boot-adb.img` = old test, no use

## plan
1. push `boot.img` + magisk `lib/x86` + `assets` -> `/data/local/tmp` ; rename libs (`libmagiskboot.so` -> `magiskboot` ...) ; run `sh boot_patch.sh boot.img` -> `new-boot.img`
2. pull `new-boot.img` -> wrap in fls use `flsTool` (see `flsTool --help` ; keep same psi/slb as `boot.fls`) -> `boot.fls` patched
3. tablet off -> usb -> flash all fls use `downloadTool` (same order as `flash2.log` @ `~/tablet-root/`) ; boot = patched
4. first boot ~10 min ; install `Magisk-v25.2.apk` ; check `adb shell su -c id`
5. reinstall roundabout: see `README.md` build + `adb install` + notification listener

## risk
flash = wipe all data
old run: `downloadTool` stop @ `Get flash id` <- killed (signal 15) ; need run until done, no timeout
patched boot fail -> reflash stock `boot.fls` => recover

## state 2026-11-20
roundabout = rm @ tablet ; wip = branch `aa-wireless-wip`
tablet launcher = roundabout-on-oh (`io.github.quezka.roundabout`, branch `open-headunit`, worktree `../roundabout-oh`) ; alias `com.andrerinas.openheadunit.CarLauncherAlias` = enabled
old oh `com.andrerinas.headunitrevived` = rm 2026-11-21 ; prefs + `device_policies.xml` bak @ tablet `/data/local/tmp/ohbak/`
! device admin moved by hand: `/data/system/device_policies.xml` -> new pkg
disabled: `launcher3` `camera2` `cellbroadcastreceiver` `musicfx` `partnerbookmarks`
undo: `adb shell su -c "pm enable com.android.launcher3"`
! phone often on usb too => use `adb -s PMP270262005418`
