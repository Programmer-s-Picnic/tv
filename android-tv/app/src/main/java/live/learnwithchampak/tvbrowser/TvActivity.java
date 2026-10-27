package live.learnwithchampak.tvbrowser;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
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

public class TvActivity extends Activity {
    private static final String UPDATE_INFO =
            "https://raw.githubusercontent.com/Programmer-s-Picnic/tv/main/android-tv/update.json";

    private final List<BrowserTab> tabs = new ArrayList<>();
    private LinearLayout tabStrip;
    private FrameLayout webContainer;
    private EditText addressBar;
    private TextView pointer;
    private BrowserTab activeTab;
    private float pointerX = 500;
    private float pointerY = 300;

    private static class BrowserTab {
        WebView webView;
        Button tabButton;
        String title = "New Tab";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        newTab();

        Uri incoming = getIntent() == null ? null : getIntent().getData();
        if (incoming != null) loadUrl(incoming.toString());

        requestDefaultBrowser();
        getWindow().getDecorView().postDelayed(this::ensureUpdatePermission, 800);
        getWindow().getDecorView().postDelayed(this::checkForUpdates, 1800);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView brandText() {
        TextView text = new TextView(this);
        text.setText("Champak Roy");
        text.setTextColor(Color.rgb(18, 55, 105));
        text.setTextSize(20);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setPadding(dp(8), 0, dp(12), 0);
        text.setTypeface(null, android.graphics.Typeface.BOLD);
        return text;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(16);
        b.setMinHeight(dp(50));
        b.setFocusable(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(50));
        lp.setMargins(dp(3), 0, dp(3), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(232, 244, 255));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(6), dp(8), dp(6));
        top.setBackgroundColor(Color.rgb(225, 240, 255));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.champak_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(48), dp(48));
        logoLp.setMargins(0, 0, dp(4), 0);
        logo.setLayoutParams(logoLp);

        addressBar = new EditText(this);
        addressBar.setSingleLine(true);
        addressBar.setHint("Enter address or search");
        addressBar.setTextSize(18);
        addressBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressBar.setPadding(dp(12), 0, dp(12), 0);
        addressBar.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams addressLp = new LinearLayout.LayoutParams(0, dp(50), 1f);
        addressLp.setMargins(dp(6), 0, dp(6), 0);
        addressBar.setLayoutParams(addressLp);
        addressBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                loadFromAddressBar();
                return true;
            }
            return false;
        });

        Button go = button("Go");
        go.setOnClickListener(v -> loadFromAddressBar());

        Button reload = button("Reload");
        reload.setOnClickListener(v -> {
            if (activeTab != null) activeTab.webView.reload();
        });

        Button newTab = button("+ New Tab");
        newTab.setOnClickListener(v -> newTab());

        Button closeAll = button("Close All Tabs");
        closeAll.setOnClickListener(v -> closeAllTabs());

        top.addView(logo);
        top.addView(brandText());
        top.addView(addressBar);
        top.addView(go);
        top.addView(reload);
        top.addView(newTab);
        top.addView(closeAll);

        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.setBackgroundColor(Color.rgb(211, 232, 252));
        tabStrip = new LinearLayout(this);
        tabStrip.setOrientation(LinearLayout.HORIZONTAL);
        tabStrip.setPadding(dp(8), dp(4), dp(8), dp(4));
        tabScroll.addView(tabStrip, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        webContainer = new FrameLayout(this);
        webContainer.setBackgroundColor(Color.WHITE);
        webContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        page.addView(top);
        page.addView(tabScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
        page.addView(webContainer);
        root.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        pointer = new TextView(this);
        pointer.setText("➤");
        pointer.setTextSize(38);
        pointer.setTextColor(Color.WHITE);
        pointer.setShadowLayer(6, 2, 2, Color.BLACK);
        pointer.setGravity(Gravity.CENTER);
        pointer.setFocusable(false);
        pointer.setClickable(false);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(58), dp(58));
        pointer.setLayoutParams(pp);
        root.addView(pointer);

        setContentView(root);
        root.post(() -> {
            pointerX = root.getWidth() / 2f;
            pointerY = root.getHeight() / 2f;
            updatePointer();
        });
    }

    private void newTab() {
        BrowserTab tab = new BrowserTab();
        tab.webView = createWebView(tab);
        tab.tabButton = button("New Tab");
        tab.tabButton.setMinWidth(dp(150));
        tab.tabButton.setOnClickListener(v -> activateTab(tab));

        tabs.add(tab);
        tabStrip.addView(tab.tabButton);
        webContainer.addView(tab.webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        activateTab(tab);
        showStartPage(tab.webView);
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
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMediaPlaybackRequiresUserGesture(false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String scheme = request.getUrl().getScheme();
                return !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (tab == activeTab) addressBar.setText(url);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onReceivedTitle(WebView view, String title) {
                if (title == null || title.trim().isEmpty()) title = "Tab";
                tab.title = title.length() > 18 ? title.substring(0, 18) + "…" : title;
                tab.tabButton.setText(tab.title);
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
            t.tabButton.setAlpha(t == tab ? 1f : 0.65f);
        }
        String url = tab.webView.getUrl();
        addressBar.setText(url == null || url.startsWith("data:") ? "" : url);
    }

    private void closeAllTabs() {
        for (BrowserTab tab : tabs) {
            webContainer.removeView(tab.webView);
            tab.webView.destroy();
        }
        tabs.clear();
        tabStrip.removeAllViews();
        activeTab = null;
        newTab();
        Toast.makeText(this, "All tabs closed", Toast.LENGTH_SHORT).show();
    }

    private void showStartPage(WebView web) {
        String html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>body{font-family:sans-serif;background:#edf7ff;color:#17365d;text-align:center;padding:10vh 4vw}"
                + "h1{font-size:7vh;margin:0 0 2vh}p{font-size:3vh}.box{display:inline-block;padding:4vh 5vw;"
                + "background:#fff;border:3px solid #4aa3ff;border-radius:22px;font-size:3.2vh}</style></head>"
                + "<body><h1>Champak TV Browser</h1><p>Use the remote arrows to move the pointer.</p>"
                + "<div class='box'>Move pointer • Press OK to click</div></body></html>";
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
        pointer.setX(pointerX - pointer.getWidth() / 2f);
        pointer.setY(pointerY - pointer.getHeight() / 2f);
        pointer.bringToFront();
    }

    private void clickPointer() {
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
        } catch (Exception ignored) {
        }
    }

    private void ensureUpdatePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !getPackageManager().canRequestPackageInstalls()) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Exception ignored) {
            }
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
                    String apk = json.getString("apkUrl");
                    downloadAndInstallUpdate(apk);
                }
            } catch (Exception ignored) {
            }
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
            File target = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "champak-tv-browser-update.apk");
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
                    Toast.makeText(this, "Update downloaded. Allow app installs to continue.", Toast.LENGTH_LONG).show();
                    ensureUpdatePermission();
                }
            });
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        for (BrowserTab tab : tabs) {
            tab.webView.destroy();
        }
        super.onDestroy();
    }
}
