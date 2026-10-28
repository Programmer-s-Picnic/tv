package live.learnwithchampak.tvbrowser;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.UiModeManager;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String UPDATE_INFO =
            "https://raw.githubusercontent.com/Programmer-s-Picnic/tv/main/android-tv/update.json";

    private final List<BrowserTab> tabs = new ArrayList<>();
    private LinearLayout tabStrip;
    private FrameLayout webContainer;
    private EditText addressBar;
    private TextView pointer;
    private TextView tabCount;
    private BrowserTab activeTab;
    private float pointerX = 500;
    private float pointerY = 300;
    private boolean tvMode;
    private boolean tabletMode;

    private static class BrowserTab {
        WebView webView;
        Button tabButton;
        String title = "New Tab";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        detectDeviceClass();
        buildUi();
        newTab();

        Uri incoming = getIntent() == null ? null : getIntent().getData();
        if (incoming != null) loadUrl(incoming.toString());

        requestDefaultBrowser();
        getWindow().getDecorView().postDelayed(this::ensureUpdatePermission, 800);
        getWindow().getDecorView().postDelayed(this::checkForUpdates, 1800);
    }

    private void detectDeviceClass() {
        UiModeManager manager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        tvMode = manager != null && manager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
        int sw = getResources().getConfiguration().smallestScreenWidthDp;
        tabletMode = !tvMode && sw >= 600;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable roundedBackground(int fill, int stroke, int radiusDp) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fill);
        bg.setCornerRadius(dp(radiusDp));
        if (stroke != Color.TRANSPARENT) bg.setStroke(dp(1), stroke);
        return bg;
    }

    private void stylePrimary(Button b) {
        b.setTextColor(Color.WHITE);
        b.setTypeface(null, android.graphics.Typeface.BOLD);
        b.setBackground(roundedBackground(Color.rgb(9, 105, 218), Color.rgb(9, 105, 218), 12));
        b.setPadding(dp(14), 0, dp(14), 0);
    }

    private void styleSecondary(Button b) {
        b.setTextColor(Color.rgb(18, 55, 105));
        b.setBackground(roundedBackground(Color.WHITE, Color.rgb(190, 215, 238), 12));
        b.setPadding(dp(12), 0, dp(12), 0);
    }

    private TextView brandText(float size) {
        TextView text = new TextView(this);
        text.setText("Champak Roy");
        text.setTextColor(Color.rgb(18, 55, 105));
        text.setTextSize(size);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setPadding(dp(6), 0, dp(8), 0);
        text.setTypeface(null, android.graphics.Typeface.BOLD);
        return text;
    }

    private Button button(String label, int height, float textSize) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(textSize);
        b.setMinHeight(dp(height));
        b.setAllCaps(false);
        b.setFocusable(tvMode);
        styleSecondary(b);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(height));
        lp.setMargins(dp(3), 0, dp(3), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private ImageView logo(int size) {
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.champak_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        logo.setLayoutParams(new LinearLayout.LayoutParams(dp(size), dp(size)));
        return logo;
    }

    private void configureAddressBar(int height, float size) {
        addressBar = new EditText(this);
        addressBar.setSingleLine(true);
        addressBar.setHint("Search or enter address");
        addressBar.setTextSize(size);
        addressBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressBar.setPadding(dp(14), 0, dp(14), 0);
        addressBar.setTextColor(Color.rgb(25, 45, 65));
        addressBar.setHintTextColor(Color.rgb(110, 130, 150));
        addressBar.setBackground(roundedBackground(Color.WHITE, Color.rgb(175, 205, 232), 14));
        addressBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                loadFromAddressBar();
                return true;
            }
            return false;
        });
    }

    private void buildUi() {
        if (tvMode) buildTvUi();
        else if (tabletMode) buildTabletUi();
        else buildPhoneUi();
    }

    private void buildPhoneUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setOrientation(LinearLayout.HORIZONTAL);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        brandRow.setPadding(dp(10), dp(5), dp(10), dp(2));
        brandRow.setBackgroundColor(Color.rgb(243, 249, 255));
        brandRow.addView(logo(34));
        brandRow.addView(brandText(18));

        tabCount = new TextView(this);
        tabCount.setText("1 tab");
        tabCount.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        tabCount.setTextSize(14);
        tabCount.setTextColor(Color.DKGRAY);
        brandRow.addView(tabCount, new LinearLayout.LayoutParams(0, dp(38), 1f));

        LinearLayout addressRow = new LinearLayout(this);
        addressRow.setOrientation(LinearLayout.HORIZONTAL);
        addressRow.setGravity(Gravity.CENTER_VERTICAL);
        addressRow.setPadding(dp(8), dp(4), dp(8), dp(5));
        addressRow.setBackgroundColor(Color.rgb(243, 249, 255));

        configureAddressBar(46, 16);
        addressRow.addView(addressBar, new LinearLayout.LayoutParams(0, dp(46), 1f));

        Button go = button("Go", 46, 14);
        stylePrimary(go);
        go.setOnClickListener(v -> loadFromAddressBar());
        Button reload = button("↻", 46, 22);
        reload.setContentDescription("Reload");
        reload.setOnClickListener(v -> { if (activeTab != null) activeTab.webView.reload(); });

        addressRow.addView(go);
        addressRow.addView(reload);

        tabStrip = new LinearLayout(this);
        tabStrip.setOrientation(LinearLayout.HORIZONTAL);
        tabStrip.setVisibility(View.GONE);

        webContainer = new FrameLayout(this);
        webContainer.setBackgroundColor(Color.WHITE);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(dp(6), dp(4), dp(6), dp(4));
        bottom.setBackgroundColor(Color.rgb(248, 252, 255));

        Button back = button("‹", 50, 26);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> { if (activeTab != null && activeTab.webView.canGoBack()) activeTab.webView.goBack(); });

        Button tabsButton = button("Tabs", 50, 14);
        tabsButton.setOnClickListener(v -> showTabChooser());

        Button newTab = button("+ Tab", 50, 14);
        newTab.setOnClickListener(v -> newTab());

        Button closeAll = button("Close all", 50, 14);
        closeAll.setOnClickListener(v -> closeAllTabs());

        bottom.addView(back, new LinearLayout.LayoutParams(0, dp(50), 1f));
        bottom.addView(tabsButton, new LinearLayout.LayoutParams(0, dp(50), 1f));
        bottom.addView(newTab, new LinearLayout.LayoutParams(0, dp(50), 1f));
        bottom.addView(closeAll, new LinearLayout.LayoutParams(0, dp(50), 1f));

        page.addView(brandRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(45)));
        page.addView(addressRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(55)));
        page.addView(webContainer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        page.addView(bottom, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        root.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    private void buildTabletUi() {
        FrameLayout root = new FrameLayout(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(5), dp(8), dp(5));
        top.setBackgroundColor(Color.rgb(243, 249, 255));
        top.addView(logo(40));
        top.addView(brandText(18));

        configureAddressBar(48, 17);
        top.addView(addressBar, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button go = button("Go", 48, 15);
        stylePrimary(go);
        go.setOnClickListener(v -> loadFromAddressBar());
        Button reload = button("Reload", 48, 15);
        reload.setOnClickListener(v -> { if (activeTab != null) activeTab.webView.reload(); });
        Button newTab = button("+ Tab", 48, 15);
        newTab.setOnClickListener(v -> newTab());
        top.addView(go);
        top.addView(reload);
        top.addView(newTab);

        HorizontalScrollView tabScroll = createTabStrip();

        webContainer = new FrameLayout(this);
        webContainer.setBackgroundColor(Color.WHITE);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        bottom.setPadding(dp(8), dp(2), dp(8), dp(2));
        bottom.setBackgroundColor(Color.rgb(248, 252, 255));
        Button closeAll = button("Close All Tabs", 44, 14);
        closeAll.setOnClickListener(v -> closeAllTabs());
        bottom.addView(closeAll);

        page.addView(top);
        page.addView(tabScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        page.addView(webContainer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        page.addView(bottom, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        root.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    private void buildTvUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(232, 244, 255));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(6), dp(8), dp(6));
        top.setBackgroundColor(Color.rgb(243, 249, 255));

        top.addView(logo(48));
        top.addView(brandText(20));

        configureAddressBar(50, 18);
        top.addView(addressBar, new LinearLayout.LayoutParams(0, dp(50), 1f));

        Button go = button("Go", 50, 16);
        go.setOnClickListener(v -> loadFromAddressBar());
        Button reload = button("Reload", 50, 16);
        reload.setOnClickListener(v -> { if (activeTab != null) activeTab.webView.reload(); });
        Button newTab = button("+ New Tab", 50, 16);
        newTab.setOnClickListener(v -> newTab());
        Button closeAll = button("Close All Tabs", 50, 16);
        closeAll.setOnClickListener(v -> closeAllTabs());

        top.addView(go);
        top.addView(reload);
        top.addView(newTab);
        top.addView(closeAll);

        HorizontalScrollView tabScroll = createTabStrip();
        webContainer = new FrameLayout(this);
        webContainer.setBackgroundColor(Color.WHITE);

        page.addView(top);
        page.addView(tabScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
        page.addView(webContainer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        pointer = new TextView(this);
        pointer.setText("➤");
        pointer.setTextSize(38);
        pointer.setTextColor(Color.rgb(255, 179, 0));
        pointer.setShadowLayer(7, 2, 2, Color.BLACK);
        pointer.setGravity(Gravity.CENTER);
        pointer.setFocusable(false);
        pointer.setClickable(false);
        pointer.setLayoutParams(new FrameLayout.LayoutParams(dp(58), dp(58)));
        root.addView(pointer);

        setContentView(root);
        root.post(() -> {
            pointerX = root.getWidth() / 2f;
            pointerY = root.getHeight() / 2f;
            updatePointer();
        });
    }

    private HorizontalScrollView createTabStrip() {
        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.setBackgroundColor(Color.rgb(235, 246, 255));
        tabStrip = new LinearLayout(this);
        tabStrip.setOrientation(LinearLayout.HORIZONTAL);
        tabStrip.setPadding(dp(8), dp(4), dp(8), dp(4));
        tabScroll.addView(tabStrip, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return tabScroll;
    }

    private void newTab() {
        BrowserTab tab = new BrowserTab();
        tab.webView = createWebView(tab);
        tab.tabButton = button("New Tab", tvMode ? 46 : 44, tvMode ? 15 : 14);
        tab.tabButton.setMinWidth(dp(tvMode ? 150 : 130));
        tab.tabButton.setOnClickListener(v -> activateTab(tab));

        tabs.add(tab);
        if (tabStrip != null) tabStrip.addView(tab.tabButton);
        webContainer.addView(tab.webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        activateTab(tab);
        showStartPage(tab.webView);
        updateTabCount();
    }

    private WebView createWebView(BrowserTab tab) {
        WebView web = new WebView(this);
        web.setBackgroundColor(Color.WHITE);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);

        if (tvMode || tabletMode) {
            s.setLoadWithOverviewMode(true);
            s.setUseWideViewPort(true);
        } else {
            s.setLoadWithOverviewMode(false);
            s.setUseWideViewPort(false);
        }

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String scheme = request.getUrl().getScheme();
                return !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (tab == activeTab && !url.startsWith("data:")) addressBar.setText(url);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onReceivedTitle(WebView view, String title) {
                if (title == null || title.trim().isEmpty()) title = "Tab";
                tab.title = title.length() > 18 ? title.substring(0, 18) + "…" : title;
                if (tab.tabButton != null) tab.tabButton.setText(tab.title);
            }
        });

        web.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            try {
                DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "download");
                ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(req);
                Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Download failed", Toast.LENGTH_SHORT).show();
            }
        });
        return web;
    }

    private void activateTab(BrowserTab tab) {
        activeTab = tab;
        for (BrowserTab t : tabs) {
            t.webView.setVisibility(t == tab ? View.VISIBLE : View.GONE);
            if (t.tabButton != null) {
                t.tabButton.setAlpha(t == tab ? 1f : 0.78f);
                if (t == tab) {
                    t.tabButton.setTextColor(Color.WHITE);
                    t.tabButton.setBackground(roundedBackground(Color.rgb(9, 105, 218), Color.rgb(9, 105, 218), 11));
                } else {
                    styleSecondary(t.tabButton);
                }
            }
        }
        String url = tab.webView.getUrl();
        addressBar.setText(url == null || url.startsWith("data:") ? "" : url);
    }

    private void showTabChooser() {
        if (tabs.isEmpty()) return;
        String[] labels = new String[tabs.size()];
        for (int i = 0; i < tabs.size(); i++) {
            labels[i] = (i + 1) + ". " + tabs.get(i).title;
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("Open Tabs")
                .setItems(labels, (d, which) -> activateTab(tabs.get(which)))
                .setNegativeButton("Close", null)
                .show();
    }

    private void updateTabCount() {
        if (tabCount != null) {
            int n = tabs.size();
            tabCount.setText(n + (n == 1 ? " tab" : " tabs"));
        }
    }

    private void closeAllTabs() {
        for (BrowserTab tab : tabs) {
            webContainer.removeView(tab.webView);
            tab.webView.destroy();
        }
        tabs.clear();
        if (tabStrip != null) tabStrip.removeAllViews();
        activeTab = null;
        newTab();
        Toast.makeText(this, "All tabs closed", Toast.LENGTH_SHORT).show();
    }

    private void showStartPage(WebView web) {
        String message = tvMode
                ? "Use remote arrows to move the pointer. Press OK to click."
                : "Search or enter a website above.";
        String html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>body{font-family:sans-serif;background:#edf7ff;color:#17365d;text-align:center;padding:10vh 5vw}"
                + "h1{font-size:clamp(32px,7vw,72px);margin:0 0 2vh}p{font-size:clamp(18px,3vw,30px)}"
                + ".box{display:inline-block;padding:24px 30px;background:#fff;border:3px solid #4aa3ff;"
                + "border-radius:22px;font-size:clamp(18px,2.6vw,28px)}</style></head>"
                + "<body><h1>Champak Browser</h1><p>" + message + "</p>"
                + "<div class='box'>Fast • Simple • Device-aware</div></body></html>";
        web.loadDataWithBaseURL("https://start.learnwithchampak.live/", html, "text/html", "UTF-8", null);
        addressBar.setText("");
    }

    private void loadFromAddressBar() {
        loadUrl(addressBar.getText().toString());
    }

    private void loadUrl(String raw) {
        if (activeTab == null) newTab();
        String input = raw == null ? "" : raw.trim();
        if (input.isEmpty()) return;

        String url;
        if (input.matches("(?i)^https?://.+")) {
            url = input;
        } else if (input.matches("(?i)^[a-z0-9.-]+\\.[a-z]{2,}([/:?#].*)?$")) {
            url = "https://" + input;
        } else {
            url = "https://www.google.com/search?q=" + Uri.encode(input);
        }
        activeTab.webView.loadUrl(url);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!tvMode || pointer == null) return super.dispatchKeyEvent(event);
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            float step = dp(28);
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    pointerX -= step; clampPointer(); updatePointer(); return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    pointerX += step; clampPointer(); updatePointer(); return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    pointerY -= step; clampPointer(); updatePointer(); return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    pointerY += step; clampPointer(); updatePointer(); return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_BUTTON_A:
                    clickPointer();
                    return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void clampPointer() {
        View root = (View) pointer.getParent();
        pointerX = Math.max(dp(8), Math.min(pointerX, root.getWidth() - dp(8)));
        pointerY = Math.max(dp(8), Math.min(pointerY, root.getHeight() - dp(8)));
    }

    private void updatePointer() {
        if (pointer == null) return;
        pointer.setX(pointerX - pointer.getWidth() / 2f);
        pointer.setY(pointerY - pointer.getHeight() / 2f);
        pointer.bringToFront();
    }

    private void clickPointer() {
        if (pointer == null) return;
        pointer.setVisibility(View.INVISIBLE);
        long now = System.currentTimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, pointerX, pointerY, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, pointerX, pointerY, 0);
        super.dispatchTouchEvent(down);
        super.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
        pointer.setVisibility(View.VISIBLE);
        pointer.bringToFront();
    }

    private void requestDefaultBrowser() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                RoleManager roleManager = (RoleManager) getSystemService(ROLE_SERVICE);
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)
                        && !roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)) {
                    startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER), 7001);
                }
            } else {
                startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
            }
        } catch (Exception ignored) {}
    }

    private void ensureUpdatePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !getPackageManager().canRequestPackageInstalls()) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Exception ignored) {}
        }
    }

    private void checkForUpdates() {
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(UPDATE_INFO).openConnection();
                c.setConnectTimeout(6000);
                c.setReadTimeout(6000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(c.getInputStream()));
                StringBuilder body = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
                reader.close();

                JSONObject json = new JSONObject(body.toString());
                int remoteCode = json.getInt("versionCode");
                if (remoteCode > currentVersionCode()) {
                    downloadAndInstallUpdate(json.getString("apkUrl"));
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private int currentVersionCode() {
        try {
            android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return (int) info.getLongVersionCode();
            return info.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    private void downloadAndInstallUpdate(String apkUrl) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(apkUrl).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(15000);
            File target = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                    "champak-tv-browser-update.apk");
            BufferedInputStream in = new BufferedInputStream(c.getInputStream());
            FileOutputStream out = new FileOutputStream(target);
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            out.close();
            in.close();

            runOnUiThread(() -> {
                try {
                    Uri uri = FileProvider.getUriForFile(this,
                            getPackageName() + ".fileprovider", target);
                    Intent install = new Intent(Intent.ACTION_VIEW);
                    install.setDataAndType(uri, "application/vnd.android.package-archive");
                    install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(install);
                } catch (Exception e) {
                    Toast.makeText(this, "Update downloaded. Allow app installs to continue.",
                            Toast.LENGTH_LONG).show();
                    ensureUpdatePermission();
                }
            });
        } catch (Exception ignored) {}
    }

    @Override
    public void onBackPressed() {
        if (activeTab != null && activeTab.webView.canGoBack()) activeTab.webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        for (BrowserTab tab : tabs) tab.webView.destroy();
        super.onDestroy();
    }
}
