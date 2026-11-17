#!/system/bin/sh
# UltraPower - bagian boot-time Disable Thermal (berjalan SEBELUM modul di-mount).
# THERMAL=off di /data/adb/ultrapower/config  ->  properti thermal ditimpa (nilai asli dicatat). Service thermal dihentikan oleh `ultrapower` saat boot.
# THERMAL=on / safe mode (file "skip") -> tidak ada yang ditimpa.
MODDIR=${0%/*}
DIR=/data/adb/ultrapower

# --- Pelindung bootloop: hitung boot yang tidak pernah selesai. service.sh mereset hitungan begitu boot selesai.
# Bila 3 boot beruntun tidak selesai, modul menonaktifkan dirinya (hapus zygisk/*.so + buat file skip) SEBELUM zygote jalan,
# supaya HP bisa boot. Aktifkan lagi dengan pasang ulang modul (dan hapus /data/adb/ultrapower/skip).
mkdir -p $DIR 2>/dev/null
bn=$(cat $DIR/boot_try 2>/dev/null); bn=$(( ${bn:-0} + 1 )); echo $bn > $DIR/boot_try
if [ $bn -ge 3 ]; then
  rm -rf $MODDIR/zygisk
  : > $DIR/skip
  echo "$(date '+%F %T') bootloop terdeteksi: zygisk dihapus dan modul dinonaktifkan (file skip)" >> $DIR/bootguard.log
  echo 0 > $DIR/boot_try
fi
off=0
[ -f $DIR/config ] && grep -q '^THERMAL=off' $DIR/config && [ ! -f $DIR/skip ] && off=1

# --- Spoof perangkat GLOBAL (resetprop) sudah dihapus: spoof kini hanya per-app lewat Zygisk, tanpa reboot. Bersihkan sisa konfigurasinya.
rm -f $DIR/spoofg $DIR/spoofg.orig
# preset Infinix lama memakai model tanpa prefiks (X6891); model asli Transsion "Infinix X6891" -> perbarui entri yang sudah tersimpan
[ -f $DIR/spoof_apps ] && sed -i 's/^\([^|]*|gt50p|INFINIX|\)X6891|/\1Infinix X6891|/;s/^\([^|]*|gt30p|INFINIX|\)X6873|/\1Infinix X6873|/' $DIR/spoof_apps

# --- Spoof global sudah dihapus (spoof kini per-app lewat Zygisk). Bersihkan sisa pengaturan lamanya.
if [ -f $DIR/config ] && grep -q '^SPOOF' $DIR/config; then
  grep -v '^SPOOF' $DIR/config > $DIR/config.t && mv $DIR/config.t $DIR/config
fi
rm -f $DIR/spoof_orig

# --- THERMAL (boot-time): hanya properti. File overlay kosong untuk binary/config thermal SUDAH TIDAK DIPAKAI: overlay yang
# terpasang saat boot tidak bisa dilepas tanpa reboot, sehingga "thermal on" selalu butuh reboot. Sekarang service thermal
# dihentikan oleh `ultrapower` saat boot (THERMAL=off) dan bisa dinyalakan lagi kapan saja tanpa reboot.
EP=$DIR/early_props   # nilai ASLI properti sebelum ditimpa; dipakai `thermal on` untuk memulihkan
if [ $off = 1 ] && [ -f $MODDIR/thermal_props.list ]; then
  mkdir -p $DIR; : > $EP
  while IFS= read -r kv; do
    case "$kv" in ''|\#*) continue;; esac
    pn=${kv%%=*}
    echo "$pn|$(getprop $pn)" >> $EP
    resetprop -n "$pn" "${kv#*=}"
  done < $MODDIR/thermal_props.list
else
  rm -f $EP
fi

# Bersihkan file overlay kosong sisa versi lama (kalau ada) SEBELUM modul di-mount, supaya binary/config thermal asli utuh.
if [ -f $MODDIR/thermal_overlay.list ]; then
  while IFS= read -r f; do
    case "$f" in ''|/*|*..*) continue;; esac
    rm -f "$MODDIR/$f"
  done < $MODDIR/thermal_overlay.list
  [ -d $MODDIR/system/vendor ] && find $MODDIR/system/vendor -depth -type d -empty -exec rmdir {} \; 2>/dev/null
  [ -d $MODDIR/system/etc ] && find $MODDIR/system/etc -depth -type d -empty -exec rmdir {} \; 2>/dev/null
fi
exit 0
