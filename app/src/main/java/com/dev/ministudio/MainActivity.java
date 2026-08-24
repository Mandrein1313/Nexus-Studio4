package com.dev.ministudio;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue; 
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView; 
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;
import android.widget.FrameLayout;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.langs.java.JavaLanguage;
import io.github.rosemoe.sora.event.ContentChangeEvent;
import com.dev.ministudio.fs.FileSystemManager;
import com.dev.ministudio.model.ProjectModel;
import com.dev.ministudio.model.FileNode;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import android.text.SpannableString;
import android.content.Intent;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import androidx.viewpager2.widget.ViewPager2;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.transport.URIish;
import android.os.Build;
import androidx.core.view.WindowCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import android.content.SharedPreferences;
import com.dev.ministudio.ai.AiChatActivity;
import android.content.Intent;
import com.dev.ministudio.utils.LogcatReader;
import com.dev.ministudio.ui.ExitConfirmDialog;


public class MainActivity extends AppCompatActivity {

    // Views
    private TextView tvSaveStatus, tvFilePath;
    private CodeEditor codeEditor; 
    private DrawerLayout drawerLayout;
    private ListView treeView; 
    private LinearLayout searchBar;
    private android.widget.EditText etFind, etReplace; 
    
    // Tab System Views
    private RecyclerView tabRecyclerView;
    private TabAdapter tabAdapter;

    // 🌟 ระบบ Dialog เต็มหน้าจอชุดใหม่ (Full-screen Panel)
    private android.app.Dialog fullPanelDialog;
    private TabLayout dialogTabLayout;
    private ViewPager2 dialogViewPager;
    private PanelPagerAdapter dialogPanelAdapter;
    
    private TextView tvConsole;
        
    // Controllers & Models
    private ProjectModel currentProject;

    // Utils
    private final Handler autoSaveHandler = new Handler(); 
    private Runnable saveRunnable;
    
    
    private float currentCodeFontSize = 14.0f; 

    // 🛠️ แยกออกไปจัดการที่ระบบภายนอกคลาสหลัก
    private ProjectTreeManager projectTreeManager;

    private BuildEnvironmentManager buildEnvManager;
    private static final int PICK_FILE_REQUEST_CODE = 2026; 
    
    private ProjectDialogManager dialogManager;
    
    // 🤖 ตัวจัดการวิเคราะห์เลย์เอาต์ระดับสูงเพื่อความเสถียร
    public com.dev.ministudio.AiLayoutAnalyzer aiLayoutAnalyzer; 
    
    private RecyclerView rvErrorPanel;
    
    // 🌟 ระบบ XML Preview
    private FrameLayout previewContainer;
    private boolean isPreviewMode = false; 
    private String chatHistory = "";
    // Views ตัวใหม่เพิ่มเติม
    private LinearLayout emptyStateView;
    private String pendingProjectName = "";
    private boolean isLightEditorTheme = false;
    private boolean isShortcutExpanded = false;
    private EditorSearchManager editorSearchManager;
   // เพิ่มตัวแปรนี้ในส่วนขอบเขตของคลาส MainActivity
    private LogcatReader logcatReader;
    private LocalBuildManager localBuildManager;
    private static final int REQUEST_AI_CHAT = 9101;
    

   
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    WindowCompat.setDecorFitsSystemWindows(getWindow(), true);

    getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
    getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

    int barColor = android.graphics.Color.parseColor("#1A1B26");
    getWindow().setStatusBarColor(barColor);
    getWindow().setNavigationBarColor(barColor);

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(0);
    }

    setContentView(R.layout.activity_main);

    View drawerContent = findViewById(R.id.drawer_content);
    if (drawerContent != null) {
        int statusBarHeight = 0;
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resId > 0) {
            statusBarHeight = getResources().getDimensionPixelSize(resId);
        }
        int extra = (int) (8 * getResources().getDisplayMetrics().density);
        drawerContent.setPadding(
                drawerContent.getPaddingLeft(),
                statusBarHeight + extra,
                drawerContent.getPaddingRight(),
                drawerContent.getPaddingBottom()
        );
    }

    buildEnvManager = new BuildEnvironmentManager(this);

    initViews();
    setupLogic();

    // ===== ยืนยันตอนกดกลับ =====
    getOnBackPressedDispatcher().addCallback(this,
            new androidx.activity.OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    if (drawerLayout != null
                            && drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
                        drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START);
                        return;
                    }
                    if (editorSearchManager != null && editorSearchManager.isVisible()) {
                        editorSearchManager.hide();
                        return;
                    }
                    if (fullPanelDialog != null && fullPanelDialog.isShowing()) {
                        fullPanelDialog.dismiss();
                        return;
                    }
                    com.dev.ministudio.ui.ExitConfirmDialog.show(MainActivity.this);
                }
            });
}
private void initViews() {
    codeEditor = findViewById(R.id.codeEditor);
    tvFilePath = findViewById(R.id.tvFilePath);
    tvSaveStatus = findViewById(R.id.tvSaveStatus);
    emptyStateView = findViewById(R.id.emptyStateView);
    searchBar = findViewById(R.id.searchBar);
    etFind = findViewById(R.id.etFind);
    etReplace = findViewById(R.id.etReplace);

    treeView = findViewById(R.id.treeView);
    tabRecyclerView = findViewById(R.id.tabRecyclerView);
    tabRecyclerView.setLayoutManager(
            new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);

    drawerLayout = findViewById(R.id.drawer_layout);
    ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
            this, drawerLayout, toolbar, android.R.string.ok, android.R.string.cancel);
    drawerLayout.addDrawerListener(toggle);
    toggle.syncState();

    // ===== ระบบค้นหา / แทนที่ (แยกคลาส) =====
    if (codeEditor != null) {
        editorSearchManager = new EditorSearchManager(this, codeEditor);
        editorSearchManager.bindViews(drawerLayout != null ? drawerLayout : findViewById(android.R.id.content));
    }

    setupShortcutBar();

    // ===== ปุ่มด้านล่าง =====
    ImageView btnToggleShortcut = findViewById(R.id.btnToggleShortcut);
    View shortcutRow = findViewById(R.id.shortcutRow);
    ImageView btnColorPicker = findViewById(R.id.btnColorPicker);
    ImageView btnPreview = findViewById(R.id.btnPreview);
    ImageView btnFileSearch = findViewById(R.id.btnFileSearch);
    ImageView btnGitPush = findViewById(R.id.btnGitPush);
    ImageView btnMainAi = findViewById(R.id.btnMainAi);
    ImageView btnUndo = findViewById(R.id.btnUndo);
    ImageView btnRedo = findViewById(R.id.btnRedo);

    // ซ่อนแถบสัญลักษณ์ตอนเริ่ม
    isShortcutExpanded = false;
    if (shortcutRow != null) {
        shortcutRow.setVisibility(View.GONE);
    }
    if (btnToggleShortcut != null) {
        btnToggleShortcut.setImageResource(R.drawable.ic_expand_more_24);
    }

    if (btnUndo != null) {
        btnUndo.setOnClickListener(v -> {
            if (codeEditor != null) codeEditor.undo();
        });
    }
    if (btnRedo != null) {
        btnRedo.setOnClickListener(v -> {
            if (codeEditor != null) codeEditor.redo();
        });
    }

    if (btnColorPicker != null) {
        btnColorPicker.setOnClickListener(v -> showFullColorPickerDialog());
    }

    if (btnPreview != null) {
        btnPreview.setOnClickListener(v -> toggleXmlPreview());
    }

    if (btnFileSearch != null) {
        btnFileSearch.setOnClickListener(v -> showFileSearchDialog());
    }

    if (btnGitPush != null) {
        btnGitPush.setOnClickListener(v -> {
            if (currentProject != null) {
                pushChangesToGithub(currentProject.getProjectName());
            } else {
                showToast("⚠️ กรุณาเปิดโปรเจกต์ก่อนทำการ Push โค้ด");
            }
        });
    }

    // ผูกคลิกแค่จุดเดียว เปิด AI Chat
    if (btnMainAi != null) {
        btnMainAi.setOnClickListener(v -> openAiChat());
    }

    // ===== Toggle ย่อ/ขยาย =====
    if (btnToggleShortcut != null && shortcutRow != null) {
        btnToggleShortcut.setOnClickListener(v -> {
            if (isShortcutExpanded) {
                isShortcutExpanded = false;
                shortcutRow.animate()
                        .alpha(0f)
                        .translationY(-30f)
                        .setDuration(180)
                        .setInterpolator(new android.view.animation.AccelerateInterpolator())
                        .withEndAction(() -> {
                            shortcutRow.setVisibility(View.GONE);
                            shortcutRow.setAlpha(1f);
                            shortcutRow.setTranslationY(0f);
                        })
                        .start();
                btnToggleShortcut.setImageResource(R.drawable.ic_expand_more_24);
            } else {
                isShortcutExpanded = true;
                shortcutRow.setVisibility(View.VISIBLE);
                shortcutRow.setAlpha(0f);
                shortcutRow.setTranslationY(-30f);
                shortcutRow.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(220)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                        .start();
                btnToggleShortcut.setImageResource(R.drawable.ic_expand_less_24);
            }
        });
    }

    rvErrorPanel = findViewById(R.id.rvErrorPanel);
    if (rvErrorPanel != null) {
        rvErrorPanel.setLayoutManager(new LinearLayoutManager(this));
    }

    previewContainer = findViewById(R.id.previewContainer);
}


