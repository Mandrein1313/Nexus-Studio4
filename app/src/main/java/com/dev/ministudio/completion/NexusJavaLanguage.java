package com.dev.ministudio.completion;

import android.os.Bundle;

import androidx.annotation.NonNull;

import java.util.List;

import io.github.rosemoe.sora.lang.completion.CompletionItemKind;
import io.github.rosemoe.sora.lang.completion.CompletionPublisher;
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem;
import io.github.rosemoe.sora.langs.java.JavaLanguage;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.ContentReference;

/**
 * JavaLanguage + แนะนำคลาสจาก android.jar (AndroidApiIndexer)
 */
public class NexusJavaLanguage extends JavaLanguage {

    private static final int MAX_API_ITEMS = 40;
    private static final int MIN_PREFIX = 2;

    @Override
    public void requireAutoComplete(
            @NonNull ContentReference content,
            @NonNull CharPosition position,
            @NonNull CompletionPublisher publisher,
            @NonNull Bundle extraArguments
    ) {
        // ของเดิม: keyword + identifier ในไฟล์
        super.requireAutoComplete(content, position, publisher, extraArguments);

        // เติม Android API
        try {
            String prefix = extractPrefix(content, position);
            if (prefix.length() < MIN_PREFIX) {
                return;
            }

            List<AndroidApiIndexer.ApiClass> hits =
                    AndroidApiIndexer.search(prefix, MAX_API_ITEMS);
            if (hits == null || hits.isEmpty()) {
                return;
            }

            int prefixLen = prefix.length();
            for (AndroidApiIndexer.ApiClass api : hits) {
                SimpleCompletionItem item = new SimpleCompletionItem(
                        api.simpleName,
                        api.fullName,
                        prefixLen,
                        api.simpleName
                );
                try {
                    item.kind(CompletionItemKind.Class);
                } catch (Throwable ignored) {
                    // บางรุ่นไม่มี kind() — ข้ามได้
                }
                publisher.addItem(item);
            }
        } catch (Throwable t) {
            // อย่าให้ completion พังทั้งก้อน
        }
    }

    /**
     * ดึงคำที่กำลังพิมพ์ก่อนเคอร์เซอร์ (ตัวอักษร/ตัวเลข/_)
     */
    private static String extractPrefix(ContentReference content, CharPosition position) {
        try {
            int line = position.line;
            int col = position.column;
            CharSequence lineSeq = content.getLine(line);
            if (lineSeq == null || col <= 0) {
                return "";
            }
            int i = Math.min(col, lineSeq.length()) - 1;
            StringBuilder sb = new StringBuilder();
            while (i >= 0) {
                char c = lineSeq.charAt(i);
                if (Character.isLetterOrDigit(c) || c == '_' || c == '.') {
                    sb.append(c);
                    i--;
                } else {
                    break;
                }
            }
            String raw = sb.reverse().toString();
            // ถ้ามีจุด เช่น android.widget.Te → ใช้ส่วนหลังจุดสุดท้าย
            int dot = raw.lastIndexOf('.');
            if (dot >= 0 && dot < raw.length() - 1) {
                return raw.substring(dot + 1);
            }
            if (dot >= 0 && dot == raw.length() - 1) {
                return ""; // พิมพ์จบด้วยจุด รอตัวถัดไป
            }
            return raw;
        } catch (Exception e) {
            return "";
        }
    }
}