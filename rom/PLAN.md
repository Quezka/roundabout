## roundabout rom = plan
goal: tablet `PMT3797` = headunit only ; stock-based rom ; roundabout = only ui
branch = `rom` @ `Quezka/roundabout` ; wait user go before each phase
! no flash before backup @ nas + checked

## phase 0 = offline (no tablet, no nas)
add: tablet settings in roundabout = `roundabout/system/*` (started: `SystemAccess.kt`)
sections: wifi ; bluetooth ; display (brightness, timeout, stay on charging) ; sound (* streams) ; date + time + zone ; apps (open, stop, clear, rm, disable) ; roundabout access (grant all) ; about + storage ; power (reboot, off) ; developer (adb on/off)
activity catch `android.settings.*` intents <- stock settings app = rm
manifest add: `WRITE_SECURE_SETTINGS` `SET_TIME` `SET_TIME_ZONE` `REBOOT` `DELETE_PACKAGES` `BLUETOOTH_PRIVILEGED` ; priv-app => granted ; else su fallback
rail settings btn -> roundabout settings ; new row "Tablet" -> tablet settings
add: `rom/backup.sh` `rom/unpack.sh` `rom/build.sh` `rom/flash.sh` ; `rom/keep.txt` ; `rom/remove.txt`
need host tools: `simg2img` `make_ext4fs` | `mke2fs`+`e2fsdroid` ; `mtkclient` ; `unpackbootimg`/`magiskboot`
test: unit tests pass ; apk @ tablet later

## phase 1 = tablet @ usb (home)
read: `ro.board.platform` `ro.hardware` cpuinfo ram ; `/proc/partitions` ; `/dev/block/platform/*/by-name/*` | `/proc/dumchar_info` (mtk old)
read: `pm list packages -f -s` ; `pm list packages -d` ; `ls /system/app /system/priv-app` ; `getprop` full -> `rom/device/`
check: gms present ? ; webview pkg ; ime pkg ; magisk ver + `boot` patched
check: bootloader unlock state ; `fastboot` reachable ? ; mtk brom (vol key + usb) reachable via `mtkclient` ?

## phase 2 = backup -> nas
need: nas mount up (home lan) ; target dir `nas:/backup/tablet-pmt3797/<date>/` ? <- ask path
dump * partitions via `su` + `dd` -> `adb pull` (preloader, lk, boot, recovery, logo, nvram, nvdata, protect1/2, seccfg, system, cache?, userdata = skip|optional)
! nvram + nvdata + protect = imei + wifi mac + calib => irreplaceable
dump scatter: `mtkclient printgpt` | build from `/proc/dumchar_info`
sha256 each img -> `SHA256SUMS` ; copy nas + laptop ; verify sums both
also: stock firmware from prestigio site | needrom -> nas (second unbrick path)
keep: magisk modules `rb_mini_boot` `rb_no_navbar` + `/data/adb` tar

## phase 3 = build rom (laptop)
`system.img` -> raw (`simg2img` if sparse) -> mount loop rw | `debugfs`
rm: `remove.txt` apps (launcher3, systemui, settings, setupwizard, browser, gallery, email, calendar, music, video, fm, sound recorder, stk, deskclock, contacts, dialer, mms, downloads ui, ...) <- final list after phase 1
keep: framework, settingsprovider, mediaprovider, telephonyprovider ?, bluetooth, packageinstaller, shell, defaultcontainerservice, externalstorageprovider, latinime, webview, certinstaller, keychain, mtk core services (`com.mediatek.*` needed by radio/wifi ?) <- check each
add: roundabout -> `/system/priv-app/Roundabout/Roundabout.apk` + `lib/` ; perms whitelist n/a @ 5.1
add: `build.prop`: `qemu.hw.mainkeys=1` ; `ro.sf.lcd_density` same ; `persist.sys.timezone` ; `ro.setupwizard.mode=DISABLED`
add: bootanimation mini -> `/system/media/bootanimation.zip` (=> magisk module `rb_mini_boot` rm)
add: `logo.bin` mini (mtk logo tool) <- bootloader + kernel logo
cfg default: `settings` defaults via `/system/etc/` init script | first-boot script = overscan, rotation lock, grant all, systemui gone
repack same fs size + same label + selinux contexts (`file_contexts` from boot ramdisk)
boot: keep magisk-patched boot | repatch ; optional `init.rc` service = first-boot script

## phase 4 = flash (user go only)
flash `system` (+ `logo`) via `dd` @ recovery | `mtkclient w` | sp flash tool ; no preloader touch
first boot: wipe dalvik ; check roundabout = home ; aa usb + wifi ; volume ; brightness ; rail ; boot anim
fail -> restore `system.img` from nas backup

## phase 5 = after
push branch `rom` ; scripts + lists in repo ; imgs != git (nas only)
memory update ; old magisk modules rm if rom covers them