private void setupLogic() {
    aiLayoutAnalyzer = new com.dev.ministudio.AiLayoutAnalyzer(this);
    dialogManager = new ProjectDialogManager(this, parentNode -> {
        triggerTreeRefresh(parentNode);
    });

    if (codeEditor == null) return;

    codeEditor.setEditorLanguage(new JavaLanguage());

    // โหลดธีมจากหน้า Projects
    SharedPreferences appPrefs = getSharedPreferences("AppSettings", MODE_PRIVATE);
    boolean isLight = appPrefs.getBoolean("editor_light_theme", false);
    if (isLight) {
        codeEditor.setColorScheme(new com.dev.ministudio.editor.NexusLightColorScheme());
    } else {
        codeEditor.setColorScheme(new com.dev.ministudio.editor.NexusColorScheme());
    }
    isLightEditorTheme = isLight;

    codeEditor.setTextSize(currentCodeFontSize);
    codeEditor.setTypefaceText(android.graphics.Typeface.MONOSPACE);
    codeEditor.setLineSpacing(2f, 1.2f);
    codeEditor.setWordwrap(false);
    codeEditor.setUndoEnabled(true);
    codeEditor.setHighlightCurrentBlock(true);

    // Auto-Save + รีเฟรช Preview (ไม่มี AI suggestion แล้ว)
    codeEditor.subscribeEvent(ContentChangeEvent.class, (event, unsubscribe) -> {
        if (tvSaveStatus != null) {
            tvSaveStatus.setText("Editing...");
            tvSaveStatus.setTextColor(android.graphics.Color.parseColor("#FF9E64"));
        }

        autoSaveHandler.removeCallbacks(saveRunnable);
        saveRunnable = () -> {
            saveFile();
            if (tvSaveStatus != null) {
                tvSaveStatus.setText("Saved");
                tvSaveStatus.setTextColor(android.graphics.Color.parseColor("#9ECE6A"));
            }
        };
        autoSaveHandler.postDelayed(saveRunnable, 1500);

        // รีเฟรช Preview อัตโนมัติตอนแก้ XML
        if (isPreviewMode && previewContainer != null && codeEditor != null) {
            previewContainer.postDelayed(() -> {
                if (!isPreviewMode || previewContainer == null || codeEditor == null) return;
                try {
                    View v = new XmlPreviewManager(MainActivity.this)
                            .inflateXml(codeEditor.getText().toString());
                    previewContainer.removeAllViews();
                    previewContainer.addView(v, new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
                } catch (Exception ignored) {
                }
            }, 600);
        }
    });

    // แสดงสีที่แถบสถานะเมื่อ cursor อยู่บนรหัสสี
    codeEditor.subscribeEvent(
            io.github.rosemoe.sora.event.SelectionChangeEvent.class,
            (event, unsubscribe) -> showColorPreviewIfNeeded()
    );

    // แตะรหัสสี → เปิด Edit Color
    codeEditor.subscribeEvent(io.github.rosemoe.sora.event.ClickEvent.class, (event, unsubscribe) -> {
        if (codeEditor == null) return;

        codeEditor.post(() -> {
            if (codeEditor.getCursor() == null) return;

            int line = codeEditor.getCursor().getLeftLine();
            int col = codeEditor.getCursor().getLeftColumn();

            String lineText;
            try {
                lineText = codeEditor.getText().getLineString(line);
            } catch (Exception e) {
                return;
            }
            if (lineText == null || lineText.isEmpty()) return;

            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    "#(?:[0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{3})"
            );
            java.util.regex.Matcher matcher = pattern.matcher(lineText);

            String bestHex = null;
            int bestStart = -1;
            int bestEnd = -1;
            int bestDist = Integer.MAX_VALUE;

            while (matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();
                if (col >= start - 1 && col <= end) {
                    int mid = (start + end) / 2;
                    int dist = Math.abs(col - mid);
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestHex = matcher.group();
                        bestStart = start;
                        bestEnd = end;
                    }
                }
            }

            if (bestHex != null) {
                showFullColorPickerDialog(bestHex, line, bestStart, line, bestEnd);
            }
        });
    });

    // โหลดโปรเจกต์
    String projectName = getIntent().getStringExtra("projectName");
    if (projectName != null) {
        String rootPath = "/sdcard/MiniStudio/" + projectName;
        currentProject = new ProjectModel(projectName, rootPath);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(currentProject.getProjectName());
        }
        setupTabLogic();
        if (treeView != null) {
            projectTreeManager = new ProjectTreeManager(this, treeView);
            projectTreeManager.initializeFileTree();
        }
        setEditorActiveState(false);
    }
}
private void showColorPreviewIfNeeded() {
    if (codeEditor == null || codeEditor.getCursor() == null || tvSaveStatus == null) {
        return;
    }

    int line = codeEditor.getCursor().getLeftLine();
    int col = codeEditor.getCursor().getLeftColumn();

    String lineText;
    try {
        lineText = codeEditor.getText().getLineString(line);
    } catch (Exception e) {
        return;
    }
    if (lineText == null || lineText.isEmpty()) return;

    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})\\b"
    );
    java.util.regex.Matcher matcher = pattern.matcher(lineText);

    String foundHex = null;
    while (matcher.find()) {
        if (col >= matcher.start() && col <= matcher.end()) {
            foundHex = matcher.group();
            break;
        }
    }

    if (foundHex != null) {
        int color = parseHexColor(foundHex);
        if (color != 0) {
            tvSaveStatus.setText("● " + foundHex);
            tvSaveStatus.setTextColor(color);
            return;
        }
    }

    // ออกจากรหัสสี → คืน Saved ถ้ากำลังโชว์ preview อยู่
    CharSequence cur = tvSaveStatus.getText();
    if (cur != null && cur.toString().startsWith("●")) {
        tvSaveStatus.setText("Saved");
        tvSaveStatus.setTextColor(android.graphics.Color.parseColor("#9ECE6A"));
    }
}

private int parseHexColor(String hex) {
    try {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() == 3) {
            h = "" + h.charAt(0) + h.charAt(0)
                    + h.charAt(1) + h.charAt(1)
                    + h.charAt(2) + h.charAt(2);
            return android.graphics.Color.parseColor("#" + h);
        } else if (h.length() == 6) {
            return android.graphics.Color.parseColor("#" + h);
        } else if (h.length() == 8) {
            return (int) Long.parseLong(h, 16);
        }
    } catch (Exception ignored) {
    }
    return 0;
}
private void showFullPanelDialog(int initialTabPosition) {
    if (fullPanelDialog != null && fullPanelDialog.isShowing()) {
        if (dialogViewPager != null) {
            dialogViewPager.setCurrentItem(0, true);
        }
        return;
    }

    fullPanelDialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar);
    fullPanelDialog.setContentView(R.layout.dialog_full_console_panel);
    fullPanelDialog.setCancelable(true);

    if (fullPanelDialog.getWindow() != null) {
        android.view.Window window = fullPanelDialog.getWindow();
        window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, true);
        window.setStatusBarColor(android.graphics.Color.parseColor("#1F2335"));
        window.setNavigationBarColor(android.graphics.Color.parseColor("#1A1B26"));
    }

    dialogTabLayout = fullPanelDialog.findViewById(R.id.tabLayout);
    dialogViewPager = fullPanelDialog.findViewById(R.id.viewPager);

    if (dialogTabLayout != null) {
        dialogTabLayout.setVisibility(View.GONE);
    }

    fullPanelDialog.findViewById(R.id.btnCloseConsole)
            .setOnClickListener(v -> fullPanelDialog.dismiss());

    View btnToggleExpand = fullPanelDialog.findViewById(R.id.btnToggleExpand);
    if (btnToggleExpand != null) btnToggleExpand.setVisibility(View.GONE);

    fullPanelDialog.findViewById(R.id.btnClearConsole).setOnClickListener(v -> {
        if (dialogPanelAdapter != null) {
            TextView consoleView = dialogPanelAdapter.getTvConsole();
            if (consoleView != null) consoleView.setText("");
        }
        if (tvConsole != null) tvConsole.setText("");
    });

    // ===== ปุ่ม Logcat =====
    TextView btnLogcat = fullPanelDialog.findViewById(R.id.btnLogcat);
    if (btnLogcat != null) {
        updateLogcatButtonUi(btnLogcat);
        btnLogcat.setOnClickListener(v -> {
            if (logcatReader != null && logcatReader.isRunning()) {
                logcatReader.stop();
                appendConsoleLine("⏹ หยุด Logcat\n",
                        android.graphics.Color.parseColor("#565F89"));
                updateLogcatButtonUi(btnLogcat);
            } else {
                String pkg = null;
                startLogcatMonitor(pkg);
                btnLogcat.postDelayed(() -> updateLogcatButtonUi(btnLogcat), 300);
            }
        });
    }

    // ===== ปุ่ม AI วิเคราะห์ (กดเอง ไม่เด้งอัตโนมัติ) =====
    View btnAiFixer = fullPanelDialog.findViewById(R.id.btnAiFixer);
    if (btnAiFixer != null) {
        btnAiFixer.setOnClickListener(v -> triggerAiErrorFixerPipeline());
    }

    dialogPanelAdapter = new PanelPagerAdapter(this);
    dialogViewPager.setAdapter(dialogPanelAdapter);
    dialogViewPager.setUserInputEnabled(false);

    dialogViewPager.post(() -> {
        if (dialogPanelAdapter != null) {
            tvConsole = dialogPanelAdapter.getTvConsole();
            dialogViewPager.setCurrentItem(0, false);
        }
    });

    fullPanelDialog.setOnDismissListener(dialog -> {
        if (aiLayoutAnalyzer != null) {
            aiLayoutAnalyzer.stopSpeaking();
        }
    });

    fullPanelDialog.show();
}

private void updateLogcatButtonUi(TextView btnLogcat) {
    if (btnLogcat == null) return;
    boolean on = logcatReader != null && logcatReader.isRunning();
    if (on) {
        btnLogcat.setText("⏹ Stop");
        btnLogcat.setTextColor(android.graphics.Color.parseColor("#F7768E"));
    } else {
        btnLogcat.setText("Logcat");
        btnLogcat.setTextColor(android.graphics.Color.parseColor("#7AA2F7"));
    }
}

    public void handleAiQuery() {
        if (fullPanelDialog == null || !fullPanelDialog.isShowing()) {
            openAiChat();
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (dialogPanelAdapter == null) return;

            android.widget.EditText etAiInput = dialogPanelAdapter.getEtAiInput();
            android.webkit.WebView webAiOutput = dialogPanelAdapter.getWebAiOutput();

            if (etAiInput == null || webAiOutput == null) return;

            // 🎯 เปิดสิทธิ์การใช้งาน JavaScript และผูกสะพานเชื่อมตัวหลัก
            webAiOutput.getSettings().setJavaScriptEnabled(true);
            webAiOutput.getSettings().setDomStorageEnabled(true);
            webAiOutput.removeJavascriptInterface("AndroidBridge");
            webAiOutput.addJavascriptInterface(new WebAppInterface(MainActivity.this), "AndroidBridge");

            String userQuestion = etAiInput.getText().toString().trim();
            if (userQuestion.isEmpty()) {
                chatHistory += "\n\n⚠️ *กรุณาพิมพ์คำถามก่อนครับ*";
                String html = AiHtmlFormatter.convertMarkdownToHtml(chatHistory);
                webAiOutput.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
                return;
            }

            // สั่งหยุดพูดทันทีก่อนที่ AI ตัวใหม่จะประมวลผลคำถามถัดไป (ป้องกันเสียงตีกัน)
            if (aiLayoutAnalyzer != null) {
                aiLayoutAnalyzer.stopSpeaking();
            }

            dialogViewPager.setCurrentItem(1, true);

            chatHistory += "\n\n👤 **คุณ:** " + userQuestion;
            String htmlUser = AiHtmlFormatter.convertMarkdownToHtml(chatHistory);
            webAiOutput.loadDataWithBaseURL(null, htmlUser, "text/html", "utf-8", null);

            String fullPrompt = chatHistory + "\nผู้ใช้ถาม: " + userQuestion;

            aiLayoutAnalyzer.askAi(fullPrompt, new AiLayoutAnalyzer.OnAnalysisListener() {
                @Override
                public void onStart() {
                    runOnUiThread(() -> {
                        try {
                            android.webkit.WebView currentWeb = dialogPanelAdapter.getWebAiOutput();
                            if (currentWeb != null) {
                                currentWeb.getSettings().setJavaScriptEnabled(true);
                                currentWeb.removeJavascriptInterface("AndroidBridge");
                                currentWeb.addJavascriptInterface(new WebAppInterface(MainActivity.this), "AndroidBridge");
                                
                                String tempHtml = AiHtmlFormatter.convertMarkdownToHtml(chatHistory + "\n\n🤖 *AI กำลังคิด...*");
                                currentWeb.loadDataWithBaseURL(null, tempHtml, "text/html", "utf-8", null);
                            }
                        } catch (Exception e) { e.printStackTrace(); }
                    });
                }

                @Override
                public void onSuccess(android.text.SpannableString formattedResult) {
                    runOnUiThread(() -> {
                        try {
                            android.webkit.WebView currentWeb = dialogPanelAdapter.getWebAiOutput();
                            chatHistory += "\n\n🤖 **AI:** " + formattedResult.toString();
                            
                            if (currentWeb != null) {
                                currentWeb.getSettings().setJavaScriptEnabled(true);
                                currentWeb.removeJavascriptInterface("AndroidBridge");
                                currentWeb.addJavascriptInterface(new WebAppInterface(MainActivity.this), "AndroidBridge");
                                
                                String htmlResult = AiHtmlFormatter.convertMarkdownToHtml(chatHistory);
                                currentWeb.loadDataWithBaseURL(null, htmlResult, "text/html", "utf-8", null);
                            }
                        } catch (Exception e) { e.printStackTrace(); }
                    });
                }

                @Override
                public void onError(String errorMessage) {
                    runOnUiThread(() -> {
                        try {
                            android.webkit.WebView currentWeb = dialogPanelAdapter.getWebAiOutput();
                            chatHistory += "\n\n❌ **AI เกิดข้อผิดพลาด:** " + errorMessage;
                            
                            if (currentWeb != null) {
                                currentWeb.getSettings().setJavaScriptEnabled(true);
                                currentWeb.removeJavascriptInterface("AndroidBridge");
                                currentWeb.addJavascriptInterface(new WebAppInterface(MainActivity.this), "AndroidBridge");

                                String htmlError = AiHtmlFormatter.convertMarkdownToHtml(chatHistory);
                                currentWeb.loadDataWithBaseURL(null, htmlError, "text/html", "utf-8", null);
                            }
                        } catch (Exception e) { e.printStackTrace(); }
                    });
                }
            });

            etAiInput.setText("");
        }, 300);
    }
    private void openAiChat() {
    Intent intent = new Intent(this, com.dev.ministudio.ai.AiChatActivity.class);

    if (currentProject != null) {
        intent.putExtra(com.dev.ministudio.ai.AiChatActivity.EXTRA_PROJECT_NAME,
                currentProject.getProjectName());
    }

    // ส่งโค้ดที่เลือกอยู่ (ถ้ามี)
    if (codeEditor != null && codeEditor.getCursor() != null) {
        try {
            String selected = codeEditor.getText()
                    .subContent(
                            codeEditor.getCursor().getLeftLine(),
                            codeEditor.getCursor().getLeftColumn(),
                            codeEditor.getCursor().getRightLine(),
                            codeEditor.getCursor().getRightColumn()
                    ).toString();
            if (selected != null && !selected.trim().isEmpty()) {
                intent.putExtra(com.dev.ministudio.ai.AiChatActivity.EXTRA_CODE_SNIPPET, selected);
            }
        } catch (Exception ignored) {
        }
    }

    startActivityForResult(intent, REQUEST_AI_CHAT);
}
/** เปิด/ปิดสถานะ Editor หลังเปิดไฟล์หรือปิดแท็บ */
public void setEditorActiveState(boolean active) {
    if (codeEditor != null) {
        codeEditor.setEnabled(active);
        try {
            codeEditor.setEditable(active);
        } catch (Exception ignored) {
        }
        codeEditor.setVisibility(active ? View.VISIBLE : View.GONE);
    }

    if (emptyStateView != null) {
        emptyStateView.setVisibility(active ? View.GONE : View.VISIBLE);
    }

    setViewEnabled(findViewById(R.id.btnUndo), active);
    setViewEnabled(findViewById(R.id.btnRedo), active);
    setViewEnabled(findViewById(R.id.btnColorPicker), active);
    setViewEnabled(findViewById(R.id.btnFileSearch), active);
    setViewEnabled(findViewById(R.id.btnGitPush), active);
    setViewEnabled(findViewById(R.id.btnPreview), active);
}

