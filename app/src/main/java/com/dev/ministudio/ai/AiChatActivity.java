package com.dev.ministudio.ai;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Base64;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.dev.ministudio.R;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class AiChatActivity extends AppCompatActivity {

    private WebView webAiChat;
    private EditText etAiInput;
    private String chatHistory = "";
    private GeminiAssistant geminiAssistant;
    private boolean isWaitingReply = false;

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean isSpeaking = false;
    private String lastAiReply = "";
    private ImageButton btnSpeak;

    public static final String EXTRA_PROJECT_NAME = "projectName";
    public static final String EXTRA_FILE_PATH = "filePath";
    public static final String EXTRA_CODE_SNIPPET = "codeSnippet";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        getWindow().setStatusBarColor(Color.parseColor("#1F2335"));
        getWindow().setNavigationBarColor(Color.parseColor("#1A1B26"));

        setContentView(R.layout.activity_ai_chat);

        geminiAssistant = new GeminiAssistant(this);
        initTts();

        webAiChat = findViewById(R.id.webAiChat);
        etAiInput = findViewById(R.id.etAiInput);
        ImageButton btnBack = findViewById(R.id.btnAiBack);
        ImageButton btnClear = findViewById(R.id.btnAiClear);
        ImageButton btnSend = findViewById(R.id.btnAiSend);
        btnSpeak = findViewById(R.id.btnAiSpeak);

        setupWebView();

        btnBack.setOnClickListener(v -> {
            stopSpeaking();
            finish();
        });

        btnClear.setOnClickListener(v -> {
            stopSpeaking();
            chatHistory = "";
            lastAiReply = "";
            loadEmptyChat();
            Toast.makeText(this, "ล้างประวัติแล้ว", Toast.LENGTH_SHORT).show();
        });

        btnSend.setOnClickListener(v -> sendMessage());

        etAiInput.setOnEditorActionListener((tv, actionId, event) -> {
            sendMessage();
            return true;
        });

        if (btnSpeak != null) {
            btnSpeak.setOnClickListener(v -> {
                if (isSpeaking) {
                    stopSpeaking();
                    Toast.makeText(this, "หยุดเสียงแล้ว", Toast.LENGTH_SHORT).show();
                } else if (lastAiReply != null && !lastAiReply.isEmpty()) {
                    speakText(lastAiReply);
                } else {
                    Toast.makeText(this, "ยังไม่มีข้อความ AI ให้พูด", Toast.LENGTH_SHORT).show();
                }
            });
        }

        String snippet = getIntent().getStringExtra(EXTRA_CODE_SNIPPET);
        if (snippet != null && !snippet.isEmpty()) {
            etAiInput.setText("ช่วยอธิบายหรือปรับปรุงโค้ดนี้:\n" + snippet);
        }

        loadEmptyChat();
    }

    private void initTts() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int r = tts.setLanguage(new Locale("th", "TH"));
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale.US);
                }
                ttsReady = true;
            } else {
                ttsReady = false;
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    isSpeaking = true;
                    runOnUiThread(() -> {
                        if (btnSpeak != null) btnSpeak.setAlpha(1f);
                    });
                }

                @Override
                public void onDone(String utteranceId) {
                    isSpeaking = false;
                    runOnUiThread(() -> {
                        if (btnSpeak != null) btnSpeak.setAlpha(0.85f);
                    });
                }

                @Override
                public void onError(String utteranceId) {
                    isSpeaking = false;
                    runOnUiThread(() -> {
                        if (btnSpeak != null) btnSpeak.setAlpha(0.85f);
                    });
                }
            });
        }
    }

    private void speakText(String text) {
        if (tts == null || !ttsReady || text == null || text.trim().isEmpty()) {
            Toast.makeText(this, "ระบบเสียงยังไม่พร้อม", Toast.LENGTH_SHORT).show();
            return;
        }
        stopSpeaking();

        String clean = text
                .replaceAll("```[\\s\\S]*?```", " (โค้ด) ")
                .replaceAll("[#*_`]", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (clean.length() > 1200) {
            clean = clean.substring(0, 1200) + "...";
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "ai_reply");
        } else {
            tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null);
        }
        isSpeaking = true;
        if (btnSpeak != null) btnSpeak.setAlpha(1f);
    }

    private void stopSpeaking() {
        if (tts != null) {
            tts.stop();
        }
        isSpeaking = false;
        if (btnSpeak != null) btnSpeak.setAlpha(0.85f);
    }

    private void setupWebView() {
        WebSettings settings = webAiChat.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        webAiChat.setBackgroundColor(Color.parseColor("#1A1B26"));
        webAiChat.addJavascriptInterface(new AiBridge(), "NexusAI");
    }

    private void loadEmptyChat() {
        String html = "<html><body style='background:#1A1B26;color:#A9B1D6;"
                + "font-family:sans-serif;padding:16px;'>"
                + "<p style='color:#565F89;'>เริ่มสนทนากับ AI ได้เลย</p>"
                + "</body></html>";
        webAiChat.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
    }

    private void sendMessage() {
        String msg = etAiInput.getText().toString().trim();
        if (msg.isEmpty() || isWaitingReply) return;

        if (!geminiAssistant.hasApiKey()) {
            Toast.makeText(this,
                    "ยังไม่มี Groq API Key\nไปตั้งค่าที่ AI Settings",
                    Toast.LENGTH_LONG).show();
            appendAiBubble("❌ ไม่พบ API Key — กรุณาไปหน้า AI Settings แล้วบันทึก groq_api_key");
            return;
        }

        stopSpeaking();
        etAiInput.setText("");
        appendUserBubble(msg);
        appendAiBubble("⏳ กำลังคิด...");
        isWaitingReply = true;

        geminiAssistant.askAI(msg, new GeminiAssistant.AICallback() {
            @Override
            public void onSuccess(String responseText) {
                runOnUiThread(() -> {
                    isWaitingReply = false;
                    lastAiReply = responseText;
                    replaceLastAiBubble(responseText);
                    speakText(responseText);
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    isWaitingReply = false;
                    lastAiReply = "";
                    replaceLastAiBubble("❌ " + errorMessage);
                });
            }
        });
    }

    private void replaceLastAiBubble(String text) {
        String marker = "⏳ กำลังคิด...";
        int divStart = chatHistory.lastIndexOf("<div style='margin:12px 0;text-align:left;'>");
        if (divStart >= 0 && chatHistory.indexOf(marker, divStart) >= 0) {
            chatHistory = chatHistory.substring(0, divStart);
        }
        appendAiBubble(text);
    }

    private void appendUserBubble(String text) {
        chatHistory += "<div style='margin:12px 0;text-align:right;'>"
                + "<span style='background:#3B4261;color:#C0CAF5;padding:10px 14px;"
                + "border-radius:16px 16px 4px 16px;display:inline-block;max-width:85%;'>"
                + escapeHtml(text).replace("\n", "<br>") + "</span></div>";
        reloadChat();
    }

    private void appendAiBubble(String text) {
        chatHistory += "<div style='margin:12px 0;text-align:left;'>"
                + "<div style='background:#24283B;color:#A9B1D6;padding:12px 14px;"
                + "border-radius:16px 16px 16px 4px;display:inline-block;max-width:92%;"
                + "text-align:left;line-height:1.45;'>"
                + formatAiHtml(text)
                + "</div></div>";
        reloadChat();
    }

    private String formatAiHtml(String text) {
        if (text == null) return "";

        StringBuilder out = new StringBuilder();
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "```([a-zA-Z0-9]*)\\s*\\n([\\s\\S]*?)```");
        java.util.regex.Matcher m = p.matcher(text);

        int last = 0;
        while (m.find()) {
            out.append(escapeHtml(text.substring(last, m.start())).replace("\n", "<br>"));

            String lang = m.group(1) != null ? m.group(1) : "";
            String code = m.group(2) != null ? m.group(2) : "";
            if (code.endsWith("\n")) code = code.substring(0, code.length() - 1);

            String b64 = Base64.encodeToString(
                    code.getBytes(StandardCharsets.UTF_8),
                    Base64.NO_WRAP);

            out.append("<div style='margin:10px 0;background:#1A1B26;border:1px solid #3B4261;")
                    .append("border-radius:10px;overflow:hidden;'>");

            out.append("<div style='display:flex;justify-content:space-between;align-items:center;")
                    .append("padding:6px 10px;background:#16161E;border-bottom:1px solid #3B4261;'>")
                    .append("<span style='color:#7AA2F7;font-size:12px;'>")
                    .append(escapeHtml(lang.isEmpty() ? "code" : lang))
                    .append("</span><div>")
                    .append("<button type='button' onclick=\"NexusAI.copyTextBase64('")
                    .append(b64)
                    .append("')\" style='background:#3B4261;color:#C0CAF5;border:none;")
                    .append("border-radius:6px;padding:4px 10px;font-size:12px;margin-right:6px;'>")
                    .append("Copy</button>")
                    .append("<button type='button' onclick=\"NexusAI.insertIntoEditorBase64('")
                    .append(b64)
                    .append("')\" style='background:#7C3AED;color:#fff;border:none;")
                    .append("border-radius:6px;padding:4px 10px;font-size:12px;'>")
                    .append("ใส่ Editor</button>")
                    .append("</div></div>");

            out.append("<pre style='margin:0;padding:12px;overflow-x:auto;")
                    .append("font-family:monospace;font-size:13px;line-height:1.55;")
                    .append("color:#C0CAF5;white-space:pre;'>")
                    .append(highlightCode(code, lang))
                    .append("</pre></div>");

            last = m.end();
        }

        if (last < text.length()) {
            out.append(escapeHtml(text.substring(last)).replace("\n", "<br>"));
        }
        if (out.length() == 0) {
            return escapeHtml(text).replace("\n", "<br>");
        }
        return out.toString();
    }

    /** Syntax highlight แบบง่าย (Tokyo Night) */
    private String highlightCode(String code, String lang) {
        if (code == null || code.isEmpty()) return "";

        String l = lang == null ? "" : lang.toLowerCase(Locale.US);
        String escaped = escapeHtml(code);

        final String KW = "<span style='color:#BB9AF7'>";
        final String TYP = "<span style='color:#7AA2F7'>";
        final String STR = "<span style='color:#9ECE6A'>";
        final String CM = "<span style='color:#565F89'>";
        final String NUM = "<span style='color:#FF9E64'>";
        final String ANN = "<span style='color:#E0AF68'>";
        final String END = "</span>";

        // string
        escaped = escaped.replaceAll(
                "(&quot;.*?&quot;|&#39;.*?&#39;)",
                STR + "$1" + END);

        // comment
        if (l.contains("xml") || l.contains("html")) {
            escaped = escaped.replaceAll("(&lt;!--[\\s\\S]*?--&gt;)", CM + "$1" + END);
        } else {
            escaped = escaped.replaceAll("(//[^\\n]*)", CM + "$1" + END);
            escaped = escaped.replaceAll("(/\\*[\\s\\S]*?\\*/)", CM + "$1" + END);
        }

        // number
        escaped = escaped.replaceAll("\\b(\\d+\\.?\\d*[fFdDlL]?)\\b", NUM + "$1" + END);

        // annotation
        escaped = escaped.replaceAll("(@[A-Za-z_][A-Za-z0-9_]*)", ANN + "$1" + END);

        String keywords;
        if (l.contains("xml") || l.contains("html")) {
            escaped = escaped.replaceAll(
                    "&lt;(/?)([a-zA-Z][a-zA-Z0-9_.]*)",
                    "&lt;$1" + TYP + "$2" + END);
            escaped = escaped.replaceAll(
                    "\\b(android:[a-zA-Z0-9_]+|app:[a-zA-Z0-9_]+)\\b",
                    KW + "$1" + END);
            keywords = "true|false|match_parent|wrap_content|fill_parent";
        } else if (l.contains("kt") || l.contains("kotlin")) {
            keywords = "package|import|class|object|interface|fun|val|var|if|else|when|"
                    + "for|while|return|null|true|false|is|in|as|this|super|"
                    + "override|private|public|internal|protected|data|sealed|"
                    + "companion|init|try|catch|finally|throw|by|lateinit|const";
        } else {
            keywords = "package|import|class|interface|enum|extends|implements|"
                    + "public|private|protected|static|final|abstract|void|return|"
                    + "if|else|for|while|do|switch|case|break|continue|new|this|super|"
                    + "try|catch|finally|throw|throws|boolean|int|long|float|double|"
                    + "char|byte|short|null|true|false|instanceof|synchronized|"
                    + "volatile|transient|native|strictfp|assert|default";
        }

        escaped = escaped.replaceAll("\\b(" + keywords + ")\\b", KW + "$1" + END);

        if (!l.contains("xml") && !l.contains("html")) {
            escaped = escaped.replaceAll(
                    "\\b([A-Z][A-Za-z0-9_]*)\\b",
                    TYP + "$1" + END);
        }

        return escaped;
    }

    private void reloadChat() {
        String html = "<html><body style='background:#1A1B26;color:#A9B1D6;"
                + "font-family:sans-serif;padding:12px;'>"
                + chatHistory
                + "</body></html>";
        webAiChat.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
        webAiChat.post(() -> webAiChat.pageDown(true));
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    public class AiBridge {

        @android.webkit.JavascriptInterface
        public void copyTextBase64(String b64) {
            runOnUiThread(() -> {
                String decoded = decodeBase64(b64);
                android.content.ClipboardManager cm =
                        (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("code", decoded));
                    Toast.makeText(AiChatActivity.this, "📋 คัดลอกโค้ดแล้ว", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @android.webkit.JavascriptInterface
        public void insertIntoEditorBase64(String b64) {
            runOnUiThread(() -> {
                String decoded = decodeBase64(b64);
                android.content.Intent data = new android.content.Intent();
                data.putExtra("ai_insert_code", decoded);
                setResult(RESULT_OK, data);
                Toast.makeText(AiChatActivity.this, "✨ ส่งโค้ดเข้า Editor แล้ว", Toast.LENGTH_SHORT).show();
                finish();
            });
        }

        private String decodeBase64(String b64) {
            try {
                byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
                return new String(bytes, StandardCharsets.UTF_8);
            } catch (Exception e) {
                return "";
            }
        }
    }

    @Override
    protected void onDestroy() {
        stopSpeaking();
        if (tts != null) {
            tts.shutdown();
            tts = null;
        }
        try {
            if (webAiChat != null) {
                webAiChat.stopLoading();
                webAiChat.loadUrl("about:blank");
                webAiChat.removeAllViews();
                webAiChat.destroy();
                webAiChat = null;
            }
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }
}