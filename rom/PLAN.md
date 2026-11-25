## roundabout rom = plan
goal: tablet `PMT3797` = headunit only ; stock-based rom ; roundabout = only ui
branch = `rom` @ `Quezka/roundabout` ; wait user go before each phase
! no flash before backup @ laptop + checked ; nas = no need (user 2026-11-25)

## rule = no root need
roundabout @ `/system/priv-app` => signature|system perms -> api direct ; no su @ runtime
`SystemAccess` = api first (+ reflection hidden api) ; su = fallback only @ dev install
rom first boot -> roundabout self: overscan + grant listener/accessibility/admin + rotation lock
signature-only perms (`CLEAR_APP_USER_DATA` ...) -> need platform key ; `ro.build.tags` = `test-keys` ? => sign roundabout w/ aosp platform key + `android.uid.system` ; else su fallback | skip
root = rom default: boot img = magisk-patched ; magisk app = system app ; superuser toast = none ; only for maintenance

## tablet facts (read 2026-11-24)
soc = intel atom x3 `Sofia3GR` x86 (not mtk) ; ram `1.4 GB` ; nand `7.3 GB`
build = `PMT3797_3G.20160509.1.0.02` ; `release-keys` user => no platform key => signature-only perms = su fallback
partitions = `/dev/block/platform/soc0/e0000000.noc/by-name/ImcPartIDnnn` ; system = `068` (nand0p15, 2 GB) ; data = `069` ; cache = `070` ; nvm = `/nvm_fs_partition`
no `/sdcard` path ; no awk/head/tar in toolbox -> `/data/adb/magisk/busybox`
recovery path: intel flash tool | fastboot ? (check `adb reboot bootloader` @ phase 1) ; mtkclient = n/a
setup + tablet settings = tested @ tablet ok
boot = `ImcPartID071` ; recovery = `ImcPartID121` ; nvm (imei, calib) = `ImcPartID076` -> `/nvm_fs_partition`
fastboot = no : `adb reboot bootloader` -> normal boot (tested 2026-11-25) ; unbrick = intel flash tool + stock `.fls` firmware (not found yet) | adb + su (adbd in boot ramdisk, keys @ /data) if system bootloops

## status 2026-11-25 (flashed)
idea later = wired aa + car power (aca cable test, then hw mod) -> memory `roundabout-wired-aa-mod`
flash = ok 08:46 (system + splash) ; boots ; roundabout = home ; aa ok
tablet settings = tab `Tablet` @ roundabout settings (beside basic/advanced) ; `TabletSettingsActivity` = trampoline for `android.settings.*`
aa touch off fix <- rail strip counted twice @ boot (`railReserved` + skip double-padded layout)
oobe no show <- `rb-setup-done` set by dev tests ; about -> run setup again
dev apk = `/data/app` update over priv-app (same sig) ; next system flash only if need
stale dalvik-cache of removed apps = rm ok ; odex in image = ok (`build.py --odex`) ; reflash #2 = ok, verified on device (sha match)
roundabout stays `/data/app` update over priv-app <- `pm uninstall` blocked by device admin ; still SYSTEM + PRIVILEGED => ok
gestures + tablet tab = installed, untested by hand (aa in front)
out/system.img rebuilt w/ apk `2d602904`
backup = `rom/backup/2026-11-25_0302` (all 17 partitions, sums ok, nvm read twice) + copy `~/Backup/tablet-pmt3797/` ; nas = no (user: laptop only)
! backup includes userdata (`069`, 5 GB, private) <- ImcPartID names != `userdata` skip
rom image = `rom/out/system.img` built from real dump ; e2fsck clean ; labels match stock (trailing NUL)
remove = systemui, settings, launcher3, dialer, contacts, cell broadcast, camera2, downloads ui, partner bookmarks, musicfx, stresstest, backup confirm, shared storage backup
keep = intel `ituxd` (thermal), vpn dialogs (oh dummy vpn), contacts provider + telecom (bt phonebook)
splash = `ImcPartID013` (`!SPLASH!` 800x1280 rgba @ `512`) ; `rom/logo.py` -> `out/splash.img` (mini) ; flash.sh `--splash`
next = user go -> `rom/flash.sh --backup rom/backup/2026-11-25_0302 --i-have-a-backup --splash` ; tablet = usb need ; then find stock `.fls` firmware for a second unbrick path