private void setViewEnabled(View v, boolean enabled) {
    if (v != null) {
        v.setEnabled(enabled);
        v.setAlpha(enabled ? 1f : 0.4f);
    }
}


    // 🌟 ระบบตรวจจับสกัดกั้นและแก้บั๊กอัจฉริยะ (AI Error Fixer Pipeline) สำหรับระบบที่ 1 ตัวใหม่ล่าสุดครับท่าน
 public void triggerAiErrorFixerPipeline() {
    if (codeEditor == null || currentProject == null) {
        showToast("⚠️ ไม่สามารถเข้าถึงตัวจัดเตรียมรหัสซอร์สโค้ดได้");
        return;
    }

    // 1. ดึง Error Log จาก Console
    String consoleLog = "";
    if (dialogPanelAdapter != null && dialogPanelAdapter.getTvConsole() != null) {
        consoleLog = dialogPanelAdapter.getTvConsole().getText().toString().trim();
    } else if (tvConsole != null) {
        consoleLog = tvConsole.getText().toString().trim();
    }

    if (consoleLog.isEmpty() || consoleLog.equals("> Ready to build...")) {
        showToast("🔎 ยังไม่มีบันทึกข้อผิดพลาด (Error Log) ในคอนโซล");
        return;
    }

    // 2. ไฟล์ + โค้ดปัจจุบัน
    java.io.File currentFile = currentProject.getCurrentOpenFile();
    final String fileName = (currentFile != null) ? currentFile.getName() : "UnknownFile.java";
    String currentSourceCode = codeEditor.getText().toString();

    // 3. หยุดเสียง AI เดิม (ถ้ามี)
    if (aiLayoutAnalyzer != null) {
        aiLayoutAnalyzer.stopSpeaking();
    }

    // 4. สร้างข้อความ error ส่งเข้า Dialog
    final String errorContext =
            "ชื่อไฟล์: " + fileName + "\n\n"
                    + "❌ Error Log จาก Console:\n"
                    + consoleLog + "\n\n"
                    + "📄 ซอร์สโค้ดปัจจุบัน:\n"
                    + currentSourceCode;

    // 5. เปิด UI AI Build Doctor
    runOnUiThread(() -> showAiBuildDoctorDialog(errorContext, fileName));
}

