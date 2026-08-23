package com.dev.ministudio.editor;

import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;

/**
 * ธีม Tokyo Night โทนม่วง สำหรับ Nexus Studio
 * ใช้เฉพาะ color ID ที่มีใน Sora 0.24.x
 */
public class NexusColorScheme extends EditorColorScheme {

    @Override
    public void applyDefault() {
        super.applyDefault();

        // ===== พื้นหลัง =====
        setColor(WHOLE_BACKGROUND, 0xFF1A1B26);
        setColor(TEXT_NORMAL, 0xFFA9B1D6);
        setColor(CURRENT_LINE, 0xFF24283B);
        setColor(LINE_NUMBER, 0xFF3B4261);
        setColor(LINE_NUMBER_BACKGROUND, 0xFF1A1B26);
        setColor(LINE_DIVIDER, 0xFF292E42);

        // ===== การเลือกข้อความ =====
        setColor(SELECTED_TEXT_BACKGROUND, 0xFF364A82);
        setColor(SELECTION_INSERT, 0xFFC0CAF5);
        setColor(SELECTION_HANDLE, 0xFF7AA2F7);

        // ===== ไวยากรณ์ =====
        setColor(KEYWORD, 0xFFBB9AF7);          // ม่วง — public, class, if
        setColor(IDENTIFIER_NAME, 0xFF7DCFFF);  // ฟ้า — ชื่อเมธอด
        setColor(IDENTIFIER_VAR, 0xFFC0CAF5);   // ขาวฟ้า — ตัวแปร
        setColor(LITERAL, 0xFF9ECE6A);          // เขียว — string + ตัวเลข
        setColor(OPERATOR, 0xFF89DDFF);         // ฟ้าใส — { } ( ) ;
        setColor(COMMENT, 0xFF565F89);          // เทาม่วง — // comment
        setColor(ANNOTATION, 0xFFE0AF68);       // ทอง — @Override

        // ===== วงเล็บ / block =====
        setColor(BLOCK_LINE, 0xFF3B4261);
        setColor(BLOCK_LINE_CURRENT, 0xFF7AA2F7);
        setColor(MATCHED_TEXT_BACKGROUND, 0xFF3D59A1);
        setColor(UNDERLINE, 0xFF7AA2F7);

        // ===== scrollbar =====
        setColor(SCROLL_BAR_THUMB, 0xFF3B4261);
        setColor(SCROLL_BAR_THUMB_PRESSED, 0xFF7AA2F7);
        setColor(SCROLL_BAR_TRACK, 0xFF1A1B26);
    }
}
