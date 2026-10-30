package live.learnwithchampak.tvbrowser;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
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
import java.util.Locale;
import java.util.List;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

public class TvActivity extends Activity {
    private static final String UPDATE_INFO =
            "https://raw.githubusercontent.com/Programmer-s-Picnic/tv/main/android-tv/update.json";

    private final List<BrowserTab> tabs = new ArrayList<>();
    private LinearLayout tabStrip;
    private FrameLayout webContainer;
    private EditText addressBar;
    private TextView pointer;
    private LinearLayout topBar;
    private HorizontalScrollView tabScroll;
    private Button modeButton;
    private TextView statusBadge;
    private ProgressBar loadingBar;
    private Button menuButton;
    private BrowserTab activeTab;
    private boolean focusMode = true;
    private boolean fullScreen = false;
    private boolean transientControls = false;
    private SharedPreferences prefs;
    private int pointerSpeed = 28;
    private long lastPointerMove = 0L;
    private int pointerRepeat = 0;
    private final Runnable hidePointerRunnable = () -> {
        if (!focusMode && pointer != null && System.currentTimeMillis() - lastPointerMove >= 2200) {
            pointer.setVisibility(View.GONE);
        }
    };
    private static final int VOICE_REQUEST = 7101;
    private float pointerX = 500;
    private float pointerY = 300;

    private static class BrowserTab {
        WebView webView;
        Button tabButton;
        String title = "New Tab";
    }

    private class TvEditText extends EditText {
        TvEditText(Context context) {
            super(context);
        }

        @Override
        public boolean onKeyPreIme(int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_BACK &&
                    event.getAction() == KeyEvent.ACTION_UP) {
                hideKeyboardAndClearAddressFocus();
                return true;
            }
            return super.onKeyPreIme(keyCode, event);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("tv_browser", MODE_PRIVATE);
        focusMode = prefs.getBoolean("focus_default", true);
        pointerSpeed = prefs.getInt("pointer_speed", 28);
        buildUi();
        restoreTabsOrNew();
        updateModeUi();

        Uri incoming = getIntent() == null ? null : getIntent().getData();
        if (incoming != null) loadUrl(incoming.toString());

        requestDefaultBrowser();
        getWindow().getDecorView().postDelayed(this::ensureUpdatePermission, 800);
        getWindow().getDecorView().postDelayed(this::checkForUpdates, 1800);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int navy() { return Color.rgb(5, 20, 39); }
    private int navy2() { return Color.rgb(10, 34, 63); }
    private int navy3() { return Color.rgb(15, 48, 82); }
    private int electricBlue() { return Color.rgb(31, 120, 255); }
    private int cyan() { return Color.rgb(38, 214, 255); }
    private int gold() { return Color.rgb(255, 179, 0); }

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
        b.setBackground(roundedBackground(electricBlue(), cyan(), 14));
        b.setPadding(dp(15), 0, dp(15), 0);
        b.setStateListAnimator(null);
    }

    private void styleSecondary(Button b) {
        b.setTextColor(Color.rgb(225, 239, 255));
        b.setBackground(roundedBackground(navy3(), Color.rgb(47, 98, 145), 14));
        b.setPadding(dp(14), 0, dp(14), 0);
        b.setStateListAnimator(null);
        b.setOnFocusChangeListener((v, hasFocus) -> {
            Button x = (Button) v;
            if (hasFocus) {
                x.setTextColor(Color.WHITE);
                x.setBackground(roundedBackground(Color.rgb(21, 91, 170), cyan(), 14));
                x.animate().scaleX(1.035f).scaleY(1.035f).setDuration(120).start();
            } else {
                x.setTextColor(Color.rgb(225, 239, 255));
                x.setBackground(roundedBackground(navy3(), Color.rgb(47, 98, 145), 14));
                x.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
            }
        });
    }