/** เปิด dialog_ai_doctor.xml */
private void showAiBuildDoctorDialog(String errorContext, String fileName) {
    final android.app.Dialog dialog = new android.app.Dialog(this);
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
    dialog.setContentView(R.layout.dialog_ai_doctor);
    dialog.setCancelable(true);

    if (dialog.getWindow() != null) {
        dialog.getWindow().setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    final android.widget.TextView tvAiOutput = dialog.findViewById(R.id.tvAiOutput);
    final android.widget.EditText etAiInput = dialog.findViewById(R.id.etAiInput);
    android.widget.Button btnSend = dialog.findViewById(R.id.btnSendToAi);
    android.widget.Button btnClose = dialog.findViewById(R.id.btnDialogClose);

    if (btnClose != null) {
        btnClose.setOnClickListener(v -> dialog.dismiss());
    }

    if (tvAiOutput != null) {
        tvAiOutput.setText("📋 ไฟล์: " + fileName
                + "\n\n⏳ กำลังส่ง error ให้ AI วิเคราะห์...");
    }

    // วิเคราะห์รอบแรกอัตโนมัติ
    runBuildDoctorAnalysis(errorContext, fileName, tvAiOutput);

    if (btnSend != null) {
        btnSend.setOnClickListener(v -> {
            String extra = etAiInput != null ? etAiInput.getText().toString().trim() : "";
            String promptBody = errorContext;
            if (!extra.isEmpty()) {
                promptBody = errorContext + "\n\nคำถามเพิ่มเติมจากผู้ใช้:\n" + extra;
            }
            if (tvAiOutput != null) {
                tvAiOutput.setText("⏳ กำลังวิเคราะห์...");
            }
            if (etAiInput != null) etAiInput.setText("");
            runBuildDoctorAnalysis(promptBody, fileName, tvAiOutput);
        });
    }

    dialog.show();
}

private void runBuildDoctorAnalysis(String errorContext, String fileName,
                                    android.widget.TextView tvAiOutput) {
    String prompt =
            "คุณคือระบบ AI ตรวจจับและแก้ไขบั๊ก Android (MiniStudio)\n\n"
                    + "ไฟล์: " + fileName + "\n\n"
                    + errorContext + "\n\n"
                    + "กรุณา:\n"
                    + "1. อธิบายสั้นๆ ว่าพังที่ไหน สาเหตุอะไร\n"
                    + "2. วิธีแก้เป็นข้อๆ\n"
                    + "3. ถ้าแก้โค้ดได้ ส่งโค้ดทั้งไฟล์ในบล็อก ```java";

    com.dev.ministudio.ai.GeminiAssistant ai =
            new com.dev.ministudio.ai.GeminiAssistant(this);

    if (!ai.hasApiKey()) {
        if (tvAiOutput != null) {
            tvAiOutput.setText("❌ ไม่พบ Groq API Key\nไปตั้งค่าที่ AI Settings ก่อน");
        }
        return;
    }

    ai.askAI(prompt, new com.dev.ministudio.ai.GeminiAssistant.AICallback() {
        @Override
        public void onSuccess(String responseText) {
            runOnUiThread(() -> {
                if (tvAiOutput != null) {
                    tvAiOutput.setText(responseText);
                }
            });
        }

        @Override
        public void onError(String errorMessage) {
            runOnUiThread(() -> {
                if (tvAiOutput != null) {
                    tvAiOutput.setText("❌ " + errorMessage);
                }
            });
        }
    });
}



private void runBuildDoctorAnalysis(String errorOrPrompt, android.widget.TextView tvAiOutput) {
    String prompt =
            "คุณเป็นผู้ช่วยวิเคราะห์ Android Build Error\n"
                    + "นี่คือข้อมูล error:\n"
                    + errorOrPrompt
                    + "\n\nวิเคราะห์สาเหตุและวิธีแก้เป็นภาษาไทย กระชับ เป็นข้อๆ";

    // ใช้ GeminiAssistant (Groq) ตัวเดียวกับแชท
    com.dev.ministudio.ai.GeminiAssistant ai =
            new com.dev.ministudio.ai.GeminiAssistant(this);

    if (!ai.hasApiKey()) {
        if (tvAiOutput != null) {
            tvAiOutput.setText("❌ ไม่พบ Groq API Key\nไปตั้งค่าที่ AI Settings ก่อน");
        }
        return;
    }

    ai.askAI(prompt, new com.dev.ministudio.ai.GeminiAssistant.AICallback() {
        @Override
        public void onSuccess(String responseText) {
            runOnUiThread(() -> {
                if (tvAiOutput != null) {
                    tvAiOutput.setText(responseText);
                }
            });
        }

        @Override
        public void onError(String errorMessage) {
            runOnUiThread(() -> {
                if (tvAiOutput != null) {
                    tvAiOutput.setText("❌ " + errorMessage);
                }
            });
        }
    });
}

private void toggleXmlPreview() {
    if (codeEditor == null || previewContainer == null) {
        showToast("⚠️ ไม่พบแผง Preview");
        return;
    }

    // เข้าโหมด Preview
    if (!isPreviewMode) {
        File current = currentProject != null ? currentProject.getCurrentOpenFile() : null;
        String name = current != null ? current.getName().toLowerCase() : "";

        // แนะนำเฉพาะ layout XML
        if (!name.endsWith(".xml")) {
            showToast("⚠️ Preview รองรับไฟล์ .xml (layout)");
            // ยังอนุญาตต่อได้ถ้าอยาก — หรือ return;
        }
        if (name.equals("colors.xml") || name.equals("strings.xml")
                || name.equals("styles.xml") || name.equals("themes.xml")
                || name.contains("AndroidManifest")) {
            showToast("⚠️ ไฟล์นี้ไม่ใช่ layout — ผลพรีวิวอาจว่าง");
        }

        try {
            String xml = codeEditor.getText().toString();
            XmlPreviewManager previewManager = new XmlPreviewManager(this);
            View generated = previewManager.inflateXml(xml);

            previewContainer.removeAllViews();
            previewContainer.addView(generated,
                    new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));

            codeEditor.setVisibility(View.GONE);
            if (emptyStateView != null) emptyStateView.setVisibility(View.GONE);
            previewContainer.setVisibility(View.VISIBLE);

            isPreviewMode = true;
            showToast("✨ Preview layout");
            invalidateOptionsMenu();
        } catch (Exception e) {
            showToast("❌ " + e.getMessage());
        }
    } else {
        // กลับไปแก้โค้ด
        previewContainer.setVisibility(View.GONE);
        previewContainer.removeAllViews();
        codeEditor.setVisibility(View.VISIBLE);
        isPreviewMode = false;
        invalidateOptionsMenu();
        showToast("✏️ กลับสู่โหมดแก้ไข");
    }
}

    private void startCloudBuildPipeline() {
        if (currentProject == null) {
            showToast("กรุณาเปิดโปรเจกต์ก่อนทำการรัน");
            return;
        }

        SharedPreferences prefs = getSharedPreferences("GitHubPrefs", Context.MODE_PRIVATE);
        String username = prefs.getString("username", "");
        String savedToken = prefs.getString("token", "");

        if (username.isEmpty() || savedToken.isEmpty()) {
            showToast("❌ ยังไม่ได้ตั้งค่าบัญชี GitHub กรุณาตั้งค่าที่ปุ่มฟันเพืองหน้าแรกก่อนครับ");
            return;
        }

        saveFile(); 
        showFullPanelDialog(0);

        final BuildSummaryAnalyzer analyzer = new BuildSummaryAnalyzer();
        analyzer.clearErrors(); 
        
        final boolean[] isPipelineStopped = {false};

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (dialogPanelAdapter != null) {
                tvConsole = dialogPanelAdapter.getTvConsole();
            }
            if (tvConsole != null) tvConsole.setText("");

            appendLog("##[group]เริ่มขั้นตอนการตั้งค่า & ตรวจสอบโปรเจกต์เบื้องต้น", TerminalColor.LOG_GRAY); 
            appendLog("🔔 [กำลังจัดเตรียมสภาพแวดล้อม...] เริ่มทำงานระบบ Workflow สำเร็จ", TerminalColor.LOG_WHITE);
            appendLog("📂 ที่อยู่โปรเจกต์ (Root Path): " + currentProject.getRootPath(), TerminalColor.BORDER_BLUE); 
            appendLog("##[endgroup]", TerminalColor.LOG_GRAY);

            BuildTaskManager buildTask = new BuildTaskManager(
                MainActivity.this, 
                currentProject.getRootPath(),
                new BuildTaskManager.BuildListener() {
                    
                    @Override 
                    public void onLogAppend(final String text, final int color) { 
                        if (isPipelineStopped[0]) return;

                        String lowerText = text != null ? text.toLowerCase() : "";
                        boolean isErrorLine = lowerText.contains("error:") || lowerText.contains("failed:") || color == Color.RED;

                        boolean hasFailed = analyzer.analyzeLine(text, color, new BuildSummaryAnalyzer.LogOutputListener() {
                            @Override
                            public void onAppendLog(String logText, int logColor) {
                                appendLog(logText, logColor); 
                            }
                        });

                        if (hasFailed) {
                            isPipelineStopped[0] = true;
                            showToast("💥 บิวด์ล้มเหลว! (Exit Code 1)");
                            return;
                        }

                        if (text != null && (text.startsWith("📍") || text.startsWith("💬"))) {
                            return;
                        }

                        if (color == Color.GREEN || lowerText.contains("success")) {
                            appendLog(text, TerminalColor.SUGGEST_GREEN); 
                        } else if (color == Color.YELLOW) {
                            appendLog(text, TerminalColor.TARGET_YELLOW); 
                        } else if (color == Color.CYAN) {
                            appendLog(text, TerminalColor.LOG_CYAN); 
                        } else if (isErrorLine) {
                            appendLog(text, TerminalColor.DETAIL_RED); 
                        } else {
                            appendLog(text, TerminalColor.TEXT_WHITE); 
                        }
                    }

                    @Override 
                    public void onBuildStarted() { 
                        showToast("กำลังเริ่มระบบ Cloud Workflow... 🐙"); 
                        appendLog("\n##[group]🚀 เรียกทำงานคำสั่ง: compileJava", TerminalColor.LOG_GRAY);
                        appendLog("🔄 กำลังเชื่อมต่อไปยังเซิร์ฟเวอร์คอมไพล์บนคลาวด์...", TerminalColor.LOG_WHITE);
                    }

                    @Override
                    public void onBuildFinished(boolean success, String apkPath) {
                        if (isPipelineStopped[0]) return;

                        appendLog("##[endgroup]", TerminalColor.LOG_GRAY);

                        if (success) {
                            showToast("บิวด์แอปสำเร็จ! 🎉");
                            appendLog("\n##[group]🎉 งานหลังบิวด์: จัดเก็บไฟล์ระบบแอปพลิเคชัน", TerminalColor.SUGGEST_GREEN);
                            appendLog("✅ สำเร็จ: กระบวนการทำงานทั้งหมดเสร็จสิ้นโดยไม่มีข้อผิดพลาด", TerminalColor.SUGGEST_GREEN);
                            appendLog("📦 ไฟล์แอปที่ได้ (APK): " + (apkPath != null ? apkPath : "outputs/apk/debug/app-debug.apk"), TerminalColor.LOG_CYAN);
                            appendLog("##[endgroup]", TerminalColor.SUGGEST_GREEN);
                            
                            runOnUiThread(() -> { if (rvErrorPanel != null) rvErrorPanel.setVisibility(View.GONE); });
                        } else {
                            showToast("กระบวนการทำงานล้มเหลว");
                            appendLog("\n##[error] การทำงานหยุดช้าลงเนื่องจากการปิดตัวของระบบบิวด์อย่างกะทันหัน", TerminalColor.ERROR_RED);
                            
                            if (analyzer != null) {
                                analyzer.printSummary(new BuildSummaryAnalyzer.LogOutputListener() {
                                    @Override
                                    public void onAppendLog(String text, int color) {
                                        if (dialogPanelAdapter != null) tvConsole = dialogPanelAdapter.getTvConsole();
                                        appendColoredText(tvConsole, text, color);
                                    }
                                });
                            }
                            
                            final ParsedError err = analyzer.getLastError();
                            if (err != null) {
                                runOnUiThread(() -> {
                                    executeJumpToError(err);
                                });
                            }
                        }
                    }
                }
            );

            String githubToken = savedToken; 
            String projectName = currentProject.getProjectName();
            String repoUrl = "https://github.com/" + username + "/" + projectName + ".git";
            String packageName = "com.dev.ministudio"; 

            buildTask.startCloudBuild(githubToken, repoUrl, projectName, packageName); 
            buildTask.setAnalyzer(analyzer);
        }, 300);
    }

    private void executeJumpToError(final ParsedError errorItem) {
        if (errorItem == null || currentProject == null) return;

        try {
            java.io.File targetFile = new java.io.File(errorItem.file);
            if (!targetFile.isAbsolute()) {
                targetFile = new java.io.File(currentProject.getRootPath(), errorItem.file);
            }

            if (targetFile.exists()) {
                openFile(targetFile); 
                
                if (codeEditor != null) {
                    final int zeroBasedLine = Math.max(0, errorItem.line - 1); 
                    final int targetColumn = Math.max(0, errorItem.column);

                    codeEditor.postDelayed(() -> {
                        try {
                            if (codeEditor.getSearcher() != null) {
                                codeEditor.getSearcher().stopSearch();
                            }
                            codeEditor.jumpToLine(zeroBasedLine);            
                            codeEditor.setSelection(zeroBasedLine, targetColumn);
                            codeEditor.setSelectionRegion(zeroBasedLine, targetColumn, zeroBasedLine, targetColumn + 4);
                            
                            if (rvErrorPanel != null) {
                                rvErrorPanel.setVisibility(View.VISIBLE);
                            }
                            showToast("🚨 วาร์ปล็อกเป้าหมายพังในบรรทัดที่ " + errorItem.line + " สำเร็จครับ!");
                        } catch (Exception layoutEx) {
                            layoutEx.printStackTrace();
                        }
                    }, 200); 
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void openFilePicker() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
        intent.setType("*/*"); 
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        startActivityForResult(android.content.Intent.createChooser(intent, "เลือกไฟล์ที่จะนำเข้า"), PICK_FILE_REQUEST_CODE);
    }

public void openFile(File file) {
    if (file == null || !file.exists() || !file.isFile()) return;

    // 1. อ่านเนื้อหาไฟล์
    final String content = readFileContent(file);

    // 2. อัปเดตโมเดล / แท็บ
    if (currentProject != null) {
        currentProject.setCurrentOpenFile(file);
        currentProject.addFileToTabs(file); // ถ้ามีเมธอดนี้ ถ้าไม่มีให้จัดการใน ProjectTreeManager
    }

    if (projectTreeManager != null) {
        try {
            projectTreeManager.openFile(file);
        } catch (Exception ignored) {
        }
    }

    // 3. อัปเดต UI บน main thread
    runOnUiThread(() -> {
        if (codeEditor != null) {
            codeEditor.setText(content != null ? content : "");
        }

        updateFilePathStatus(file);

        setEditorActiveState(true); // ซ่อน emptyState + โชว์ editor

        if (tabAdapter != null) {
            tabAdapter.notifyDataSetChanged();
        }

        if (tvSaveStatus != null) {
            tvSaveStatus.setText("Saved");
            tvSaveStatus.setTextColor(android.graphics.Color.parseColor("#9ECE6A"));
        }
    });
}

private String readFileContent(File file) {
    StringBuilder sb = new StringBuilder();
    try (BufferedReader br = new BufferedReader(
            new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
        String line;
        while ((line = br.readLine()) != null) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(line);
        }
    } catch (Exception e) {
        runOnUiThread(() -> showToast("อ่านไฟล์ไม่สำเร็จ: " + e.getMessage()));
        return "";
    }
    return sb.toString();
}
    public void saveFile() {
        if (projectTreeManager != null) {
            projectTreeManager.saveFile();
        }
    }

    private void appendLog(final String text, final int color) {
        runOnUiThread(() -> {
            if (dialogPanelAdapter != null) {
                tvConsole = dialogPanelAdapter.getTvConsole();
            }
            if (tvConsole != null) {
                appendColoredText(tvConsole, text + "\n", color);
            }
        });
    }

private void setupShortcutBar() {
    LinearLayout shortcutBar = findViewById(R.id.shortcutBar);
    LinearLayout aiShortcutBar = findViewById(R.id.aiShortcutBar);
    if (shortcutBar == null) return;

    shortcutBar.removeAllViews();
    if (aiShortcutBar != null) {
        aiShortcutBar.removeAllViews();
        aiShortcutBar.setVisibility(View.GONE); // ซ่อน AI ด้านบน
    }

    float density = getResources().getDisplayMetrics().density;
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, (int) (36 * density)
    );
    params.setMargins((int) (3 * density), (int) (2 * density),
            (int) (3 * density), (int) (2 * density));

    // เหลือแค่สัญลักษณ์ (ไม่มี Undo/Redo และ AI แล้ว)
    String[] shortcuts = {
    // วงเล็บ
    "{", "}", "[", "]", "(", ")", "<", ">",
    // ตัวดำเนินการ
    "=", "+", "-", "*", "/", "%",
    // เครื่องหมายคำพูด
    "\"", "'", "`",
    // อื่น ๆ
    ".", ",", ":", ";", "!", "?",
    "&", "|", "_", "#", "@", "$"
};
    for (String symbol : shortcuts) {
        shortcutBar.addView(createButton(symbol, params, v -> {
            if (codeEditor != null && codeEditor.getCursor() != null) {
                codeEditor.getText().insert(
                        codeEditor.getCursor().getLeftLine(),
                        codeEditor.getCursor().getLeftColumn(),
                        symbol
                );
            }
        }, "#A9B1D6", "#24283B"));
    }
}

private TextView createButton(String text, LinearLayout.LayoutParams ignored,
                              View.OnClickListener listener, String textColor, String bgColor) {
    float density = getResources().getDisplayMetrics().density;
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, (int) (36 * density));
    params.setMargins((int) (3 * density), (int) (2 * density),
            (int) (3 * density), (int) (2 * density));

    TextView btn = new TextView(this);
    btn.setText(text);
    btn.setTextSize(14);
    btn.setGravity(Gravity.CENTER);
    btn.setPadding((int) (10 * density), 0, (int) (10 * density), 0);
    btn.setTextColor(Color.parseColor(textColor));
    btn.setLayoutParams(params);

    GradientDrawable shape = new GradientDrawable();
    shape.setCornerRadius(6 * density);
    shape.setColor(Color.parseColor(bgColor));
    btn.setBackground(shape);
    btn.setOnClickListener(listener);
    return btn;
}
/** เรียกจากปุ่ม palette → แทรกสีใหม่ */
private void showFullColorPickerDialog() {
    showFullColorPickerDialog(null, -1, -1, -1, -1);
}

/**
 * @param initialHex สีเริ่มต้น เช่น #FF008577 (null = Insert ใหม่)
 * @param replaceStartLine ถ้าระบุ >= 0 จะแทนที่ช่วงนี้แทนการ insert
 */
private void showFullColorPickerDialog(String initialHex,
                                       int replaceStartLine, int replaceStartCol,
                                       int replaceEndLine, int replaceEndCol) {
    android.app.Dialog dialog = new android.app.Dialog(this);
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

    float density = getResources().getDisplayMetrics().density;
    int wheelSize = (int) (220 * density);

    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding((int) (24 * density), (int) (24 * density),
            (int) (24 * density), (int) (16 * density));
    root.setBackground(createRoundedBg("#1F2335", 20));

    // Title
    TextView title = new TextView(this);
    title.setText(initialHex != null ? "Edit Color" : "Insert Color");
    title.setTextColor(Color.parseColor("#C0CAF5"));
    title.setTextSize(18);
    title.setTypeface(null, Typeface.BOLD);
    root.addView(title);

    // ===== วงล้อสี =====
    final ColorWheelView colorWheel = new ColorWheelView(this);
    LinearLayout.LayoutParams wheelParams = new LinearLayout.LayoutParams(wheelSize, wheelSize);
    wheelParams.gravity = Gravity.CENTER_HORIZONTAL;
    wheelParams.topMargin = (int) (12 * density);
    colorWheel.setLayoutParams(wheelParams);
    root.addView(colorWheel);

    // ===== สีตัวอย่าง + ช่อง Hex (แก้ไขได้) + คัดลอก =====
    LinearLayout infoRow = new LinearLayout(this);
    infoRow.setOrientation(LinearLayout.HORIZONTAL);
    infoRow.setGravity(Gravity.CENTER_VERTICAL);
    LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
    infoParams.topMargin = (int) (12 * density);
    infoRow.setLayoutParams(infoParams);

    final View colorDot = new View(this);
    LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
            (int) (36 * density), (int) (36 * density));
    colorDot.setLayoutParams(dotParams);
    colorDot.setBackground(createCircleBg("#00FF00"));
    infoRow.addView(colorDot);

    final android.widget.EditText etHex = new android.widget.EditText(this);
    etHex.setText(initialHex != null ? initialHex : "#FF00FF00");
    etHex.setTextColor(Color.parseColor("#A9B1D6"));
    etHex.setTextSize(14);
    etHex.setSingleLine(true);
    etHex.setHint("#AARRGGBB");
    etHex.setHintTextColor(Color.parseColor("#565F89"));
    etHex.setBackgroundColor(Color.parseColor("#24283B"));
    etHex.setPadding((int) (10 * density), (int) (8 * density),
            (int) (10 * density), (int) (8 * density));
    LinearLayout.LayoutParams etParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    etParams.setMarginStart((int) (10 * density));
    etHex.setLayoutParams(etParams);
    etHex.setInputType(android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    infoRow.addView(etHex);

    TextView btnCopy = new TextView(this);
    btnCopy.setText("คัดลอก");
    btnCopy.setTextColor(Color.parseColor("#7AA2F7"));
    btnCopy.setTextSize(13);
    btnCopy.setPadding((int) (10 * density), (int) (8 * density),
            (int) (4 * density), (int) (8 * density));
    btnCopy.setOnClickListener(v -> {
        String hex = etHex.getText().toString().trim();
        android.content.ClipboardManager cm =
                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(android.content.ClipData.newPlainText("color", hex));
            showToast("คัดลอก " + hex + " แล้ว");
        }
    });
    infoRow.addView(btnCopy);
    root.addView(infoRow);

    // ===== ตัวแปรสี =====
    final float[] currentHue = {120f};
    final float[] currentValue = {1f};
    final int[] currentAlpha = {255};
    final boolean[] updatingFromUi = {false};

    if (initialHex != null) {
        int c = parseHexColor(initialHex);
        if (c != 0) {
            float[] hsv = new float[3];
            Color.colorToHSV(c, hsv);
            currentHue[0] = hsv[0];
            currentValue[0] = hsv[2];
            currentAlpha[0] = (initialHex.length() >= 9) ? Color.alpha(c) : 255;
            try {
                colorWheel.getClass().getMethod("setHue", float.class)
                        .invoke(colorWheel, currentHue[0]);
            } catch (Exception ignored) {
            }
        }
    }

    final Runnable updateColor = () -> {
        updatingFromUi[0] = true;
        int rgb = Color.HSVToColor(new float[]{currentHue[0], 1f, currentValue[0]});
        int colorWithAlpha = (currentAlpha[0] << 24) | (rgb & 0x00FFFFFF);
        String hex = String.format("#%08X", colorWithAlpha);
        etHex.setText(hex);
        colorDot.setBackground(createCircleBgWithAlpha(colorWithAlpha));
        updatingFromUi[0] = false;
    };

    colorWheel.setOnColorChangeListener(color -> {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        currentHue[0] = hsv[0];
        updateColor.run();
    });

    // พิมพ์ / วางในช่อง Hex
    etHex.addTextChangedListener(new android.text.TextWatcher() {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(android.text.Editable s) {
            if (updatingFromUi[0]) return;
            String raw = s.toString().trim();
            if (raw.isEmpty()) return;
            if (!raw.startsWith("#")) raw = "#" + raw;

            int color = parseHexColor(raw);
            if (color == 0) return;

            if (raw.length() >= 9) {
                currentAlpha[0] = (color >> 24) & 0xFF;
            } else {
                currentAlpha[0] = 255;
            }

            float[] hsv = new float[3];
            Color.colorToHSV(color | 0xFF000000, hsv);
            currentHue[0] = hsv[0];
            currentValue[0] = hsv[2];

            int withAlpha = (currentAlpha[0] << 24) | (color & 0x00FFFFFF);
            colorDot.setBackground(createCircleBgWithAlpha(withAlpha));
        }
    });

    // ===== ความสว่าง =====
    TextView tvBrightLabel = new TextView(this);
    tvBrightLabel.setText("ความสว่าง");
    tvBrightLabel.setTextColor(Color.parseColor("#565F89"));
    tvBrightLabel.setTextSize(12);
    tvBrightLabel.setPadding(0, (int) (10 * density), 0, (int) (2 * density));
    root.addView(tvBrightLabel);

    final TextView tvBrightPercent = new TextView(this);
    int brightPct = Math.round(currentValue[0] * 100);
    tvBrightPercent.setText(brightPct + "%");
    tvBrightPercent.setTextColor(Color.parseColor("#A9B1D6"));
    tvBrightPercent.setTextSize(12);
    root.addView(tvBrightPercent);

    android.widget.SeekBar brightSeek = new android.widget.SeekBar(this);
    brightSeek.setMax(100);
    brightSeek.setProgress(brightPct);
    root.addView(brightSeek);

    brightSeek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
            currentValue[0] = progress / 100f;
            tvBrightPercent.setText(progress + "%");
            updateColor.run();
        }
        @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
    });

    // ===== ความโปร่งใส =====
    TextView tvAlphaLabel = new TextView(this);
    tvAlphaLabel.setText("ความโปร่งใส");
    tvAlphaLabel.setTextColor(Color.parseColor("#565F89"));
    tvAlphaLabel.setTextSize(12);
    tvAlphaLabel.setPadding(0, (int) (10 * density), 0, (int) (2 * density));
    root.addView(tvAlphaLabel);

    final TextView tvAlphaPercent = new TextView(this);
    int alphaPct = Math.round(currentAlpha[0] / 255f * 100);
    tvAlphaPercent.setText(alphaPct + "%");
    tvAlphaPercent.setTextColor(Color.parseColor("#A9B1D6"));
    tvAlphaPercent.setTextSize(12);
    root.addView(tvAlphaPercent);

    android.widget.SeekBar alphaSeek = new android.widget.SeekBar(this);
    alphaSeek.setMax(100);
    alphaSeek.setProgress(alphaPct);
    root.addView(alphaSeek);

    alphaSeek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
            currentAlpha[0] = (int) (progress / 100f * 255);
            tvAlphaPercent.setText(progress + "%");
            updateColor.run();
        }
        @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
    });

    updateColor.run();

    // ===== Cancel / Apply =====
    LinearLayout btnRow = new LinearLayout(this);
    btnRow.setOrientation(LinearLayout.HORIZONTAL);
    btnRow.setGravity(Gravity.END);
    LinearLayout.LayoutParams btnRowParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
    btnRowParams.topMargin = (int) (16 * density);
    btnRow.setLayoutParams(btnRowParams);

    TextView btnCancel = new TextView(this);
    btnCancel.setText("Cancel");
    btnCancel.setTextColor(Color.parseColor("#7AA2F7"));
    btnCancel.setTextSize(15);
    btnCancel.setPadding((int) (20 * density), (int) (12 * density),
            (int) (20 * density), (int) (12 * density));
    btnCancel.setOnClickListener(v -> dialog.dismiss());
    btnRow.addView(btnCancel);

    TextView btnApply = new TextView(this);
    btnApply.setText("Apply");
    btnApply.setTextColor(Color.parseColor("#7AA2F7"));
    btnApply.setTextSize(15);
    btnApply.setTypeface(null, Typeface.BOLD);
    btnApply.setPadding((int) (20 * density), (int) (12 * density),
            (int) (20 * density), (int) (12 * density));
    btnApply.setOnClickListener(v -> {
        String hex = etHex.getText().toString().trim();
        if (!hex.startsWith("#")) hex = "#" + hex;
        hex = hex.toUpperCase();

        if (parseHexColor(hex) == 0) {
            showToast("รหัสสีไม่ถูกต้อง");
            return;
        }

        if (codeEditor != null && codeEditor.getCursor() != null) {
            if (replaceStartLine >= 0) {
                codeEditor.getText().delete(
                        replaceStartLine, replaceStartCol,
                        replaceEndLine, replaceEndCol
                );
                codeEditor.getText().insert(replaceStartLine, replaceStartCol, hex);
            } else {
                codeEditor.getText().insert(
                        codeEditor.getCursor().getLeftLine(),
                        codeEditor.getCursor().getLeftColumn(),
                        hex
                );
            }
        }
        dialog.dismiss();
    });
    btnRow.addView(btnApply);
    root.addView(btnRow);

    dialog.setContentView(root);
    if (dialog.getWindow() != null) {
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().setLayout(
                (int) (320 * density),
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }
    dialog.show();
}
// Helper สำหรับสีที่มี alpha
private android.graphics.drawable.GradientDrawable createCircleBgWithAlpha(int color) {
    android.graphics.drawable.GradientDrawable gd =
            new android.graphics.drawable.GradientDrawable();
    gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
    gd.setColor(color);
    return gd;
}

