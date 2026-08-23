package com.dev.ministudio;

import android.content.Context;

/**
 * โครง Local Build — ขั้นที่ 1 ยังไม่ compile จริง
 * ขั้นถัดไป: ดาวน์โหลด tools แล้ว compile บนเครื่อง
 */
public class LocalBuildManager {

    public interface LogCallback {
        void onLog(String message);
    }

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
        log.onLog("→ โหมด Local ยังไม่เชื่อม toolchain");
        log.onLog("✓ โครง LocalBuildManager พร้อม (ขั้นที่ 1 เสร็จ)");
        log.onLog("⏭ ขั้นที่ 2: ดาวน์โหลด/เก็บเครื่องมือ build");
    }
}
