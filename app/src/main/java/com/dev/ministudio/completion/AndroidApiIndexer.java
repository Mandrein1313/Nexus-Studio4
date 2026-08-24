package com.dev.ministudio.completion;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * อ่าน android.jar แล้วสร้างรายชื่อคลาส Android API
 * เก็บ cache เพื่อไม่ต้องสแกนทุกครั้ง
 */
public class AndroidApiIndexer {

    private static final String TAG = "AndroidApiIndexer";

    /** path มาตรฐานจากชุด tools ที่โหลดไว้ */
    public static final String DEFAULT_JAR =
            "/sdcard/MiniStudio/tools/android.jar";

    public static final String DEFAULT_CACHE =
            "/sdcard/MiniStudio/tools/android-api-index.txt";

    public static class ApiClass {
        public final String simpleName;  // TextView
        public final String fullName;    // android.widget.TextView
        public final String packageName; // android.widget

        public ApiClass(String fullName) {
            this.fullName = fullName;
            int last = fullName.lastIndexOf('.');
            if (last > 0) {
                this.packageName = fullName.substring(0, last);
                this.simpleName = fullName.substring(last + 1);
            } else {
                this.packageName = "";
                this.simpleName = fullName;
            }
        }
    }

    public interface Callback {
        void onReady(List<ApiClass> classes, boolean fromCache);

        void onError(String message);
    }

    private static volatile List<ApiClass> cachedList = null;
    private static volatile boolean loading = false;

    private final Context context;

    public AndroidApiIndexer(Context context) {
        this.context = context.getApplicationContext();
    }

    /** ได้ list ทันทีถ้าโหลดแล้ว ไม่งั้น null */
    public static List<ApiClass> getCachedIfReady() {
        return cachedList;
    }

    /**
     * โหลดจาก cache หรือสแกน jar บน background thread
     */
    public void loadAsync(Callback callback) {
        if (cachedList != null && !cachedList.isEmpty()) {
            if (callback != null) {
                callback.onReady(cachedList, true);
            }
            return;
        }

        new Thread(() -> {
            try {
                List<ApiClass> list = loadSync();
                cachedList = list;
                if (callback != null) {
                    callback.onReady(list, false);
                }
            } catch (Exception e) {
                Log.e(TAG, "load failed", e);
                if (callback != null) {
                    callback.onError(e.getMessage() != null ? e.getMessage() : "index failed");
                }
            }
        }).start();
    }

    /**
     * โหลดแบบ sync (เรียกจาก background เท่านั้น)
     */
    public List<ApiClass> loadSync() throws Exception {
        if (cachedList != null && !cachedList.isEmpty()) {
            return cachedList;
        }
        synchronized (AndroidApiIndexer.class) {
            if (cachedList != null && !cachedList.isEmpty()) {
                return cachedList;
            }
            if (loading) {
                // รอแบบง่าย
                while (loading) {
                    try {
                        AndroidApiIndexer.class.wait(100);
                    } catch (InterruptedException ignored) {
                        break;
                    }
                }
                if (cachedList != null) return cachedList;
            }
            loading = true;
            try {
                File cacheFile = new File(DEFAULT_CACHE);
                File jarFile = new File(DEFAULT_JAR);

                List<ApiClass> list = null;
                if (cacheFile.exists() && cacheFile.length() > 100) {
                    list = readCache(cacheFile);
                }
                if (list == null || list.isEmpty()) {
                    if (!jarFile.exists()) {
                        throw new Exception("ไม่พบ android.jar ที่ " + DEFAULT_JAR);
                    }
                    list = scanJar(jarFile);
                    writeCache(cacheFile, list);
                }
                cachedList = Collections.unmodifiableList(list);
                return cachedList;
            } finally {
                loading = false;
                AndroidApiIndexer.class.notifyAll();
            }
        }
    }

    private List<ApiClass> scanJar(File jarFile) throws Exception {
        List<ApiClass> out = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(jarFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                String name = entry.getName();
                if (!name.endsWith(".class")) {
                    zis.closeEntry();
                    continue;
                }
                // ข้าม inner class และ package-info
                if (name.contains("$") || name.endsWith("package-info.class")) {
                    zis.closeEntry();
                    continue;
                }
                // เอาเฉพาะ android.* และ androidx.* (ถ้ามีใน jar)
                if (!name.startsWith("android/")
                        && !name.startsWith("androidx/")
                        && !name.startsWith("com/android/")) {
                    zis.closeEntry();
                    continue;
                }
                String full = name.substring(0, name.length() - 6).replace('/', '.');
                // ข้ามชื่อแปลก
                if (full.isEmpty() || full.contains("-")) {
                    zis.closeEntry();
                    continue;
                }
                out.add(new ApiClass(full));
                zis.closeEntry();
            }
        }
        // เรียงตาม simpleName
        Collections.sort(out, (a, b) -> a.simpleName.compareToIgnoreCase(b.simpleName));
        return out;
    }

    private List<ApiClass> readCache(File cacheFile) throws Exception {
        List<ApiClass> out = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(cacheFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                out.add(new ApiClass(line));
            }
        }
        return out;
    }

    private void writeCache(File cacheFile, List<ApiClass> list) {
        try {
            File parent = cacheFile.getParentFile();
            if (parent != null && !parent.exists()) {
                // noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }
            try (BufferedWriter bw = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(cacheFile), StandardCharsets.UTF_8))) {
                bw.write("# Android API class index\n");
                bw.write("# count=" + list.size() + "\n");
                for (ApiClass c : list) {
                    bw.write(c.fullName);
                    bw.write('\n');
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "write cache failed", e);
        }
    }

    /**
     * ค้นหาตาม prefix (simpleName หรือ fullName)
     * @param limit สูงสุดที่คืน
     */
    public static List<ApiClass> search(String prefix, int limit) {
        List<ApiClass> src = cachedList;
        if (src == null || src.isEmpty() || prefix == null) {
            return Collections.emptyList();
        }
        String q = prefix.trim();
        if (q.length() < 1) {
            return Collections.emptyList();
        }
        String lower = q.toLowerCase();
        List<ApiClass> result = new ArrayList<>();

        // 1) simpleName ขึ้นต้นด้วย q
        for (ApiClass c : src) {
            if (c.simpleName.toLowerCase().startsWith(lower)) {
                result.add(c);
                if (result.size() >= limit) return result;
            }
        }
        // 2) simpleName บรรจุ q
        if (result.size() < limit) {
            for (ApiClass c : src) {
                if (c.simpleName.toLowerCase().contains(lower)
                        && !c.simpleName.toLowerCase().startsWith(lower)) {
                    result.add(c);
                    if (result.size() >= limit) return result;
                }
            }
        }
        // 3) fullName
        if (result.size() < limit && lower.contains(".")) {
            for (ApiClass c : src) {
                if (c.fullName.toLowerCase().startsWith(lower) && !result.contains(c)) {
                    result.add(c);
                    if (result.size() >= limit) return result;
                }
            }
        }
        return result;
    }
}