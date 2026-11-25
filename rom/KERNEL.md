## kernel plan (2026-11-25) = user ask: own kernel, direct cpu control, max speed
goal: max speed for headunit use (aa decode, ui, boot) ; ! keep thermal safe <- car heat
wait user go per phase ; ! no boot partition write before recovery-partition test ok

## facts
cmdline (boot img): `console=ttyFIQ0,115200n8 idle=halt earlyprintk=xgold notsc apic=sofia androidboot.hardware=sofiaboard nolapic_pm firmware_class.path=/system/vendor/firmware androidboot.selinux=permissive x86_intel_xgold_timer=soctimer_only vmalloc=512m slub_max_order=2`
linux = guest of intel mobilevisor (`apic=sofia`, vmm) => cpu freq + power = maybe via vmm, != direct ; check `scaling_driver`
boot img header: kernel 8 MB @ 4096, not plain bzImage (`fc f6 86 11`) ; no gzip/xz/lzma payload found => format ? (signed | intel xgold wrap ?)
magisk-patched boot boots => ramdisk not verified ; kernel verify = ?
pmic `rk818` ; charger `bq2415x` ; wifi `iwlwifi`/`iwlmvm` = modules (vermagic must match new kernel)
source candidates: `bmoraine/linux-sofia-3gr` (3.14.y) ; `tinocyngn/sofia-kernel` ; prestigio gpl = not found
no `/proc/config.gz` (check) => config must be rebuilt from running kernel (modules, kallsyms, sysfs)

## safe test path
custom kernel -> recovery partition `ImcPartID121` only (stock recovery = backup) -> `adb reboot recovery`
ok -> same kernel + current magisk ramdisk -> boot `ImcPartID071`
fail -> normal boot untouched ; restore recovery from backup
! no fastboot + no stock .fls => boot partition brick = no way back

## phase a = tune running kernel (no build, safe, fast win)
inventory: `/proc/version`, cpufreq driver/governors/freq table, thermal zones + cooling, io scheduler, zram, lmk, gpu devfreq, top procs
cpu: governor `interactive` tuned (hispeed = max, go_hispeed_load low, min_sample_time) | `performance` while aa live
io: nand -> `deadline`|`noop` ; read_ahead_kb ; no iostats
mem: zram size + swappiness ; lmk minfree for 1.4 GB ; dalvik heap props ok ?
rom diet: gms (`GmsCore`, `GoogleServicesFramework`, `Phonesky`, `Velvet`, `GoogleLoginService`, `GooglePartnerSetup`, `GoogleOneTimeInitializer`, `GoogleBackupTransport`, `GoogleFeedback`), `Multistore`, prestigio `OTA`, `DeviceTest`, `Mms`, `CalendarProvider`, `AndroidForWork`, `ManagedProvisioning` ? <- check each vs aa/bt needs
apply: magisk `service.d` script (root = rom default) | roundabout priv-app @ boot (su fallback)
measure before/after: boot time, free ram, aa frame drops / decode latency (oh logs), ui jank

## phase b = own kernel (only if source fits)
get running version -> pick tree -> rebuild config -> extract dtb (`/proc/device-tree` | boot img) -> build x86 -> wrap like stock -> recovery test
tune @ build: no debug (earlyprintk, ftrace, slub debug), `CONFIG_HZ`, preempt, lz4 zram, bfq/deadline, cpufreq governors, no unused drivers
risk: vendor drivers missing from public tree (touch, panel, sensors, camera, modem) => hardware dead ; module vermagic