private android.graphics.drawable.GradientDrawable createRoundedBg(String color, int radiusDp) {
    android.graphics.drawable.GradientDrawable gd =
            new android.graphics.drawable.GradientDrawable();
    gd.setColor(Color.parseColor(color));
    gd.setCornerRadius(radiusDp * getResources().getDisplayMetrics().density);
    return gd;
}

private android.graphics.drawable.GradientDrawable createCircleBg(String color) {
    android.graphics.drawable.GradientDrawable gd =
            new android.graphics.drawable.GradientDrawable();
    gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
    gd.setColor(Color.parseColor(color));
    return gd;
}

// จัดการ Logic ของ AI
private void handleAiAction(boolean isOptimize) {
    if (codeEditor == null || currentProject == null) return;
    java.io.File currentFile = currentProject.getCurrentOpenFile();
    if (isOptimize && currentFile == null) {
        showToast("⚠️ กรุณาเปิดไฟล์ที่ต้องการปรับปรุงก่อนครับ");
        return;
    }

    if (aiLayoutAnalyzer != null) aiLayoutAnalyzer.stopSpeaking();
    openAiChat();

    String fileName = (currentFile != null) ? currentFile.getName() : "UnknownFile.java";
    String code = codeEditor.getText().toString();
    String prompt = isOptimize ? CodeOptimizerManager.createOptimizePrompt(fileName, code) : null;

    updateAiOutput("🤖 *" + (isOptimize ? "กำลังสแกนวิเคราะห์เพื่อปรับปรุงโค้ด..." : "กำลังวิเคราะห์โค้ด...") + "*");

    AiLayoutAnalyzer.OnAnalysisListener listener = new AiLayoutAnalyzer.OnAnalysisListener() {
        @Override
        public void onStart() {} // ส่วนแสดงผลถูกเรียกจากบรรทัด updateAiOutput ด้านบนแล้ว
        @Override
        public void onSuccess(android.text.SpannableString result) {
            chatHistory += "\n\n🤖 **" + (isOptimize ? "ผลลัพธ์การปรับปรุง:" : "ผลวิเคราะห์:") + "**\n" + result.toString();
            updateAiOutput(chatHistory);
        }
        @Override
        public void onError(String error) {
            chatHistory += "\n\n❌ **Error:** " + error;
            updateAiOutput(chatHistory);
        }
    };

    if (isOptimize) aiLayoutAnalyzer.askAi(prompt, listener);
    else aiLayoutAnalyzer.analyzeCode(fileName, code, listener);
}


