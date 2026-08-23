package com.dev.ministudio;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Local Build
 * ขั้น 1: โครง
 * ขั้น 2: ดาวน์โหลด tools
 * ขั้น 3: compile .java ด้วย ECJ (+ พยายาม D8 ถ้ามี)
 * ขั้น 4 (ยังไม่ทำ): aapt2 + เซ็น APK
 */
public class LocalBuildManager {

    public interface LogCallback {
        void onLog(String message);
    }

    private static final String TOOLS_ZIP_URL =
            "https://github.com/Mandrein1313/Nexus-Studio/releases/download/local-tools-v1/local-build-tools.zip";

    private static final String TOOLS_DIR = "/sdcard/MiniStudio/tools";

    private final Context context;

    public LocalBuildManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public void startBuild(String projectRootPath, LogCallback log) {
        if (log == null) return;

        new Thread(() -> {
            try {
                log.onLog("📱 Local Build — ขั้นที่ 3 (compile)");
                log.onLog("→ ตรวจสอบโปรเจกต์...");
                if (projectRootPath == null || projectRootPath.isEmpty()) {
                    log.onLog("✗ ยังไม่ได้เปิดโปรเจกต์");
                    return;
                }
                File projectRoot = new File(projectRootPath);
                if (!projectRoot.exists()) {
                    log.onLog("✗ ไม่พบโฟลเดอร์: " + projectRootPath);
                    return;
                }
                log.onLog("→ path: " + projectRootPath);

                ensureToolsReady(log);

                File androidJar = new File(TOOLS_DIR, "android.jar");
                if (!androidJar.exists()) {
                    log.onLog("✗ ไม่พบ android.jar ใน " + TOOLS_DIR);
                    return;
                }

                // โฟลเดอร์งาน
                File workDir = new File(projectRoot, ".local-build");
                File classesDir = new File(workDir, "classes");
                File dexDir = new File(workDir, "dex");
                deleteRecursive(classesDir);
                deleteRecursive(dexDir);
                // noinspection ResultOfMethodCallIgnored
                classesDir.mkdirs();
                // noinspection ResultOfMethodCallIgnored
                dexDir.mkdirs();

                List<File> javaFiles = new ArrayList<>();
                collectJavaFiles(new File(projectRoot, "app/src/main/java"), javaFiles);
                if (javaFiles.isEmpty()) {
                    collectJavaFiles(new File(projectRoot, "src/main/java"), javaFiles);
                }
                if (javaFiles.isEmpty()) {
                    log.onLog("✗ ไม่พบไฟล์ .java ใน app/src/main/java");
                    return;
                }
                log.onLog("→ พบซอร์ส " + javaFiles.size() + " ไฟล์");

                boolean ok = compileWithEcj(javaFiles, androidJar, classesDir, log);
                if (!ok) {
                    log.onLog("✗ Compile ไม่ผ่าน");
                    return;
                }
                log.onLog("✓ Compile ผ่าน (ได้ .class ใน .local-build/classes)");

                boolean dexOk = runD8(classesDir, androidJar, dexDir, log);
                if (dexOk) {
                    log.onLog("✓ D8 เสร็จ (ดู .local-build/dex)");
                } else {
                    log.onLog("⚠️ ข้าม D8 หรือยังไม่พร้อม — compile อย่างเดียวสำเร็จ");
                }

                log.onLog("⏭ ขั้นที่ 4: แพ็ก resources + เซ็น APK (ต้องใช้ aapt2 ฯลฯ)");
                log.onLog("✓ จบขั้นที่ 3");
            } catch (Exception e) {
                log.onLog("✗ Local Build error: " + e.getMessage());
            }
        }).start();
    }

