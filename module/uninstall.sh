#!/system/bin/sh
D=/data/adb/ultrapower
# 1) hentikan pemantau latar DULU (urutan lama menghapus $D sebelum membaca file pid, jadi pemantau tidak pernah mati)
for f in $D/*.pid; do [ -f "$f" ] && kill $(cat "$f") 2>/dev/null; done
# 2) pulihkan perubahan yang hidup di luar folder modul
[ -f $D/batsaver_on ] && settings put global low_power 0 >/dev/null 2>&1
[ -s $D/angle_apps ] && sh ${0%/*}/system/bin/ultrapower angle_app clear >/dev/null 2>&1
grep -q '^COL_' $D/config 2>/dev/null && { service call SurfaceFlinger 1015 i32 0 >/dev/null 2>&1; service call SurfaceFlinger 1022 f 1.0 >/dev/null 2>&1; }
grep -q '^F_hzforce=off' $D/config 2>/dev/null || service call SurfaceFlinger 1035 i32 -1 >/dev/null 2>&1   # lepas kunci Hz SurfaceFlinger (aktif bawaan, kecuali pengguna mematikan)
grep -q '^F_aggrdoze=on' $D/config 2>/dev/null && dumpsys deviceidle unforce >/dev/null 2>&1
if grep -q '^F_gmslimit=on' $D/config 2>/dev/null; then for op in RUN_IN_BACKGROUND WAKE_LOCK; do cmd appops set com.google.android.gms $op default >/dev/null 2>&1; done; fi
if grep -q '^RENDERER=' $D/config 2>/dev/null; then for k in debug.hwui.renderer debug.skia.threaded_mode debug.hwui.render_thread; do resetprop --delete $k >/dev/null 2>&1; done; fi
rm -rf $D
pm uninstall com.ultrapower.fps >/dev/null 2>&1
