SKIPUNZIP=0

# 1 = buka channel Telegram di akhir instalasi, 0 = jangan dibuka
BUKA_TELEGRAM=1
TELEGRAM_URL="https://t.me/ComaTozePROJECT"

# ---- tampilan: jeda kecil + bar loading (tiap tahap = langkah instalasi sungguhan) ----
_nap(){ sleep 0.25 2>/dev/null; }
_bar(){ # persen teks
  n=$(( $1 / 10 )); b=""; i=0
  while [ $i -lt 10 ]; do
    if [ $i -lt $n ]; then b="${b}#"; else b="${b}-"; fi
    i=$((i+1))
  done
  ui_print "  [$b] $(printf '%3d' "$1")%"
  ui_print "      $2"
  _nap
}

ui_print " "
ui_print "  =========================="
ui_print "   ####  ###  #   #  ### "
ui_print "  #     #   # ## ## #   #"
ui_print "  #     #   # # # # #####"
ui_print "  #     #   # #   # #   #"
ui_print "   ####  ###  #   # #   #"
ui_print " "
ui_print "  #####  ###  ##### #####"
ui_print "    #   #   #    #  #    "
ui_print "    #   #   #   #   #### "
ui_print "    #   #   #  #    #    "
ui_print "    #    ###  ##### #####"
ui_print "  =========================="
ui_print "   all in one  -  by ibnul"
ui_print "   Versi $(grep '^version=' $MODPATH/module.prop 2>/dev/null | cut -d= -f2-)"
ui_print "  =========================="
ui_print " "
_nap

_bar 10 "Memasang Comatoze"
set_perm_recursive $MODPATH 0 0 0755 0644
set_perm $MODPATH/system/bin/ultrapower 0 0 0755
set_perm $MODPATH/service.sh 0 0 0755
set_perm $MODPATH/post-fs-data.sh 0 0 0755
_bar 30 "Mengatur izin file"

# Disable Thermal (brutal): daftar file thermal bawaan perangkat yang akan ditimpa file kosong saat THERMAL=off (lihat post-fs-data.sh)
_bar 45 "Memindai file thermal"
find /system/vendor/ -name '*thermal*.conf' -type f 2>/dev/null | while read -r nama; do
  echo "${nama#/}" >> $MODPATH/thermal_overlay.list
done
sort -u $MODPATH/thermal_overlay.list > $MODPATH/thermal_overlay.tmp && mv $MODPATH/thermal_overlay.tmp $MODPATH/thermal_overlay.list
njml=0
[ -f $MODPATH/thermal_overlay.list ] && njml=$(( $(wc -l < $MODPATH/thermal_overlay.list) ))
_bar 65 "Thermal: $njml file ditemukan"

# Pasang ulang = mulai bersih: buang mode aman (skip) & penghitung bootloop yang bisa membuat tweak tidak pernah diterapkan saat boot
rm -f /data/adb/ultrapower/skip; echo 0 > /data/adb/ultrapower/boot_try 2>/dev/null
# --- COMATOZE
if [ -f $MODPATH/apk/comatoze.apk ]; then
  _bar 80 "Memasang aplikasi Comatoze"
  cp $MODPATH/apk/comatoze.apk /data/local/tmp/comatoze.apk && chmod 644 /data/local/tmp/comatoze.apk
  # tanda tangan APK baru (build GitHub) bisa beda dengan versi lama -> copot dulu bila pasang ulang gagal
  pm install -r /data/local/tmp/comatoze.apk >/dev/null 2>&1 || { pm uninstall com.comatoze >/dev/null 2>&1; pm install -r /data/local/tmp/comatoze.apk >/dev/null 2>&1; }
  if pm path com.comatoze >/dev/null 2>&1; then
    pm grant com.comatoze android.permission.POST_NOTIFICATIONS >/dev/null 2>&1
    ui_print "      [OK] Aplikasi terpasang"
  else
    ui_print "      [!] Gagal memasang aplikasi"
    ui_print "          (dicoba lagi saat boot)"
  fi
  rm -f /data/local/tmp/comatoze.apk
else
  ui_print "      [-] APK tidak disertakan"
  ui_print "          (install manual)"
fi

_bar 100 "Selesai"
ui_print " "
ui_print "  Buka WebUI modul atau jalankan"
ui_print "  'su -c ultrapower status'"
ui_print " "
ui_print "  Telegram: t.me/ComaTozePROJECT"
ui_print " "

# buka channel Telegram (best-effort; diabaikan bila gagal). Ditaruh paling akhir supaya log instalasi sudah lengkap.
if [ "$BUKA_TELEGRAM" = 1 ]; then
  ( am start --user 0 -a android.intent.action.VIEW -d "$TELEGRAM_URL" >/dev/null 2>&1 ) &
fi