    // -------------------------------------------------------------------------
    // Compile ด้วย ECJ (ในโปรเซส Nexus — ต้องมี dependency org.eclipse.jdt:ecj)
    // -------------------------------------------------------------------------
    private boolean compileWithEcj(List<File> javaFiles, File androidJar,
                                   File outClassesDir, LogCallback log) {
        try {
            List<String> args = new ArrayList<>();
            args.add("-1.8");
            args.add("-proc:none");
            args.add("-classpath");
            args.add(androidJar.getAbsolutePath());
            args.add("-d");
            args.add(outClassesDir.getAbsolutePath());
            for (File f : javaFiles) {
                args.add(f.getAbsolutePath());
            }

            log.onLog("→ ECJ compile...");

            StringWriter outSw = new StringWriter();
            StringWriter errSw = new StringWriter();
            PrintWriter outPw = new PrintWriter(outSw, true);
            PrintWriter errPw = new PrintWriter(errSw, true);

            // org.eclipse.jdt.internal.compiler.batch.Main.compile(...)
            Class<?> mainClz = Class.forName("org.eclipse.jdt.internal.compiler.batch.Main");
            Method compile = mainClz.getMethod(
                    "compile",
                    String[].class,
                    PrintWriter.class,
                    PrintWriter.class,
                    Object.class
            );
            Object result = compile.invoke(
                    null,
                    (Object) args.toArray(new String[0]),
                    outPw,
                    errPw,
                    null
            );

            String outLog = outSw.toString().trim();
            String errLog = errSw.toString().trim();
            if (!outLog.isEmpty()) {
                for (String line : outLog.split("\n")) {
                    if (!line.trim().isEmpty()) log.onLog(line);
                }
            }
            if (!errLog.isEmpty()) {
                for (String line : errLog.split("\n")) {
                    if (!line.trim().isEmpty()) log.onLog(line);
                }
            }

            boolean success;
            if (result instanceof Boolean) {
                success = (Boolean) result;
            } else {
                // บางเวอร์ชันคืน void — ดูว่ามี .class หรือไม่
                success = hasClassFiles(outClassesDir);
            }

            if (!success && !hasClassFiles(outClassesDir)) {
                log.onLog("✗ ECJ รายงานว่า compile ไม่สำเร็จ");
                return false;
            }
            if (!hasClassFiles(outClassesDir)) {
                log.onLog("✗ ไม่พบไฟล์ .class หลัง compile");
                return false;
            }
            return true;
        } catch (ClassNotFoundException e) {
            log.onLog("✗ ไม่พบ ECJ ในแอป — เพิ่ม implementation 'org.eclipse.jdt:ecj:3.37.0' แล้ว build Nexus ใหม่");
            return false;
        } catch (Exception e) {
            log.onLog("✗ ECJ error: " + e.getMessage());
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // D8 ผ่าน reflection จาก d8.jar (อาจใช้ไม่ได้บน ART ถ้า jar ยังไม่ใช่ dex)
    // -------------------------------------------------------------------------
    private boolean runD8(File classesDir, File androidJar, File dexOutDir, LogCallback log) {
        try {
            File d8Jar = new File(TOOLS_DIR, "d8.jar");
            if (!d8Jar.exists()) {
                log.onLog("→ ไม่มี d8.jar ข้ามขั้น dex");
                return false;
            }

            // บน Android โหลด .class จาก jar มาตรฐานมักไม่ได้
            // ลองเรียก D8 ถ้ามีอยู่ใน classpath ของแอป
            try {
                Class<?> d8Clz = Class.forName("com.android.tools.r8.D8");
                Method main = d8Clz.getMethod("main", String[].class);

                List<String> args = new ArrayList<>();
                args.add("--lib");
                args.add(androidJar.getAbsolutePath());
                args.add("--output");
                args.add(dexOutDir.getAbsolutePath());
                List<File> classFiles = new ArrayList<>();
                collectClassFiles(classesDir, classFiles);
                for (File c : classFiles) {
                    args.add(c.getAbsolutePath());
                }
                if (classFiles.isEmpty()) {
                    log.onLog("→ ไม่มี .class ให้ D8");
                    return false;
                }

                log.onLog("→ D8 (" + classFiles.size() + " classes)...");
                main.invoke(null, (Object) args.toArray(new String[0]));
                File[] out = dexOutDir.listFiles();
                return out != null && out.length > 0;
            } catch (ClassNotFoundException e) {
                log.onLog("→ D8 ยังไม่อยู่ใน classpath แอป (ข้ามได้ — ขั้น 3 เน้น compile)");
                return false;
            }
        } catch (Exception e) {
            log.onLog("⚠️ D8: " + e.getMessage());
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Tools download (ขั้น 2)
    // -------------------------------------------------------------------------
    public void ensureToolsReady(LogCallback log) throws Exception {
        File dir = new File(TOOLS_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new Exception("สร้างโฟลเดอร์ tools ไม่ได้");
        }

        File androidJar = new File(dir, "android.jar");
        File ecj = new File(dir, "ecj.jar");
        File d8 = new File(dir, "d8.jar");

        if (androidJar.exists()) {
            log.onLog("→ พบ tools (android.jar" +
                    (ecj.exists() ? ", ecj.jar" : "") +
                    (d8.exists() ? ", d8.jar" : "") + ")");
            return;
        }

        log.onLog("→ ดาวน์โหลด local-build-tools.zip ...");
        File zipFile = new File(dir, "local-build-tools.zip");
        downloadFile(TOOLS_ZIP_URL, zipFile, log);
        log.onLog("→ แตก zip...");
        unzip(zipFile, dir);
        // noinspection ResultOfMethodCallIgnored
        zipFile.delete();

        if (!androidJar.exists()) {
            throw new Exception("หลังแตก zip ยังไม่มี android.jar");
        }
        log.onLog("→ เก็บ tools ที่ " + TOOLS_DIR);
    }

    private void downloadFile(String urlStr, File out, LogCallback log) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "Nexus-Studio");
        conn.connect();
        if (conn.getResponseCode() != 200) {
            throw new Exception("HTTP " + conn.getResponseCode() + " โหลด tools ไม่ได้");
        }
        long total = conn.getContentLength();
        try (InputStream in = new BufferedInputStream(conn.getInputStream());
             FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            long read = 0;
            int n, lastPct = -1;
            while ((n = in.read(buf)) != -1) {
                fos.write(buf, 0, n);
                read += n;
                if (total > 0) {
                    int pct = (int) (read * 100 / total);
                    if (pct >= lastPct + 20) {
                        lastPct = pct;
                        log.onLog("  ดาวน์โหลด tools " + pct + "%");
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
                        while ((n = zis.read(buf)) != -1) fos.write(buf, 0, n);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private void collectJavaFiles(File dir, List<File> out) {
        if (dir == null || !dir.exists()) return;
        File[] list = dir.listFiles();
        if (list == null) return;
        for (File f : list) {
            if (f.isDirectory()) collectJavaFiles(f, out);
            else if (f.getName().endsWith(".java")) out.add(f);
        }
    }

    private void collectClassFiles(File dir, List<File> out) {
        if (dir == null || !dir.exists()) return;
        File[] list = dir.listFiles();
        if (list == null) return;
        for (File f : list) {
            if (f.isDirectory()) collectClassFiles(f, out);
            else if (f.getName().endsWith(".class")) out.add(f);
        }
    }

    private boolean hasClassFiles(File dir) {
        List<File> list = new ArrayList<>();
        collectClassFiles(dir, list);
        return !list.isEmpty();
    }

    private void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] ch = f.listFiles();
            if (ch != null) for (File c : ch) deleteRecursive(c);
        }
        // noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}