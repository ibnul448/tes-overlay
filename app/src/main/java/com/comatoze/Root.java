package com.comatoze;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/** Akses root sederhana lewat `su -c`. Semua panggilan blocking: jalankan dari thread latar. */
public final class Root {
    private Root() {}

    public static final String MOD = "/data/adb/modules/ultrapower";
    public static final String BIN = "sh " + MOD + "/system/bin/ultrapower";

    public static final class Res {
        public int code = -1;
        public byte[] raw = new byte[0];
        public String err = "";
        public String out() { return new String(raw, java.nio.charset.StandardCharsets.UTF_8); }
    }

    public static Res exec(String cmd) {
        final Res r = new Res();
        try {
            final Process p = new ProcessBuilder("su", "-c", cmd).start();
            p.getOutputStream().close();
            final StringBuilder eb = new StringBuilder();
            Thread t = new Thread(new Runnable() {
                @Override public void run() {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
                        String l;
                        while ((l = br.readLine()) != null) eb.append(l).append('\n');
                    } catch (IOException ignored) { }
                }
            });
            t.start();
            r.raw = readAll(p.getInputStream());
            r.code = p.waitFor();
            t.join(500);
            r.err = eb.toString();
        } catch (Exception e) {
            r.code = -1;
            r.err = String.valueOf(e);
        }
        return r;
    }

    public static String sh(String cmd) { return exec(cmd).out(); }

    public static boolean available() {
        Res r = exec("id -u");
        return r.code == 0 && r.out().trim().equals("0");
    }

    /** Proses panjang (stream stdout baris demi baris). Pemanggil wajib destroy(). */
    public static Process stream(String cmd) throws IOException {
        Process p = new ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start();
        p.getOutputStream().close();
        return p;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return bo.toByteArray();
    }
}
