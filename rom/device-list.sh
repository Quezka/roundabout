# runs on the tablet as root (pushed by backup.sh) ; prints "name /dev/block/real" per partition
BB=/data/adb/magisk/busybox
for d in /dev/block/platform/*/by-name /dev/block/platform/*/*/by-name /dev/block/by-name; do
  [ -d "$d" ] || continue
  for p in "$d"/*; do echo "${p##*/} $($BB readlink -f "$p")"; done
done