// ฟังก์ชันอัปเดตหน้าจอ WebView ที่ใช้ซ้ำได้
private void updateAiOutput(String markdownText) {
    runOnUiThread(() -> {
        android.webkit.WebView web = dialogPanelAdapter.getWebAiOutput();
        if (web != null) {
            web.getSettings().setJavaScriptEnabled(true);
            web.addJavascriptInterface(new WebAppInterface(this), "AndroidBridge");
            web.loadDataWithBaseURL(null, AiHtmlFormatter.convertMarkdownToHtml(markdownText), "text/html", "utf-8", null);
        }
    });
}

    

@Override
public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_toolbar, menu);

    MenuItem buildItem = menu.findItem(R.id.action_build);
    if (buildItem != null && buildItem.getIcon() != null) {
        int size = (int) (24 * getResources().getDisplayMetrics().density);
        buildItem.getIcon().setBounds(0, 0, size, size);
    }
    return true;
}
    
@Override
public boolean onOptionsItemSelected(MenuItem item) {
    int id = item.getItemId();

    if (id == R.id.action_build) {
        showBuildModeDialog();  // ต้องเป็นอันนี้เท่านั้น
        return true;
    }

    if (id == R.id.action_search) {
        if (editorSearchManager != null) {
            editorSearchManager.toggle();
        } else if (searchBar != null) {
            searchBar.setVisibility(
                    searchBar.getVisibility() == View.GONE ? View.VISIBLE : View.GONE);
        }
        return true;
    }

    return super.onOptionsItemSelected(item);
}
private void toggleEditorTheme() {
    if (codeEditor == null) return;

    isLightEditorTheme = !isLightEditorTheme;
    if (isLightEditorTheme) {
        codeEditor.setColorScheme(new com.dev.ministudio.editor.NexusLightColorScheme());
        showToast("☀️ ธีมสว่าง");
    } else {
        codeEditor.setColorScheme(new com.dev.ministudio.editor.NexusColorScheme());
        showToast("🌙 ธีมมืด");
    }
    getSharedPreferences("AppSettings", MODE_PRIVATE)
            .edit()
            .putBoolean("editor_light_theme", isLightEditorTheme)
            .apply();
}

