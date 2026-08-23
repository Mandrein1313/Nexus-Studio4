package com.dev.ministudio;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Local Build — ขั้นที่ 2: ดาวน์โหลดและเก็บเครื่องมือ
 * ยังไม่ compile จริง (ขั้นที่ 3)
 */
public class LocalBuildManager {

    public interface LogCallback {
        void onLog(String message);
    }

    /** แก้ให้ตรง repo ของคุณถ้าเปลี่ยนชื่อ */
    private static final String TOOLS_ZIP_URL =
            "https://github.com/Mandrein1313/Nexus-Studio/releases/download/local-tools-v1/local-build-tools.zip";

    private static final String TOOLS_DIR = "/sdcard/MiniStudio/tools";

    private final Context context;

    public LocalBuildManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public void startBuild(String projectRootPath, LogCallback log) {
        if (log == null) return;

        log.onLog("→ ตรวจสอบโปรเจกต์...");
        if (projectRootPath == null || projectRootPath.isEmpty()) {
            log.onLog("✗ ยังไม่ได้เปิดโปรเจกต์");
            return;
        }
        log.onLog("→ path: " + projectRootPath);

        new Thread(() -> {
            try {
                ensureToolsReady(log);
                log.onLog("✓ เครื่องมือพร้อมที่ " + TOOLS_DIR);
                log.onLog("⏭ ขั้นที่ 3: compile / dex / แพ็ก APK (ยังไม่ทำในรอบนี้)");
            } catch (Exception e) {
                log.onLog("✗ เตรียม tools ไม่สำเร็จ: " + e.getMessage());
            }
        }).start();
    }

    /** ดาวน์โหลด + แตก zip ถ้ายังไม่มีไฟล์ครบ */
    public void ensureToolsReady(LogCallback log) throws Exception {
        File dir = new File(TOOLS_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new Exception("สร้างโฟลเดอร์ tools ไม่ได้ (ตรวจสิทธิ์ไฟล์)");
        }

        File ecj = new File(dir, "ecj.jar");
        File d8 = new File(dir, "d8.jar");
        File androidJar = new File(dir, "android.jar");

        if (ecj.exists() && d8.exists() && androidJar.exists()) {
            log.onLog("→ พบ tools ครบแล้ว ข้ามดาวน์โหลด");
            return;
        }

        log.onLog("→ กำลังดาวน์โหลด local-build-tools.zip ...");
        log.onLog("  " + TOOLS_ZIP_URL);

        File zipFile = new File(dir, "local-build-tools.zip");
        downloadFile(TOOLS_ZIP_URL, zipFile, log);

        log.onLog("→ กำลังแตกไฟล์...");
        unzip(zipFile, dir);
        // ลบ zip เพื่อประหยัดที่
        // noinspection ResultOfMethodCallIgnored
        zipFile.delete();

        if (!ecj.exists() || !d8.exists() || !androidJar.exists()) {
            throw new Exception("หลังแตก zip ยังไม่ครบ ecj.jar / d8.jar / android.jar");
        }
        log.onLog("→ ดาวน์โหลดและเก็บ tools สำเร็จ");
    }

    private void downloadFile(String urlStr, File out, LogCallback log) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);
        conn.setInstanceFollowRedirects(true);
        conn.connect();

        int code = conn.getResponseCode();
        if (code != 200) {
            throw new Exception("HTTP " + code + " (รัน workflow prepare-local-tools และรอ Release ก่อน)");
        }

        long total = conn.getContentLength();
        try (InputStream in = new BufferedInputStream(conn.getInputStream());
             FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            long read = 0;
            int n;
            int lastPct = -1;
            while ((n = in.read(buf)) != -1) {
                fos.write(buf, 0, n);
                read += n;
                if (total > 0) {
                    int pct = (int) (read * 100 / total);
                    if (pct >= lastPct + 10) {
                        lastPct = pct;
                        log.onLog("  ดาวน์โหลด " + pct + "%");
                    }
                }
            }
        } finally {
            conn.disconnect();
        }
    }

    private void unzip(File zipFile, File targetDir) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(
                new java.io.FileInputStream(zipFile)))) {
            ZipEntry entry;
            byte[] buf = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                File out = new File(targetDir, entry.getName());
                if (entry.isDirectory()) {
                    // noinspection ResultOfMethodCallIgnored
                    out.mkdirs();
                } else {
                    File parent = out.getParentFile();
                    if (parent != null) // noinspection ResultOfMethodCallIgnored
                        parent.mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(out)) {
                        int n;
                        while ((n = zis.read(buf)) != -1) {
                            fos.write(buf, 0, n);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    public static File getToolsDir() {
        return new File(TOOLS_DIR);
    }
}