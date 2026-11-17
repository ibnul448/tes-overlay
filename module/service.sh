#!/system/bin/sh
MODDIR=${0%/*}
while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 3; done
echo 0 > /data/adb/ultrapower/boot_try 2>/dev/null   # boot selesai: reset penghitung pelindung bootloop
sleep 20
# Safe mode: buat file /data/adb/ultrapower/skip untuk melewati apply saat boot
[ -f /data/adb/ultrapower/skip ] && exit 0
sh $MODDIR/system/bin/ultrapower boot