private void showFileSearchDialog() {
    if (currentProject == null) {
        showToast("ยังไม่ได้เปิดโปรเจกต์");
        return;
    }

    android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar);
    dialog.setContentView(R.layout.dialog_file_search);

    if (dialog.getWindow() != null) {
        android.view.Window window = dialog.getWindow();
        window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, true);
        window.setStatusBarColor(android.graphics.Color.parseColor("#1F2335"));
        window.setNavigationBarColor(android.graphics.Color.parseColor("#1A1B26"));
    }

    View searchBarRoot = dialog.findViewById(R.id.searchBarRoot);
    if (searchBarRoot != null) {
        int statusBarHeight = 0;
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resId > 0) {
            statusBarHeight = getResources().getDimensionPixelSize(resId);
        }
        searchBarRoot.setPadding(
                searchBarRoot.getPaddingLeft(),
                statusBarHeight,
                searchBarRoot.getPaddingRight(),
                searchBarRoot.getPaddingBottom()
        );
    }

    android.widget.EditText etSearch = dialog.findViewById(R.id.etFileSearch);
    android.widget.TextView tvHint = dialog.findViewById(R.id.tvSearchHint);
    android.widget.ListView lvResults = dialog.findViewById(R.id.lvFileSearchResults);
    android.widget.ImageButton btnBack = dialog.findViewById(R.id.btnSearchBack);
    android.widget.ImageButton btnClear = dialog.findViewById(R.id.btnSearchClear);

    // --- เพิ่มส่วนประกอบโหมดค้นหา ---
    final boolean[] contentMode = { false }; // false = ชื่อไฟล์, true = ในโค้ด
    final java.util.List<com.dev.ministudio.fs.FileSystemManager.ContentMatch> contentHits = new java.util.ArrayList<>();
    
    android.widget.TextView tabName = dialog.findViewById(R.id.tabSearchName);
    android.widget.TextView tabContent = dialog.findViewById(R.id.tabSearchContent);

    Runnable updateTabs = () -> {
        if (contentMode[0]) {
            if (tabContent != null) {
                tabContent.setTextColor(0xFFC0CAF5);
                tabContent.setBackgroundColor(0xFF292E42);
            }
            if (tabName != null) {
                tabName.setTextColor(0xFF565F89);
                tabName.setBackgroundColor(0x00000000);
            }
            etSearch.setHint("ค้นหาโค้ดทั้งโปรเจกต์...");
        } else {
            if (tabName != null) {
                tabName.setTextColor(0xFFC0CAF5);
                tabName.setBackgroundColor(0xFF292E42);
            }
            if (tabContent != null) {
                tabContent.setTextColor(0xFF565F89);
                tabContent.setBackgroundColor(0x00000000);
            }
            etSearch.setHint("ค้นหาไฟล์...");
        }
    };

    updateTabs.run(); // ตั้งค่าสถานะเริ่มต้นของ UI Tab

    btnBack.setOnClickListener(v -> dialog.dismiss());
    btnClear.setOnClickListener(v -> etSearch.setText(""));

    java.util.List<java.io.File> resultFiles = new java.util.ArrayList<>();
    java.io.File root = new java.io.File(currentProject.getRootPath());
    final String rootPath = root.getAbsolutePath();

    final android.widget.BaseAdapter[] adapterRef = new android.widget.BaseAdapter[1];

    adapterRef[0] = new android.widget.BaseAdapter() {
        @Override
        public int getCount() {
            return contentMode[0] ? contentHits.size() : resultFiles.size();
        }

        @Override
        public Object getItem(int position) {
            return contentMode[0] ? contentHits.get(position) : resultFiles.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_file_search_result, parent, false);
            }

            android.widget.TextView tvName = convertView.findViewById(R.id.tvFileName);
            android.widget.TextView tvMeta = convertView.findViewById(R.id.tvFileMeta);
            android.widget.TextView tvPath = convertView.findViewById(R.id.tvFilePath);
            android.widget.ImageView imgIcon = convertView.findViewById(R.id.imgFileIcon);
            android.widget.ImageButton btnDelete = convertView.findViewById(R.id.btnDeleteFile);

            final java.io.File file;

            if (contentMode[0] && position < contentHits.size()) {
                // --- การแสดงผลโหมด ค้นหาในเนื้อหาโค้ด ---
                com.dev.ministudio.fs.FileSystemManager.ContentMatch m = contentHits.get(position);
                file = m.file;

                tvName.setText(file.getName() + "  :" + m.lineNumber);
                tvMeta.setText(m.lineText != null ? m.lineText.trim() : "");

                if (btnDelete != null) {
                    btnDelete.setVisibility(android.view.View.GONE); // ซ่อนปุ่มลบในโหมดค้นหาโค้ด
                }

                // กดรายการ -> เปิดไฟล์ + Jump ไปยังบรรทัด
                convertView.setOnClickListener(v -> {
                    dialog.dismiss();
                    openFile(m.file);
                    if (drawerLayout != null) drawerLayout.closeDrawers();
                    
                    if (codeEditor != null) {
                        int line = Math.max(0, m.lineNumber - 1);
                        codeEditor.post(() -> {
                            try {
                                codeEditor.setSelection(line, 0);
                                codeEditor.jumpToLine(line);
                            } catch (Exception ignored) {}
                        });
                    }
                });

            } else if (position < resultFiles.size()) {
                // --- การแสดงผลโหมด ค้นตามชื่อไฟล์ (แบบเดิม) ---
                file = resultFiles.get(position);

                String name = file.getName();
                String q = etSearch.getText().toString().trim();

                if (!q.isEmpty() && name.toLowerCase().contains(q.toLowerCase())) {
                    android.text.SpannableString span = new android.text.SpannableString(name);
                    int start = name.toLowerCase().indexOf(q.toLowerCase());
                    span.setSpan(
                            new android.text.style.BackgroundColorSpan(
                                    android.graphics.Color.parseColor("#E0AF68")),
                            start, start + q.length(),
                            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                    span.setSpan(
                            new android.text.style.ForegroundColorSpan(
                                    android.graphics.Color.parseColor("#1A1B26")),
                            start, start + q.length(),
                            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                    tvName.setText(span);
                } else {
                    tvName.setText(name);
                }

                long size = file.length();
                String sizeStr;
                if (size < 1024) sizeStr = size + " B";
                else if (size < 1024 * 1024) sizeStr = String.format("%.2f KB", size / 1024.0);
                else sizeStr = String.format("%.2f MB", size / (1024.0 * 1024.0));

                java.text.SimpleDateFormat sdf =
                        new java.text.SimpleDateFormat("dd/MM/yy HH:mm", java.util.Locale.getDefault());
                String dateStr = sdf.format(new java.util.Date(file.lastModified()));
                tvMeta.setText(sizeStr + "    " + dateStr);

                // ปุ่มลบไฟล์
                if (btnDelete != null) {
                    btnDelete.setVisibility(android.view.View.VISIBLE);
                    btnDelete.setFocusable(false);
                    btnDelete.setFocusableInTouchMode(false);
                    btnDelete.setOnClickListener(v ->
                            confirmAndDeleteFile(file, resultFiles, adapterRef[0], tvHint));
                }

                // กดรายการ -> เปิดไฟล์
                convertView.setOnClickListener(v -> {
                    dialog.dismiss();
                    openFile(file);
                    if (drawerLayout != null) drawerLayout.closeDrawers();
                });
            } else {
                return convertView;
            }

            // คำนวณ Path สำหรับแสดงผล (ใช้ร่วมกันทั้งสองโหมด)
            String path = file.getAbsolutePath();
            if (path.startsWith(rootPath)) {
                path = path.substring(rootPath.length());
                if (path.startsWith("/")) path = path.substring(1);
            }
            int lastSlash = path.lastIndexOf('/');
            if (lastSlash >= 0) path = path.substring(0, lastSlash + 1);
            else path = "/";
            tvPath.setText(path);

            // สีของ Icon ตามชนิดไฟล์ (ใช้ร่วมกันทั้งสองโหมด)
            String lower = file.getName().toLowerCase();
            if (lower.endsWith(".java")) {
                imgIcon.setColorFilter(android.graphics.Color.parseColor("#7DCFFF"));
            } else if (lower.endsWith(".xml")) {
                imgIcon.setColorFilter(android.graphics.Color.parseColor("#E0AF68"));
            } else if (lower.endsWith(".gradle") || lower.endsWith(".properties")) {
                imgIcon.setColorFilter(android.graphics.Color.parseColor("#9ECE6A"));
            } else {
                imgIcon.setColorFilter(android.graphics.Color.parseColor("#7AA2F7"));
            }

            return convertView;
        }
    };
    lvResults.setAdapter(adapterRef[0]);

    android.os.Handler searchHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    final Runnable[] searchTask = new Runnable[1];

    // ผูก Event การคลิก Tab สลับโหมด
    if (tabName != null) {
        tabName.setOnClickListener(v -> {
            if (contentMode[0]) {
                contentMode[0] = false;
                updateTabs.run();
                etSearch.setText(etSearch.getText()); // trigger ค้นหาใหม่
            }
        });
    }

    if (tabContent != null) {
        tabContent.setOnClickListener(v -> {
            if (!contentMode[0]) {
                contentMode[0] = true;
                updateTabs.run();
                etSearch.setText(etSearch.getText()); // trigger ค้นหาใหม่
            }
        });
    }

    etSearch.addTextChangedListener(new android.text.TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(android.text.Editable s) {
            String q = s.toString().trim();
            btnClear.setVisibility(q.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);

            if (searchTask[0] != null) searchHandler.removeCallbacks(searchTask[0]);
            searchTask[0] = () -> {
                if (q.isEmpty()) {
                    resultFiles.clear();
                    contentHits.clear();
                    adapterRef[0].notifyDataSetChanged();
                    tvHint.setText("ผลการค้นหา");
                    return;
                }
                tvHint.setText("กำลังค้นหา...");
                new Thread(() -> {
                    if (contentMode[0]) {
                        // --- ค้นหาในเนื้อหาโค้ด ---
                        java.util.List<com.dev.ministudio.fs.FileSystemManager.ContentMatch> found =
                                com.dev.ministudio.fs.FileSystemManager.searchContentInProject(root, q, 200);
                        runOnUiThread(() -> {
                            contentHits.clear();
                            contentHits.addAll(found);
                            resultFiles.clear();
                            for (com.dev.ministudio.fs.FileSystemManager.ContentMatch m : found) {
                                if (!resultFiles.contains(m.file)) resultFiles.add(m.file);
                            }
                            adapterRef[0].notifyDataSetChanged();
                            tvHint.setText(found.isEmpty()
                                    ? "ไม่พบโค้ด"
                                    : "พบโค้ด · " + found.size() + " จุด · " + resultFiles.size() + " ไฟล์");
                        });
                    } else {
                        // --- ค้นหาตามชื่อไฟล์ ---
                        java.util.List<java.io.File> found =
                                com.dev.ministudio.fs.FileSystemManager.searchFilesByName(root, q);
                        runOnUiThread(() -> {
                            contentHits.clear();
                            resultFiles.clear();
                            resultFiles.addAll(found);
                            adapterRef[0].notifyDataSetChanged();
                            tvHint.setText(found.isEmpty()
                                    ? "ไม่พบไฟล์ที่ตรงกับ \"" + q + "\""
                                    : "ผลการค้นหา · " + found.size() + " ไฟล์");
                        });
                    }
                }).start();
            };
            searchHandler.postDelayed(searchTask[0], 250);
        }
    });

    lvResults.setOnItemClickListener(null);

    dialog.show();
    etSearch.requestFocus();
    android.view.inputmethod.InputMethodManager imm =
            (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
    if (imm != null) {
        imm.showSoftInput(etSearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
    }
}

private void confirmAndDeleteFile(java.io.File file,
                                  java.util.List<java.io.File> resultFiles,
                                  android.widget.BaseAdapter adapter,
                                  android.widget.TextView tvHint) {
    if (file == null || !file.exists()) {
        showToast("ไม่พบไฟล์");
        return;
    }

    new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("ลบไฟล์")
            .setMessage("ต้องการลบ \"" + file.getName() + "\" หรือไม่?\n\nการลบไม่สามารถย้อนกลับได้")
            .setPositiveButton("ลบ", (d, w) -> {
                if (currentProject != null) {
                    java.io.File current = currentProject.getCurrentOpenFile();
                    if (current != null && current.getAbsolutePath().equals(file.getAbsolutePath())) {
                        currentProject.removeFileFromTabs(file);
                        currentProject.setCurrentOpenFile(null);
                        if (codeEditor != null) codeEditor.setText("");
                        setEditorActiveState(false);
                        if (tvFilePath != null) tvFilePath.setText("No file open");
                        if (tabAdapter != null) tabAdapter.notifyDataSetChanged();
                    } else {
                        currentProject.removeFileFromTabs(file);
                        if (tabAdapter != null) tabAdapter.notifyDataSetChanged();
                    }
                }

                boolean ok = deleteRecursive(file);
                if (ok) {
                    resultFiles.remove(file);
                    adapter.notifyDataSetChanged();
                    if (tvHint != null) {
                        tvHint.setText(resultFiles.isEmpty()
                                ? "ไม่พบไฟล์"
                                : "ผลการค้นหา · " + resultFiles.size() + " ไฟล์");
                    }
                    if (projectTreeManager != null) {
                        projectTreeManager.refreshFileTree();
                    }
                    showToast("ลบ \"" + file.getName() + "\" แล้ว");
                } else {
                    showToast("ลบไม่สำเร็จ");
                }
            })
            .setNegativeButton("ยกเลิก", null)
            .show();
}

private boolean deleteRecursive(java.io.File fileOrDir) {
    if (fileOrDir == null || !fileOrDir.exists()) return false;
    if (fileOrDir.isDirectory()) {
        java.io.File[] children = fileOrDir.listFiles();
        if (children != null) {
            for (java.io.File child : children) {
                if (!deleteRecursive(child)) return false;
            }
        }
    }
    return fileOrDir.delete();
}
    private void triggerTreeRefresh(FileNode parentNode) { 
        if (projectTreeManager != null) projectTreeManager.refreshFileTree(); 
    }

 private void setupTabLogic() {
    tabAdapter = new TabAdapter(currentProject, new TabAdapter.OnTabInterface() {
        @Override
        public void onTabClick(File file) {
            openFile(file);
        }

        @Override
        public void onTabClose(File file, int position) {
            if (currentProject == null || file == null) return;

            // 1. เอาออกจากรายการแท็บ
            currentProject.removeFileFromTabs(file);

            // 2. ถ้าปิดไฟล์ที่กำลังเปิดอยู่ ต้องสลับไปไฟล์อื่น หรือว่าง
            File current = currentProject.getCurrentOpenFile();
            if (current != null && current.equals(file)) {
                java.util.List<File> opened = currentProject.getOpenedFiles();
                if (opened != null && !opened.isEmpty()) {
                    // เปิดแท็บข้างเคียง
                    int newIndex = Math.min(position, opened.size() - 1);
                    if (newIndex < 0) newIndex = 0;
                    openFile(opened.get(newIndex));
                } else {
                    // ไม่เหลือแท็บแล้ว
                    currentProject.setCurrentOpenFile(null);
                    if (codeEditor != null) codeEditor.setText("");
                    setEditorActiveState(false);
                    if (tvFilePath != null) tvFilePath.setText("No file open");
                    if (tvSaveStatus != null) tvSaveStatus.setText("");
                }
            }

            // 3. รีเฟรชแถบแท็บ
            if (tabAdapter != null) {
                tabAdapter.notifyDataSetChanged();
            }
        }
    });
    tabRecyclerView.setAdapter(tabAdapter);
}

    public void updateFilePathStatus(File file) {
    if (tvFilePath != null && file != null) {
        // ดึง Path เต็มๆ มาแสดงผล
        String fullPath = file.getAbsolutePath();
        
        // ถ้าต้องการตัดส่วนของ SDCARD หรือ Root ออกเพื่อความสวยงาม
        // สมมติว่าอยู่ใน /sdcard/MiniStudio/
        String displayPath = fullPath.replace("/sdcard/", ""); 
        
        tvFilePath.setText(displayPath);
        tvFilePath.setSelected(true); // เพิ่มให้ข้อความเลื่อนได้ถ้ามันยาวเกินหน้าจอ
    }
}

    public void showToast(final String message) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
    }
    
    private void appendColoredText(TextView tv, String text, int color) {
        if (tv == null) return;
        android.text.SpannableString spannable = new android.text.SpannableString(text);
        spannable.setSpan(new android.text.style.ForegroundColorSpan(color), 0, text.length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        tv.append(spannable);
        autoScrollTabContainer(tv);
    }

    private void autoScrollTabContainer(View innerTextView) {
        if (innerTextView == null) return;
        innerTextView.post(() -> {
            try {
                android.view.ViewParent currentParent = innerTextView.getParent();
                while (currentParent != null) {
                    if (currentParent instanceof ScrollView) {
                        ((ScrollView) currentParent).fullScroll(android.view.View.FOCUS_DOWN);
                        break;
                    }
                    currentParent = currentParent.getParent();
                }
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    // 🌟 Getters สำหรับเรียกจากภายนอก
    public ProjectModel getCurrentProject() { return currentProject; }
    public ProjectDialogManager getDialogManager() { return dialogManager; }
    public DrawerLayout getDrawerLayout() { return drawerLayout; }
    public CodeEditor getCodeEditor() { return codeEditor; }
    public TabAdapter getTabAdapter() { return tabAdapter; }
    public Handler getAutoSaveHandler() { return autoSaveHandler; }
    public Runnable getSaveRunnable() { return saveRunnable; }
    public PanelPagerAdapter getDialogPanelAdapter() { return dialogPanelAdapter; }
        
    @Override
protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == 2026 && projectTreeManager != null) {
        projectTreeManager.onActivityResult(requestCode, resultCode, data);
    }

    if (requestCode == REQUEST_AI_CHAT && resultCode == RESULT_OK && data != null) {
        String code = data.getStringExtra("ai_insert_code");
        if (code != null && !code.isEmpty() && codeEditor != null) {
            try {
                if (codeEditor.getCursor() != null) {
                    int line = codeEditor.getCursor().getLeftLine();
                    int col = codeEditor.getCursor().getLeftColumn();
                    codeEditor.getText().insert(line, col, code);
                } else {
                    codeEditor.setText(code);
                }
            } catch (Exception e) {
                codeEditor.setText(code);
            }
            setEditorActiveState(true);
            showToast("✨ ใส่โค้ดจาก AI แล้ว");
        }
    }
}
    // 🤖 สะพานเชื่อมแบบรวมศูนย์ตัวจริงตัวเดียว (ปรับปรุงให้รองรับ JavaScript เรียกใช้งานได้ชัวร์)
    public class WebAppInterface {
        Context mContext;

        public WebAppInterface(Context c) {
            this.mContext = c;
        }

        // ปุ่ม 1: คัดลอกข้อความซอร์สโค้ดธรรมดาลงคลิปบอร์ด Android
        @android.webkit.JavascriptInterface
        public void copyToSystemClipboard(final String text) {
            runOnUiThread(() -> {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("MiniStudioCode", text);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    showToast("📋 คัดลอกโค้ดลงคลิปบอร์ดแล้วครับน้า!");
                }
            });
        }

        // ปุ่ม 2: วางโค้ดพุ่งเข้าหา Sora CodeEditor โดยตรง
        @android.webkit.JavascriptInterface
        public void insertCodeIntoEditor(final String codeFromAi) {
            runOnUiThread(() -> {
                if (codeEditor != null) {
                    codeEditor.setText(codeFromAi);
                    
                    // ปิดเสียง AI ทันทีเมื่อกดยอมรับโค้ดไปใช้งาน
                    if (aiLayoutAnalyzer != null) {
                        aiLayoutAnalyzer.stopSpeaking();
                    }
                    if (fullPanelDialog != null && fullPanelDialog.isShowing()) {
                        fullPanelDialog.dismiss();
                    }
                    showToast("✨ นำโค้ดเข้าสู่หน้าแก้ไขเรียบร้อยแล้วครับน้า!");
                }
            });
        }
    }

    // คลาสเมนูต้นไม้
    public static class MenuOption {
        public String title;
        public int iconRes;
        public MenuOption(String title, int iconRes) {
            this.title = title;
            this.iconRes = iconRes;
        }
    }

    @Override
    protected void onDestroy() {
        if (aiLayoutAnalyzer != null) {
            aiLayoutAnalyzer.shutdown(); 
        }
        if (logcatReader != null) logcatReader.stop();
        super.onDestroy();
    }
// ฟังก์ชันกดปุ๊บ วาร์ปปั๊บ ไปยังตำแหน่งที่โค้ด Error (ฉบับปรับปรุงแก้อาการสัญลักษณ์หาย)
public void jumpToErrorLocation(String fileName, int lineNumber) {
    runOnUiThread(() -> {
        // 1. สั่งซ่อนแผงคอนโซลลงไปก่อนเพื่อคืนพื้นที่ให้หน้าจอแก้ไขโค้ด
        View consolePanel = findViewById(R.id.consolePanel);
        if (consolePanel != null) consolePanel.setVisibility(View.GONE);

        // 2. ลอจิกการสั่งเปิดไฟล์ .java ที่พังขึ้นกระดาน (อิงตามระบบเปิดไฟล์หลักของน้า)
        if (projectTreeManager != null && currentProject != null) {
            // เดินสายหาตำแหน่งไฟล์จริงในโปรเจกต์แล้วบังคับให้ระบบ Tab โหลดขึ้นมาทำงาน
            java.io.File fileToOpen = projectTreeManager.findFileInProject(currentProject.getRootPath(), fileName);
            if (fileToOpen != null && fileToOpen.exists()) {
                openFile(fileToOpen); // ใช้ฟังก์ชันเปิดไฟล์หลักของน้า
            }
        }

        // 3. ปรับโค้ดคำสั่งวาร์ปเคอร์เซอร์ให้ตรงกับ Sora Editor API ของเครื่องน้าครับ
        if (codeEditor != null) {
            int targetLine = Math.max(0, lineNumber - 1);
            // สั่งขยับตำแหน่งและเลื่อนหน้าจอฉบับตรงรุ่น
            codeEditor.getCursor().setLeft(targetLine, 0);
            codeEditor.getCursor().setRight(targetLine, 0);
            codeEditor.ensurePositionVisible(targetLine, 0);
            
            showToast("🔍 วาร์ปมาบรรทัดที่ " + lineNumber + " ให้แล้ว!");
        }
    });
}

private void pushChangesToGithub(String projectName) {
    if (projectName == null || projectName.isEmpty()) {
        showToast("⚠️ ไม่พบชื่อโปรเจกต์สำหรับทำการ Push");
        return;
    }

    this.pendingProjectName = projectName;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) 
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            showToast("⚠️ กรุณากดอนุญาตการแจ้งเตือน เพื่อให้เห็นแถบความคืบหน้านะครับ");
            return;
        }
    }

    startActualPushService(projectName);
}

