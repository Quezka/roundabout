#!/usr/bin/env bash
# full partition backup of the tablet -> local dir (+ nas copy)
# need: adb, tablet rooted (magisk) ; no write to tablet
# use: rom/backup.sh [--with-data] [--nas user@host:/path]
set -euo pipefail
ADB="${ADB:-$HOME/Android/Sdk/platform-tools/adb}"
SERIAL="${SERIAL:-PMP270262005418}"
WITH_DATA=0
NAS=""
while [ $# -gt 0 ]; do
  case "$1" in
    --with-data) WITH_DATA=1 ;;
    --nas) NAS="$2"; shift ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
  shift
done
a() { "$ADB" -s "$SERIAL" "$@"; }
sush() { a exec-out su -c "$1"; }

a wait-for-device
sush id | grep -q 'uid=0' || { echo "no root on tablet" >&2; exit 1; }

OUT="$(dirname "$0")/backup/$(date +%Y-%m-%d_%H%M)"
mkdir -p "$OUT/info"
echo "-> $OUT"

# device facts
sush getprop > "$OUT/info/getprop.txt"
sush 'cat /proc/partitions' > "$OUT/info/partitions.txt"
sush 'cat /proc/dumchar_info 2>/dev/null' > "$OUT/info/dumchar_info.txt" || true
sush 'cat /proc/cpuinfo' > "$OUT/info/cpuinfo.txt"
sush 'cat /proc/meminfo' > "$OUT/info/meminfo.txt"
sush 'cat /system/build.prop' > "$OUT/info/build.prop"
sush 'ls -l /dev/block/platform/*/by-name/ /dev/block/by-name/ 2>/dev/null' > "$OUT/info/by-name.txt" || true
sush 'pm list packages -f -s' > "$OUT/info/packages-system.txt"
sush 'pm list packages -f -3' > "$OUT/info/packages-user.txt"
sush 'pm list packages -d' > "$OUT/info/packages-disabled.txt"
sush 'ls -lR /system/app /system/priv-app' > "$OUT/info/system-apps.txt"
sush 'settings list global; echo ---; settings list secure; echo ---; settings list system' > "$OUT/info/settings.txt"
sush 'tar -cf - /data/adb 2>/dev/null' > "$OUT/data-adb.tar" || true

# partition list: name dev
# by-name links first ; old mtk (5.x) -> /proc/dumchar_info
LIST="$OUT/info/list.txt"
sush 'for d in /dev/block/platform/*/by-name /dev/block/by-name; do [ -d $d ] && for p in $d/*; do echo "${p##*/} $(readlink -f $p)"; done; done 2>/dev/null' | sort -u > "$LIST" || true
if [ ! -s "$LIST" ]; then
  awk 'NR>1 && $5 ~ /^\/dev\// {print $1, $5}' "$OUT/info/dumchar_info.txt" > "$LIST"
fi
[ -s "$LIST" ] || { echo "no partition list" >&2; exit 1; }
cat "$LIST"

: > "$OUT/SHA256SUMS"
while read -r name dev; do
  case "$name" in
    userdata|usrdata|data) [ $WITH_DATA = 1 ] || { echo "skip $name"; continue; } ;;
    cache|fat|intsd|otp|flashinfo) echo "skip $name"; continue ;;
  esac
  echo "dump $name <- $dev"
  sush "dd if=$dev bs=1048576 2>/dev/null" > "$OUT/$name.img"
  want=$(sush "cat /sys/class/block/${dev##*/}/size 2>/dev/null" | tr -d '\r')
  got=$(stat -c %s "$OUT/$name.img")
  if [ -n "$want" ] && [ "$((want * 512))" != "$got" ]; then
    echo "! size mismatch $name: want $((want * 512)) got $got" >&2; exit 1
  fi
  (cd "$OUT" && sha256sum "$name.img" >> SHA256SUMS)
done < "$LIST"

# second read of the irreplaceable ones -> same sums ?
for name in nvram nvdata protect1 protect2 proinfo; do
  [ -f "$OUT/$name.img" ] || continue
  dev=$(awk -v n="$name" '$1==n {print $2}' "$LIST")
  b=$(sush "dd if=$dev bs=1048576 2>/dev/null" | sha256sum | cut -d' ' -f1)
  a1=$(grep " $name.img\$" "$OUT/SHA256SUMS" | cut -d' ' -f1)
  [ "$a1" = "$b" ] || { echo "! $name differs on 2nd read" >&2; exit 1; }
done

echo "backup ok: $OUT"
if [ -n "$NAS" ]; then
  rsync -a --info=progress2 "$OUT" "$NAS/"
  ssh "${NAS%%:*}" "cd '${NAS#*:}/$(basename "$OUT")' && sha256sum -c --quiet SHA256SUMS" && echo "nas copy ok"
fi