## status 2026-11-24
phase 0 = done ; apk builds ; unit tests `4937` pass ; untested @ tablet
tablet settings = `roundabout/system/TabletSettingsActivity.kt` + `Panels.kt` + `MorePanels.kt` ; row "Tablet" @ roundabout settings general
setup = `roundabout/setup/SetupActivity.kt` ; runs once before oh onboarding ; rail = clock only during setup
best cfg = `roundabout/system/TabletProfile.kt` ; applied @ setup start + about -> "apply tablet profile"
pairing dialog = `roundabout/system/BluetoothPairing.kt`
scripts: `rom/backup.sh` (read only) ; `rom/build.py` (debugfs, no root, tested on fake ext4) ; `rom/flash.sh` (guarded)
boot = kept as is => current magisk-patched boot = root by default ; no repatch need
remove.txt final @ phase 1 ; java need `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64` (25 breaks gradle)
! brick recovery = fastboot | intel flash tool + backup imgs ; flash.sh needs adb => no help if no boot ; check fastboot before flash

## phase 0 = offline (no tablet, no nas)
add: tablet settings in roundabout = `roundabout/system/*` (started: `SystemAccess.kt`)
sections: wifi ; bluetooth ; display (brightness, timeout, stay on charging) ; sound (* streams) ; date + time + zone ; apps (open, stop, clear, rm, disable) ; roundabout access (grant all) ; about + storage ; power (reboot, off) ; developer (adb on/off)
activity catch `android.settings.*` intents <- stock settings app = rm
manifest add: `WRITE_SECURE_SETTINGS` `SET_TIME` `SET_TIME_ZONE` `REBOOT` `DELETE_PACKAGES` `BLUETOOTH_PRIVILEGED` ; priv-app => granted ; else su fallback
rail settings btn -> roundabout settings ; new row "Tablet" -> tablet settings
add: oobe = roundabout first boot wizard ; replace stock setupwizard (rm @ rom) ; same ui as roundabout (dark, accent `#FF7A1A`, manrope, big touch, no keyboard where possible)
oobe steps:
  1 welcome: mini wings anim + "Roundabout" ; tap = go
  2 language: big list (it, en, ...) -> `LocalePicker.updateLocale` reflection (`CHANGE_CONFIGURATION`)
  3 wifi: scan list + password ; skip ok (aa wireless need phone hotspot | tablet ap later)
  4 time: auto (network) on default ; zone list, default `Europe/Rome` ; 24h default
  5 phone: bluetooth pair phone (big code confirm) ; skip ok
  6 android auto: usb | wireless pick -> test connect ; show phone steps (enable aa, dev settings if need) ; skip ok
  7 car: dial pick (speed | analog | digital) ; favorites pick (maps, music) ; units km/h
  8 done: summary + "Start" -> home
oobe extra: grants = silent (priv-app) => no permission screens ; rail hidden during oobe ; back btn = prev step ; progress dots
oobe state: pref `rb-oobe-done` ; settings row "Run setup again" ; skip all = long-press welcome (dev)
oobe vs oh `OnboardingActivity` (auto-optimize) -> run inside step 6 | after done ; no double wizard
add: `rom/backup.sh` `rom/unpack.sh` `rom/build.sh` `rom/flash.sh` ; `rom/keep.txt` ; `rom/remove.txt`
need host tools: `simg2img` `make_ext4fs` | `mke2fs`+`e2fsdroid` ; `mtkclient` ; `unpackbootimg`/`magiskboot`
test: unit tests pass ; apk @ tablet later

## phase 1 = tablet @ usb (home)
read: `ro.board.platform` `ro.hardware` cpuinfo ram ; `/proc/partitions` ; `/dev/block/platform/*/by-name/*` | `/proc/dumchar_info` (mtk old)
read: `pm list packages -f -s` ; `pm list packages -d` ; `ls /system/app /system/priv-app` ; `getprop` full -> `rom/device/`
check: gms present ? ; webview pkg ; ime pkg ; magisk ver + `boot` patched
check: `ro.build.tags` + `ro.build.type` ; platform cert = aosp test key ? (`/system/framework/framework-res.apk` cert sha vs aosp `platform.x509.pem`)
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
boot: stock boot -> `magiskboot` patch @ laptop (same magisk ver) => root default ; magisk apk -> `/system/app/Magisk` + magisk db: superuser notify = none
no first-boot root script need <- roundabout priv-app does cfg itself

## phase 4 = flash (user go only)
flash `system` (+ `logo`) via `dd` @ recovery | `mtkclient w` | sp flash tool ; no preloader touch
first boot: wipe dalvik ; oobe * steps ; check roundabout = home ; aa usb + wifi ; volume ; brightness ; rail ; boot anim
fail -> restore `system.img` from laptop backup

## phase 5 = after
push branch `rom` ; scripts + lists in repo ; imgs != git (laptop only)
memory update ; old magisk modules rm if rom covers them