@Override
public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == 101) {
        if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            showToast("✅ อนุญาตสิทธิ์แล้ว กำลังเริ่มอัปโหลด...");
            if (!pendingProjectName.isEmpty()) {
                startActualPushService(pendingProjectName);
            }
        } else {
            showToast("❌ คุณปฏิเสธสิทธิ์การแจ้งเตือน ทำให้ไม่สามารถแสดงความคืบหน้าได้");
        }
    }
}

private void startActualPushService(String projectName) {
    File projectDir = new File("/sdcard/MiniStudio/" + projectName);
    if (!projectDir.exists()) {
        showToast("❌ ไม่พบโฟลเดอร์โปรเจกต์");
        return;
    }

    SharedPreferences prefs = getSharedPreferences("GitHubPrefs", Context.MODE_PRIVATE);
    String username = prefs.getString("username", "");
    String token = prefs.getString("github_token", "");
    if (token.isEmpty()) token = prefs.getString("token", "");

    if (username.isEmpty() || token.isEmpty()) {
        showToast("❌ กรุณาตั้งค่า Username และ GitHub Token ก่อน");
        return;
    }

    String repoUrl = "https://github.com/" + username + "/" + projectName + ".git";

    Intent serviceIntent = new Intent(this, GitHubPushService.class);
    serviceIntent.putExtra("projectName", projectName);
    serviceIntent.putExtra("username", username);
    serviceIntent.putExtra("token", token);
    serviceIntent.putExtra("repoUrl", repoUrl);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        startForegroundService(serviceIntent);
    } else {
        startService(serviceIntent);
    }

    Toast.makeText(this, "📥 เริ่มอัปโหลดแล้ว! รูดหน้าจอลงมาดู % บน Status Bar ได้เลยครับ", Toast.LENGTH_LONG).show();
}
private void startLogcatMonitor(String packageName) {
    if (logcatReader == null) logcatReader = new LogcatReader();
    logcatReader.stop();

    if (fullPanelDialog == null || !fullPanelDialog.isShowing()) {
        showFullPanelDialog(0);
    } else if (dialogViewPager != null) {
        dialogViewPager.setCurrentItem(0, true);
    }

    appendConsoleLine("📡 Logcat โหมด Crash (เงียบถ้าไม่มี Error)\n",
            android.graphics.Color.parseColor("#7AA2F7"));
    if (packageName != null && !packageName.isEmpty()) {
        appendConsoleLine("กรอง: " + packageName + "\n",
                android.graphics.Color.parseColor("#565F89"));
    }

    logcatReader.start(packageName, new LogcatReader.Listener() {
        @Override
        public void onLine(String line) {
            int color;
            if (line.contains("FATAL") || line.contains("Fatal signal")) {
                color = android.graphics.Color.parseColor("#FF5555");
            } else if (line.contains("Caused by:") || line.contains("\tat ")) {
                color = android.graphics.Color.parseColor("#E0AF68");
            } else if (line.contains("AndroidRuntime")
                    || line.contains(" E/")
                    || line.contains(" F/")) {
                color = android.graphics.Color.parseColor("#F7768E");
            } else {
                color = android.graphics.Color.parseColor("#A9B1D6");
            }
            appendConsoleLine(line + "\n", color);
        }

        @Override
        public void onError(String message) {
            appendConsoleLine(message + "\n",
                    android.graphics.Color.parseColor("#F7768E"));
        }

        @Override
        public void onStopped() {
            appendConsoleLine("⏹ หยุด Logcat\n",
                    android.graphics.Color.parseColor("#565F89"));
            if (fullPanelDialog != null && fullPanelDialog.isShowing()) {
                TextView btn = fullPanelDialog.findViewById(R.id.btnLogcat);
                updateLogcatButtonUi(btn);
            }
        }
    });
}

private void appendConsoleLine(String text, int color) {
    runOnUiThread(() -> {
        if (tvConsole == null && dialogPanelAdapter != null) {
            tvConsole = dialogPanelAdapter.getTvConsole();
        }
        if (tvConsole != null) {
            appendColoredText(tvConsole, text, color);
        }
    });
}

private void showBuildModeDialog() {
    final String[] modes = {
            "☁️ Cloud (GitHub Actions)",
            "📱 Local (บนเครื่อง)"
    };

    new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("เลือกวิธี Build")
            .setItems(modes, (dialog, which) -> {
                if (which == 0) {
                    startCloudBuildPipeline(); // ของเดิม
                } else {
                    startLocalBuildPipeline(); // ใหม่
                }
            })
            .setNegativeButton("ยกเลิก", null)
            .show();
}

private void startLocalBuildPipeline() {
    // เปิด Console ให้เห็น log
    showFullPanelDialog(0);

    appendConsoleLine("📱 Local Build\n",
            android.graphics.Color.parseColor("#7AA2F7"));
    appendConsoleLine("สถานะ: โครงระบบพร้อมแล้ว (ยังไม่ compile จริง)\n",
            android.graphics.Color.parseColor("#E0AF68"));
    appendConsoleLine("ขั้นถัดไป: ดาวน์โหลดเครื่องมือ (aapt/d8/ecj)\n",
            android.graphics.Color.parseColor("#565F89"));

    if (localBuildManager == null) {
        localBuildManager = new LocalBuildManager(this);
    }
    localBuildManager.startBuild(
            currentProject != null ? currentProject.getRootPath() : null,
            msg -> appendConsoleLine(msg + "\n",
                    android.graphics.Color.parseColor("#A9B1D6"))
    );
}


    }