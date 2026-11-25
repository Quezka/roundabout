#!/usr/bin/env bash
# write rom/out/system.img to the tablet ; ! only after backup.sh ok (laptop copy)
# boot partition untouched => current magisk-patched boot stays => root by default
# use: rom/flash.sh --backup rom/backup/<date> --i-have-a-backup
set -euo pipefail
ADB="${ADB:-$HOME/Android/Sdk/platform-tools/adb}"
SERIAL="${SERIAL:-PMP270262005418}"
BACKUP=""; SURE=0
while [ $# -gt 0 ]; do
  case "$1" in
    --backup) BACKUP="$2"; shift ;;
    --i-have-a-backup) SURE=1 ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
  shift
done
HERE="$(cd "$(dirname "$0")" && pwd)"
IMG="$HERE/out/system.img"
[ $SURE = 1 ] || { echo "refuse: pass --i-have-a-backup" >&2; exit 1; }
[ -f "$BACKUP/system.img" ] && [ -f "$BACKUP/SHA256SUMS" ] || { echo "no backup at $BACKUP" >&2; exit 1; }
(cd "$BACKUP" && sha256sum -c --quiet SHA256SUMS) || { echo "backup sums fail" >&2; exit 1; }
[ -s "$BACKUP/info/list.txt" ] || { echo "backup lacks partition list" >&2; exit 1; }
[ -f "$IMG" ] || { echo "no $IMG: run build.py" >&2; exit 1; }
[ "$(stat -c %s "$IMG")" = "$(stat -c %s "$BACKUP/system.img")" ] || { echo "size != stock system" >&2; exit 1; }

a() { "$ADB" -s "$SERIAL" "$@"; }
a wait-for-device
DEV=$(awk '$1=="system" || $1=="android" {print $2; exit}' "$BACKUP/info/list.txt")
# sanity: same block dev as the live /system mount
BB=/data/adb/magisk/busybox
LIVE=$(a exec-out su -c "$BB readlink -f \$($BB awk '\$2==\"/system\" {print \$1}' /proc/mounts)" | tr -d '\r')
[ "$LIVE" = "$DEV" ] || { echo "system dev mismatch: backup $DEV live $LIVE" >&2; exit 1; }
[ -n "$DEV" ] || { echo "no system dev in list.txt" >&2; exit 1; }
echo "target: $DEV"
# battery >= 30% <- power loss mid-dd = no system
LVL=$(a shell dumpsys battery | tr -d '\r' | grep -m1 ' level:' | tr -dc 0-9)
[ "${LVL:-0}" -ge 30 ] || { echo "battery ${LVL:-?}% < 30%: charge first" >&2; exit 1; }

# image via /data/local/tmp (no /sdcard on this tablet) ; system mounted ro => write under it, reboot right after
TMP=/data/local/tmp/rb-system.img
a push "$IMG" $TMP
LOCAL=$(sha256sum "$IMG" | cut -d' ' -f1)
# framework stop => no reads of /system mid-write ; adbd + su stay up
a exec-out su -c "stop"
a exec-out su -c "dd if=$TMP of=$DEV bs=1048576 && sync"
BACK=$(a exec-out su -c "dd if=$DEV bs=1048576 2>/dev/null" | sha256sum | cut -d' ' -f1)
if [ "$LOCAL" != "$BACK" ]; then
  echo "! read-back differs ; restoring stock system" >&2
  a push "$BACKUP/system.img" $TMP
  a exec-out su -c "dd if=$TMP of=$DEV bs=1048576 && sync"
  exit 1
fi
a shell su -c "rm $TMP"
echo "flash ok ; rebooting"
a reboot