    private void hideKeyboardAndClearAddressFocus() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            View focused = getCurrentFocus();
            if (imm != null && focused != null) {
                imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
            }
        } catch (Exception ignored) {
        }
        if (addressBar != null) {
            addressBar.clearFocus();
        }
        if (activeTab != null && activeTab.webView != null) {
            activeTab.webView.requestFocus();
        }
        if (pointer != null) {
            pointer.bringToFront();
        }
    }

    private TextView brandText() {
        TextView text = new TextView(this);
        text.setText("Champak\nTV Browser");
        text.setTextColor(Color.WHITE);
        text.setTextSize(18);
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
        b.setFocusable(true);
        b.setAllCaps(false);
        styleSecondary(b);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(50));
        lp.setMargins(dp(3), 0, dp(3), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(navy());

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);

        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(8), dp(6), dp(8), dp(6));
        topBar.setBackgroundColor(navy());

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(52), dp(52));
        logoLp.setMargins(0, 0, dp(4), 0);
        logo.setLayoutParams(logoLp);

        addressBar = new TvEditText(this);
        addressBar.setSingleLine(true);
        addressBar.setHint("Enter address or search");
        addressBar.setTextSize(18);
        addressBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            addressBar.setShowSoftInputOnFocus(false);
        }
        addressBar.setPadding(dp(15), 0, dp(15), 0);
        addressBar.setTextColor(Color.WHITE);
        addressBar.setHintTextColor(Color.rgb(155, 185, 215));
        addressBar.setBackground(roundedBackground(navy2(), Color.rgb(57, 114, 170), 16));
        LinearLayout.LayoutParams addressLp = new LinearLayout.LayoutParams(0, dp(50), 1f);
        addressLp.setMargins(dp(6), 0, dp(6), 0);
        addressBar.setLayoutParams(addressLp);
        addressBar.setOnClickListener(v -> showSearchOverlay());
        addressBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                loadFromAddressBar();
                hideKeyboardAndClearAddressFocus();
                return true;
            }
            return false;
        });

        Button go = button("Go →");
        stylePrimary(go);
        go.setOnClickListener(v -> {
            loadFromAddressBar();
            hideKeyboardAndClearAddressFocus();
        });

        Button reload = button("↻");
        reload.setOnClickListener(v -> {
            if (activeTab != null) activeTab.webView.reload();
        });

        topBar.addView(logo);
        topBar.addView(brandText());
        topBar.addView(addressBar);
        topBar.addView(go);
        topBar.addView(reload);

        statusBadge = new TextView(this);
        statusBadge.setTextSize(13);
        statusBadge.setGravity(Gravity.CENTER);
        statusBadge.setPadding(dp(10), 0, dp(10), 0);
        statusBadge.setTypeface(null, android.graphics.Typeface.BOLD);
        topBar.addView(statusBadge, new LinearLayout.LayoutParams(dp(92), dp(40)));

        menuButton = button("☰  TV Menu");
        stylePrimary(menuButton);
        menuButton.setOnClickListener(v -> showTvMenu());
        topBar.addView(menuButton);

        tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.setBackgroundColor(navy2());
        tabStrip = new LinearLayout(this);
        tabStrip.setOrientation(LinearLayout.HORIZONTAL);
        tabStrip.setPadding(dp(8), dp(4), dp(8), dp(4));
        tabScroll.addView(tabStrip, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        webContainer = new FrameLayout(this);
        webContainer.setBackgroundColor(navy());
        webContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        page.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(62)));
        page.addView(tabScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));

        loadingBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        loadingBar.setMax(100);
        loadingBar.setProgress(0);
        loadingBar.setVisibility(View.GONE);
        page.addView(loadingBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        page.addView(webContainer);
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
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(58), dp(58));
        pointer.setLayoutParams(pp);
        root.addView(pointer);
        pointer.setVisibility(focusMode ? View.GONE : View.VISIBLE);

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
        tab.tabButton = button("+  New Tab");
        tab.tabButton.setMinWidth(dp(170));
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
        s.setDatabaseEnabled(false);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setDefaultFontSize(prefs == null ? 16 : prefs.getInt("text_size", 16));

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (loadingBar != null && tab == activeTab) {
                    loadingBar.setProgress(8);
                    loadingBar.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String scheme = request.getUrl().getScheme();
                return !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (tab == activeTab) {
                    addressBar.setText(url != null && url.startsWith("data:") ? "" : url);
                    if (loadingBar != null) loadingBar.setVisibility(View.GONE);
                }
                recordRecentUrl(url);
                recordHistory(url);
                if (focusMode && tab == activeTab) installTvFocusMode(true);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        android.webkit.WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    if (loadingBar != null) loadingBar.setVisibility(View.GONE);
                    showErrorPage(view);
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (tab == activeTab && loadingBar != null) {
                    loadingBar.setProgress(newProgress);
                    loadingBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
                }
            }

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
            if (t == tab) {
                t.webView.setAlpha(0.82f);
                t.webView.animate().alpha(1f).setDuration(140).start();
            }
            t.tabButton.setAlpha(t == tab ? 1f : 0.78f);
            if (t == tab) {
                t.tabButton.setTextColor(Color.WHITE);
                t.tabButton.setBackground(roundedBackground(Color.rgb(9, 105, 218), Color.rgb(9, 105, 218), 11));
            } else {
                styleSecondary(t.tabButton);
            }
        }
        String url = tab.webView.getUrl();
        addressBar.setText(url == null || url.startsWith("data:") ? "" : url);
    }

    private void closeAllTabs() {
        for (BrowserTab tab : tabs) {
            webContainer.removeView(tab.webView);
            try { tab.webView.clearCache(true); } catch (Exception ignored) {}
            tab.webView.destroy();
        }
        tabs.clear();
        tabStrip.removeAllViews();
        activeTab = null;
        newTab();
        Toast.makeText(this, "All tabs closed", Toast.LENGTH_SHORT).show();
    }

    private void updateModeUi() {
        if (statusBadge == null) return;
        String label = fullScreen ? "FULL" : (focusMode ? "FOCUS" : "POINTER");
        statusBadge.setText(label);
        statusBadge.setTextColor(Color.WHITE);
        int fill = fullScreen ? Color.rgb(49, 33, 110)
                : (focusMode ? Color.rgb(0, 119, 166) : navy3());
        statusBadge.setBackground(roundedBackground(fill, focusMode ? cyan() : Color.rgb(67, 112, 155), 14));
    }

    private void recordSearch(String query) {
        if (prefs == null || query == null || query.trim().isEmpty()) return;
        String q = query.trim();
        String old = prefs.getString("recent_searches", "");
        List<String> list = new ArrayList<>();
        list.add(q);
        for (String x : old.split("\n")) {
            if (!x.trim().isEmpty() && !x.equalsIgnoreCase(q) && list.size() < 5) list.add(x);
        }
        prefs.edit().putString("recent_searches", android.text.TextUtils.join("\n", list)).apply();
    }

    private void recordRecentUrl(String url) {
        if (prefs == null || url == null || !(url.startsWith("http://") || url.startsWith("https://"))) return;
        if (url.startsWith("https://start.learnwithchampak.live/")) return;
        String old = prefs.getString("recent_urls", "");
        List<String> list = new ArrayList<>();
        list.add(url);
        for (String x : old.split("\n")) {
            if (!x.trim().isEmpty() && !x.equals(url) && list.size() < 6) list.add(x);
        }
        prefs.edit().putString("recent_urls", android.text.TextUtils.join("\n", list))
                .putString("last_url", url).apply();
    }

    private void recordHistory(String url) {
        if (prefs == null || url == null || !(url.startsWith("http://") || url.startsWith("https://"))) return;
        if (url.startsWith("https://start.learnwithchampak.live/")
                || url.startsWith("https://error.learnwithchampak.live/")) return;

        String old = prefs.getString("history", "");
        List<String> list = new ArrayList<>();
        list.add(url);
        for (String x : old.split("\n")) {
            if (!x.trim().isEmpty() && !x.equals(url) && list.size() < 50) list.add(x);
        }
        prefs.edit().putString("history", android.text.TextUtils.join("\n", list)).apply();
    }

    private void showBookmarks() {
        String saved = prefs == null ? "" : prefs.getString("bookmarks", "");
        List<String> items = new ArrayList<>();
        for (String u : saved.split("\n")) if (!u.trim().isEmpty()) items.add(u);

        if (items.isEmpty()) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Bookmarks")
                    .setMessage("No bookmarks yet. Open a webpage and choose “Add / Remove Bookmark” from TV Menu.")
                    .setPositiveButton("Close", null)
                    .show();
            return;
        }

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), dp(8), dp(12), dp(8));
        final android.app.AlertDialog[] holder = new android.app.AlertDialog[1];

        for (String url : items) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            Button open = button("★ " + shortLabel(url));
            open.setContentDescription(url);
            open.setOnClickListener(v -> {
                loadUrl(url);
                holder[0].dismiss();
            });

            Button remove = button("Remove");
            remove.setOnClickListener(v -> {
                removeBookmark(url);
                holder[0].dismiss();
                showBookmarks();
            });

            row.addView(open, new LinearLayout.LayoutParams(0, dp(50), 1f));
            row.addView(remove, new LinearLayout.LayoutParams(dp(130), dp(50)));
            list.addView(row);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        holder[0] = new android.app.AlertDialog.Builder(this)
                .setTitle("Bookmarks")
                .setView(scroll)
                .setPositiveButton("Close", null)
                .create();
        holder[0].show();
    }

    private void removeBookmark(String url) {
        if (prefs == null) return;
        String old = prefs.getString("bookmarks", "");
        List<String> list = new ArrayList<>();
        for (String x : old.split("\n")) {
            if (!x.trim().isEmpty() && !x.equals(url)) list.add(x);
        }
        prefs.edit().putString("bookmarks", android.text.TextUtils.join("\n", list)).apply();
    }

    private void showHistory() {
        String saved = prefs == null ? "" : prefs.getString("history", "");
        List<String> items = new ArrayList<>();
        for (String u : saved.split("\n")) if (!u.trim().isEmpty()) items.add(u);

        if (items.isEmpty()) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("History")
                    .setMessage("No browsing history yet.")
                    .setPositiveButton("Close", null)
                    .show();
            return;
        }

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), dp(8), dp(12), dp(8));
        final android.app.AlertDialog[] holder = new android.app.AlertDialog[1];

        for (String url : items) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            Button open = button(shortLabel(url));
            open.setContentDescription(url);
            open.setOnClickListener(v -> {
                loadUrl(url);
                holder[0].dismiss();
            });

            Button remove = button("Remove");
            remove.setOnClickListener(v -> {
                removeHistoryItem(url);
                holder[0].dismiss();
                showHistory();
            });

            row.addView(open, new LinearLayout.LayoutParams(0, dp(50), 1f));
            row.addView(remove, new LinearLayout.LayoutParams(dp(130), dp(50)));
            list.addView(row);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        holder[0] = new android.app.AlertDialog.Builder(this)
                .setTitle("History")
                .setView(scroll)
                .setNeutralButton("Clear All", (d, w) -> confirmClearHistory())
                .setPositiveButton("Close", null)
                .create();
        holder[0].show();
    }

    private void removeHistoryItem(String url) {
        if (prefs == null) return;
        String old = prefs.getString("history", "");
        List<String> list = new ArrayList<>();
        for (String x : old.split("\n")) {
            if (!x.trim().isEmpty() && !x.equals(url)) list.add(x);
        }
        prefs.edit().putString("history", android.text.TextUtils.join("\n", list)).apply();
    }

    private void confirmClearHistory() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Clear History?")
                .setMessage("This removes the browsing history stored by Champak TV Browser.")
                .setPositiveButton("Clear", (d, w) -> {
                    if (prefs != null) prefs.edit().remove("history").remove("recent_urls").apply();
                    Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String htmlEscape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("'", "&#39;").replace("\"", "&quot;");
    }

    private String shortLabel(String url) {
        try {
            Uri u = Uri.parse(url);
            String h = u.getHost();
            if (h != null) return h.replaceFirst("^www\\.", "");
        } catch (Exception ignored) {}
        return url;
    }

    private void showSearchOverlay() {
        final android.app.AlertDialog dialog;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(18), dp(22), dp(10));

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Search or enter a website");
        input.setTextSize(20);
        input.setImeOptions(EditorInfo.IME_ACTION_GO);
        input.setBackground(roundedBackground(Color.WHITE, Color.rgb(165, 198, 228), 14));
        input.setPadding(dp(16), 0, dp(16), 0);
        box.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        String recent = prefs == null ? "" : prefs.getString("recent_searches", "");
        if (!recent.isEmpty()) {
            TextView title = new TextView(this);
            title.setText("Recent searches");
            title.setTextSize(15);
            title.setTextColor(Color.rgb(90, 110, 130));
            title.setPadding(0, dp(14), 0, dp(6));
            box.addView(title);
            for (String q : recent.split("\n")) {
                if (q.trim().isEmpty()) continue;
                Button b = button(q);
                b.setOnClickListener(v -> input.setText(q));
                box.addView(b);
            }
        }

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this)
                .setTitle("Search")
                .setView(box)
                .setPositiveButton("Go", null)
                .setNeutralButton("Voice", null)
                .setNegativeButton("Close", null);
        dialog = builder.create();
        dialog.setOnShowListener(x -> {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String q = input.getText().toString().trim();
                if (!q.isEmpty()) {
                    recordSearch(q);
                    addressBar.setText(q);
                    loadUrl(q);
                    dialog.dismiss();
                }
            });
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                dialog.dismiss();
                startVoiceSearch();
            });
            input.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_GO ||
                        (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
                    return true;
                }
                return false;
            });
            input.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        });
        dialog.show();
    }

    private void showTabSwitcher() {
        if (tabs.isEmpty()) return;
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), dp(8), dp(12), dp(8));
        final android.app.AlertDialog[] holder = new android.app.AlertDialog[1];

        for (int i = 0; i < tabs.size(); i++) {
            BrowserTab tab = tabs.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            Button open = button((i + 1) + ". " + tab.title);
            open.setOnClickListener(v -> {
                activateTab(tab);
                holder[0].dismiss();
            });

            Button close = button("×");
            close.setTextSize(22);
            close.setOnClickListener(v -> {
                closeTab(tab);
                holder[0].dismiss();
                showTabSwitcher();
            });

            row.addView(open, new LinearLayout.LayoutParams(0, dp(50), 1f));
            row.addView(close, new LinearLayout.LayoutParams(dp(64), dp(50)));
            list.addView(row);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        holder[0] = new android.app.AlertDialog.Builder(this)
                .setTitle("Tabs")
                .setView(scroll)
                .setPositiveButton("+ New Tab", (d, w) -> newTab())
                .setNegativeButton("Close", null)
                .create();
        holder[0].show();
    }

    private void closeTab(BrowserTab tab) {
        if (tab == null) return;
        int index = tabs.indexOf(tab);
        if (index < 0) return;
        webContainer.removeView(tab.webView);
        tabStrip.removeView(tab.tabButton);
        try { tab.webView.clearCache(true); } catch (Exception ignored) {}
        tab.webView.destroy();
        tabs.remove(tab);
        if (tabs.isEmpty()) {
            activeTab = null;
            newTab();
        } else {
            activateTab(tabs.get(Math.max(0, Math.min(index, tabs.size() - 1))));
        }
    }

    private void toggleBookmarkCurrent() {
        if (activeTab == null) return;
        String url = activeTab.webView.getUrl();
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            Toast.makeText(this, "Open a webpage first", Toast.LENGTH_SHORT).show();
            return;
        }
        String old = prefs.getString("bookmarks", "");
        List<String> list = new ArrayList<>();
        boolean removed = false;
        for (String x : old.split("\n")) {
            if (x.equals(url)) removed = true;
            else if (!x.trim().isEmpty()) list.add(x);
        }
        if (!removed) list.add(0, url);
        prefs.edit().putString("bookmarks", android.text.TextUtils.join("\n", list)).apply();
        Toast.makeText(this, removed ? "Bookmark removed" : "Bookmarked", Toast.LENGTH_SHORT).show();
    }

    private void showSettings() {
        String nav = prefs.getBoolean("focus_default", true) ? "Focus" : "Pointer";
        int speed = prefs.getInt("pointer_speed", 28);
        int text = prefs.getInt("text_size", 16);
        boolean remember = prefs.getBoolean("remember_tabs", false);
        boolean clear = prefs.getBoolean("clear_data_exit", false);
        String engine = prefs.getString("search_engine", "Google");

        String[] items = new String[] {
                "Default navigation: " + nav,
                "Pointer speed: " + speed,
                "Text size: " + text,
                "Remember tabs: " + (remember ? "On" : "Off"),
                "Clear site data on exit: " + (clear ? "On" : "Off"),
                "Search engine: " + engine
        };

        new android.app.AlertDialog.Builder(this)
                .setTitle("TV Settings")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0:
                            prefs.edit().putBoolean("focus_default", !prefs.getBoolean("focus_default", true)).apply();
                            break;
                        case 1:
                            int next = speed == 20 ? 28 : (speed == 28 ? 40 : 20);
                            prefs.edit().putInt("pointer_speed", next).apply();
                            pointerSpeed = next;
                            break;
                        case 2:
                            int nextText = text == 16 ? 18 : (text == 18 ? 20 : 16);
                            prefs.edit().putInt("text_size", nextText).apply();
                            for (BrowserTab t : tabs) t.webView.getSettings().setDefaultFontSize(nextText);
                            break;
                        case 3:
                            prefs.edit().putBoolean("remember_tabs", !remember).apply();
                            break;
                        case 4:
                            prefs.edit().putBoolean("clear_data_exit", !clear).apply();
                            break;
                        case 5:
                            String nextEngine = engine.equals("Google") ? "DuckDuckGo"
                                    : (engine.equals("DuckDuckGo") ? "Bing" : "Google");
                            prefs.edit().putString("search_engine", nextEngine).apply();
                            break;
                    }
                    showSettings();
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void restoreTabsOrNew() {
        if (prefs != null && prefs.getBoolean("remember_tabs", false)) {
            String saved = prefs.getString("saved_tabs", "");
            int count = 0;
            for (String url : saved.split("\n")) {
                if (url.trim().isEmpty() || count >= 6) continue;
                newTab();
                loadUrl(url);
                count++;
            }
            if (count > 0) return;
        }
        newTab();
    }

    private void saveTabs() {
        if (prefs == null || !prefs.getBoolean("remember_tabs", false)) return;
        List<String> urls = new ArrayList<>();
        for (BrowserTab tab : tabs) {
            String u = tab.webView.getUrl();
            if (u != null && (u.startsWith("http://") || u.startsWith("https://"))
                    && !u.startsWith("https://start.learnwithchampak.live/")) {
                urls.add(u);
            }
        }
        prefs.edit().putString("saved_tabs", android.text.TextUtils.join("\n", urls)).apply();
    }

    private void showErrorPage(WebView web) {
        String html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>body{font-family:sans-serif;background:#eef7ff;color:#17365d;text-align:center;padding:14vh 8vw}"
                + "h1{font-size:6vh}p{font-size:2.6vh}.box{display:inline-block;background:white;border:2px solid #b8daf6;"
                + "border-radius:20px;padding:4vh 5vw}</style></head><body><div class='box'>"
                + "<h1>Page unavailable</h1><p>Check the connection, then use Reload or TV Menu → Home.</p>"
                + "</div></body></html>";
        web.loadDataWithBaseURL("https://error.learnwithchampak.live/", html, "text/html", "UTF-8", null);
    }

    private void showTransientControls() {
        if (!fullScreen) return;
        transientControls = true;
        topBar.setAlpha(0f);
        tabScroll.setAlpha(0f);
        topBar.setVisibility(View.VISIBLE);
        tabScroll.setVisibility(View.VISIBLE);
        topBar.animate().alpha(1f).setDuration(140).start();
        tabScroll.animate().alpha(1f).setDuration(140).start();
        topBar.postDelayed(() -> {
            if (fullScreen && transientControls) {
                topBar.setVisibility(View.GONE);
                tabScroll.setVisibility(View.GONE);
                transientControls = false;
            }
        }, 3000);
    }

    private void showTvMenu() {
        String navigationLabel = focusMode ? "Switch to Pointer Mode" : "Switch to Focus Mode";
        String[] items = new String[] {
                "Search",
                "Home",
                "Tabs",
                "Bookmarks",
                "History",
                "Voice Search",
                navigationLabel,
                "Add / Remove Bookmark",
                "Full Screen",
                "QR to Phone",
                "New Tab",
                "Close All Tabs",
                "Settings"
        };

        new android.app.AlertDialog.Builder(this)
                .setTitle("TV Menu")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0: showSearchOverlay(); break;
                        case 1:
                            if (activeTab != null) showStartPage(activeTab.webView);
                            break;
                        case 2: showTabSwitcher(); break;
                        case 3: showBookmarks(); break;
                        case 4: showHistory(); break;
                        case 5: startVoiceSearch(); break;
                        case 6: toggleNavigationMode(); break;
                        case 7: toggleBookmarkCurrent(); break;
                        case 8: setFullScreen(true); break;
                        case 9: showQrForCurrentPage(); break;
                        case 10: newTab(); break;
                        case 11: closeAllTabs(); break;
                        case 12: showSettings(); break;
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showStartPage(WebView web) {
        StringBuilder cards = new StringBuilder();
        String last = prefs == null ? "" : prefs.getString("last_url", "");
        if (!last.isEmpty()) {
            cards.append("<a class='tile wide' href='").append(htmlEscape(last)).append("'>Continue: ")
                    .append(htmlEscape(shortLabel(last))).append("</a>");
        }

        cards.append("<a class='tile' href='https://www.google.com'>Google</a>")
                .append("<a class='tile' href='https://www.youtube.com'>YouTube</a>")
                .append("<a class='tile' href='https://www.learnwithchampak.live'>Learn With Champak</a>")
                .append("<a class='tile' href='https://dsa.learnwithchampak.live'>DSA</a>")
                .append("<a class='tile' href='https://aiml.learnwithchampak.live'>AI / ML</a>")
                .append("<a class='tile' href='https://programmer-s-picnic.github.io'>Programmers Picnic</a>");

        String bookmarks = prefs == null ? "" : prefs.getString("bookmarks", "");
        for (String u : bookmarks.split("\n")) {
            if (!u.trim().isEmpty()) {
                cards.append("<a class='tile bookmark' href='").append(htmlEscape(u)).append("'>★ ")
                        .append(htmlEscape(shortLabel(u))).append("</a>");
            }
        }

        String recent = prefs == null ? "" : prefs.getString("recent_urls", "");
        int count = 0;
        for (String u : recent.split("\n")) {
            if (!u.trim().isEmpty() && count < 4) {
                cards.append("<a class='tile recent' href='").append(htmlEscape(u)).append("'>Recent: ")
                        .append(htmlEscape(shortLabel(u))).append("</a>");
                count++;
            }
        }

        String html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>body{font-family:sans-serif;background:linear-gradient(135deg,#edf7ff,#ffffff);color:#17365d;text-align:center;padding:5vh 4vw;margin:0}"
                + "h1{font-size:5.5vh;margin:0 0 1vh}.sub{font-size:2.4vh;color:#56738d;margin-bottom:3vh}"
                + ".grid{display:grid;grid-template-columns:repeat(3,1fr);gap:2vh 1.6vw;max-width:86vw;margin:auto}"
                + ".tile{display:flex;align-items:center;justify-content:center;min-height:10vh;background:#fff;border:2px solid #b8daf6;border-radius:20px;"
                + "font-size:2.4vh;font-weight:700;color:#0b5fae;text-decoration:none;padding:1vh 1vw;box-shadow:0 8px 24px rgba(30,80,130,.09);transition:.14s}"
                + ".wide{grid-column:span 3;background:#eaf5ff}.bookmark{border-color:#f2c45e}.recent{color:#416986}"
                + ".tile:focus{outline:6px solid #ffb300;outline-offset:4px;transform:scale(1.035)}"
                + ".hint{margin-top:2.5vh;font-size:2vh;color:#6a8094}</style></head>"
                + "<body><h1>Champak TV Browser</h1><div class='sub'>Choose a site, or press TV Menu for Search and Voice</div>"
                + "<div class='grid'>" + cards + "</div>"
                + "<div class='hint'>Focus Mode is designed for D-pad navigation • Pointer Mode remains available for difficult pages</div></body></html>";
        web.loadDataWithBaseURL("https://start.learnwithchampak.live/", html, "text/html", "UTF-8", null);
        addressBar.setText("");
    }

    private void toggleNavigationMode() {
        focusMode = !focusMode;
        if (prefs != null) prefs.edit().putBoolean("focus_default", focusMode).apply();
        if (modeButton != null) {
            modeButton.setText(focusMode ? "Focus Mode" : "Pointer Mode");
        }
        if (pointer != null) pointer.setVisibility(focusMode ? View.GONE : View.VISIBLE);

        if (activeTab != null) {
            activeTab.webView.requestFocus();
            installTvFocusMode(focusMode);
        }
        updateModeUi();
        Toast.makeText(this,
                focusMode ? "TV Focus Mode — use D-pad and OK"
                          : "Pointer Mode — move pointer with D-pad",
                Toast.LENGTH_SHORT).show();
    }

    private void installTvFocusMode(boolean enabled) {
        if (activeTab == null) return;

        if (!enabled) {
            activeTab.webView.evaluateJavascript(
                    "(function(){"
                    + "var s=document.getElementById('__champak_tv_focus_style');if(s)s.remove();"
                    + "var old=document.querySelectorAll('.__champak_tv_focused');"
                    + "old.forEach(function(x){x.classList.remove('__champak_tv_focused');});"
                    + "})();", null);
            return;
        }

        String js = "(function(){"
                + "if(!document.getElementById('__champak_tv_focus_style')){"
                + "var s=document.createElement('style');s.id='__champak_tv_focus_style';"
                + "s.textContent='.__champak_tv_focused{outline:6px solid #ffb300 !important;outline-offset:5px !important;"
                + "box-shadow:0 0 0 4px rgba(9,105,218,.55) !important;border-radius:8px !important;"
                + "transform:scale(1.025) !important;transition:transform .12s ease,outline .12s ease !important;}';"
                + "document.head.appendChild(s);}"
                + "window.__champakTvElements=function(){return [...document.querySelectorAll("
                + "'a[href],button,input,select,textarea,[role=button],[onclick],[tabindex]')].filter(function(e){"
                + "var r=e.getBoundingClientRect();var cs=getComputedStyle(e);"
                + "return !e.disabled&&r.width>1&&r.height>1&&cs.visibility!=='hidden'&&cs.display!=='none';});};"
                + "window.__champakTvSetFocus=function(el){"
                + "document.querySelectorAll('.__champak_tv_focused').forEach(function(x){x.classList.remove('__champak_tv_focused');});"
                + "if(!el)return;el.classList.add('__champak_tv_focused');try{el.focus({preventScroll:true});}catch(e){el.focus();}"
                + "el.scrollIntoView({block:'center',inline:'center',behavior:'smooth'});window.__champakTvCurrent=el;};"
                + "var es=window.__champakTvElements();var cur=window.__champakTvCurrent;"
                + "if(!cur||!document.contains(cur)||!es.includes(cur)){window.__champakTvSetFocus(es[0]);}"
                + "})();";
        activeTab.webView.evaluateJavascript(js, null);
    }

    private void moveFocus(int directionKey) {
        if (activeTab == null) return;

        String dir;
        switch (directionKey) {
            case KeyEvent.KEYCODE_DPAD_LEFT: dir = "left"; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: dir = "right"; break;
            case KeyEvent.KEYCODE_DPAD_UP: dir = "up"; break;
            default: dir = "down"; break;
        }

        String js = "(function(){"
                + "if(!window.__champakTvElements||!window.__champakTvSetFocus)return;"
                + "var es=window.__champakTvElements();if(!es.length)return;"
                + "var cur=window.__champakTvCurrent;if(!cur||!document.contains(cur)){window.__champakTvSetFocus(es[0]);return;}"
                + "var r=cur.getBoundingClientRect(),cx=r.left+r.width/2,cy=r.top+r.height/2;"
                + "var best=null,bestScore=1e20,dir='" + dir + "';"
                + "es.forEach(function(e){if(e===cur)return;var q=e.getBoundingClientRect(),x=q.left+q.width/2,y=q.top+q.height/2;"
                + "var dx=x-cx,dy=y-cy,primary=0,secondary=0,ok=false;"
                + "if(dir==='left'&&dx<0){ok=true;primary=-dx;secondary=Math.abs(dy);}"
                + "if(dir==='right'&&dx>0){ok=true;primary=dx;secondary=Math.abs(dy);}"
                + "if(dir==='up'&&dy<0){ok=true;primary=-dy;secondary=Math.abs(dx);}"
                + "if(dir==='down'&&dy>0){ok=true;primary=dy;secondary=Math.abs(dx);}"
                + "if(!ok)return;var score=primary+secondary*2.2;if(score<bestScore){bestScore=score;best=e;}});"
                + "if(best)window.__champakTvSetFocus(best);"
                + "})();";
        activeTab.webView.evaluateJavascript(js, null);
    }

    private void clickFocused() {
        if (activeTab == null) return;
        activeTab.webView.evaluateJavascript(
                "(function(){var e=window.__champakTvCurrent||document.activeElement;"
                + "if(!e||e===document.body)return;"
                + "if(e.tagName==='INPUT'||e.tagName==='TEXTAREA'||e.tagName==='SELECT'){e.focus();e.click();}"
                + "else{e.click();}})();", null);
    }

    private void startVoiceSearch() {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a website or search");
            startActivityForResult(intent, VOICE_REQUEST);
        } catch (Exception e) {
            Toast.makeText(this, "Voice search is not available on this TV", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VOICE_REQUEST && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                addressBar.setText(results.get(0));
                loadUrl(results.get(0));
                hideKeyboardAndClearAddressFocus();
            }
        }
    }

    private void setFullScreen(boolean enabled) {
        fullScreen = enabled;
        transientControls = false;
        if (enabled) {
            if (topBar != null) topBar.animate().alpha(0f).setDuration(140)
                    .withEndAction(() -> topBar.setVisibility(View.GONE)).start();
            if (tabScroll != null) tabScroll.animate().alpha(0f).setDuration(140)
                    .withEndAction(() -> tabScroll.setVisibility(View.GONE)).start();
            Toast.makeText(this, "Full screen — Up shows controls, Back exits", Toast.LENGTH_LONG).show();
        } else {
            if (topBar != null) {
                topBar.setVisibility(View.VISIBLE);
                topBar.setAlpha(0f);
                topBar.animate().alpha(1f).setDuration(140).start();
            }
            if (tabScroll != null) {
                tabScroll.setVisibility(View.VISIBLE);
                tabScroll.setAlpha(0f);
                tabScroll.animate().alpha(1f).setDuration(140).start();
            }
        }
        updateModeUi();
    }

    private void showQrForCurrentPage() {
        if (activeTab == null) return;
        String url = activeTab.webView.getUrl();
        if (url == null || url.startsWith("data:")) {
            Toast.makeText(this, "Open a webpage first", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            int size = dp(320);
            BitMatrix matrix = new MultiFormatWriter().encode(url, BarcodeFormat.QR_CODE, size, size);
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER);
            box.setPadding(dp(20), dp(20), dp(20), dp(20));

            ImageView image = new ImageView(this);
            image.setImageBitmap(bitmap);
            image.setAdjustViewBounds(true);
            box.addView(image, new LinearLayout.LayoutParams(size, size));

            TextView label = new TextView(this);
            label.setText("Scan with your phone\n" + url);
            label.setTextSize(16);
            label.setTextColor(Color.rgb(35, 55, 75));
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, dp(14), 0, 0);
            box.addView(label);

            new android.app.AlertDialog.Builder(this)
                    .setTitle("Open on Phone")
                    .setView(box)
                    .setPositiveButton("Close", null)
                    .show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not create QR code", Toast.LENGTH_SHORT).show();
        }
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
            recordSearch(input);
            String engine = prefs == null ? "Google" : prefs.getString("search_engine", "Google");
            if ("DuckDuckGo".equals(engine)) {
                url = "https://duckduckgo.com/?q=" + Uri.encode(input);
            } else if ("Bing".equals(engine)) {
                url = "https://www.bing.com/search?q=" + Uri.encode(input);
            } else {
                url = "https://www.google.com/search?q=" + Uri.encode(input);
            }
        }
        activeTab.webView.loadUrl(url);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int key = event.getKeyCode();
            View focused = getCurrentFocus();
            boolean onWeb = activeTab != null && focused == activeTab.webView;
            boolean inChrome = focused != null && !onWeb && focused != addressBar;

            if (key == KeyEvent.KEYCODE_BACK) {
                if (addressBar != null && addressBar.hasFocus()) {
                    hideKeyboardAndClearAddressFocus();
                    return true;
                }
                if (fullScreen) {
                    setFullScreen(false);
                    return true;
                }
                if (activeTab != null && activeTab.webView.canGoBack()) {
                    activeTab.webView.goBack();
                    return true;
                }
            }

            if (fullScreen && key == KeyEvent.KEYCODE_DPAD_UP) {
                showTransientControls();
                if (menuButton != null) menuButton.requestFocus();
                return true;
            }

            if (addressBar != null && addressBar.hasFocus()) {
                if (key == KeyEvent.KEYCODE_DPAD_DOWN && activeTab != null) {
                    activeTab.webView.requestFocus();
                    if (focusMode) installTvFocusMode(true);
                    return true;
                }
                if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
                    showSearchOverlay();
                    return true;
                }
                return super.dispatchKeyEvent(event);
            }

            if (inChrome) {
                if (key == KeyEvent.KEYCODE_DPAD_DOWN && activeTab != null) {
                    activeTab.webView.requestFocus();
                    if (focusMode) installTvFocusMode(true);
                    return true;
                }
                return super.dispatchKeyEvent(event);
            }

            if (focusMode) {
                if (key == KeyEvent.KEYCODE_DPAD_UP && activeTab != null
                        && activeTab.webView.getScrollY() <= dp(4)) {
                    if (menuButton != null) menuButton.requestFocus();
                    return true;
                }
                switch (key) {
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                    case KeyEvent.KEYCODE_DPAD_UP:
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        moveFocus(key); return true;
                    case KeyEvent.KEYCODE_DPAD_CENTER:
                    case KeyEvent.KEYCODE_ENTER:
                    case KeyEvent.KEYCODE_BUTTON_A:
                        clickFocused(); return true;
                }
            } else {
                float step = dp(pointerSpeed + Math.min(42, event.getRepeatCount() * 3));
                switch (key) {
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        pointerX -= step; showPointerForMovement(); clampPointer(); updatePointer(); return true;
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        pointerX += step; showPointerForMovement(); clampPointer(); updatePointer(); return true;
                    case KeyEvent.KEYCODE_DPAD_UP:
                        pointerY -= step; showPointerForMovement(); clampPointer(); updatePointer(); return true;
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        pointerY += step; showPointerForMovement(); clampPointer(); updatePointer(); return true;
                    case KeyEvent.KEYCODE_DPAD_CENTER:
                    case KeyEvent.KEYCODE_ENTER:
                    case KeyEvent.KEYCODE_BUTTON_A:
                        clickPointer(); return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void showPointerForMovement() {
        if (pointer == null) return;
        lastPointerMove = System.currentTimeMillis();
        pointer.setVisibility(View.VISIBLE);
        pointer.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(80).start();
        pointer.removeCallbacks(hidePointerRunnable);
        pointer.postDelayed(hidePointerRunnable, 2300);
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
        showPointerForMovement();
        pointer.animate().scaleX(1.28f).scaleY(1.28f).setDuration(70)
                .withEndAction(() -> pointer.animate().scaleX(1f).scaleY(1f).setDuration(90).start()).start();
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
        pointer.removeCallbacks(hidePointerRunnable);
        pointer.postDelayed(hidePointerRunnable, 2300);
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

    private void deleteCacheTree(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteCacheTree(child);
            }
        }
        try { file.delete(); } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        saveTabs();
        for (BrowserTab tab : tabs) {
            try { tab.webView.clearCache(true); } catch (Exception ignored) {}
            tab.webView.destroy();
        }
        deleteCacheTree(getCacheDir());
        try { deleteCacheTree(getCodeCacheDir()); } catch (Exception ignored) {}
        if (prefs != null && prefs.getBoolean("clear_data_exit", false)) {
            try { CookieManager.getInstance().removeAllCookies(null); } catch (Exception ignored) {}
            try { WebStorage.getInstance().deleteAllData(); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }
}
