package live.learnwithchampak.tvbrowser;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String PREFS = "browser_prefs";
    private static final String KEY_HISTORY = "history";
    private static final String KEY_BOOKMARKS = "bookmarks";
    private static final String KEY_HOME = "home";
    private static final String KEY_DESKTOP = "desktop";

    private LinearLayout toolbar;
    private AutoCompleteTextView addressBar;
    private WebView webView;
    private ProgressBar progressBar;
    private FrameLayout root;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private boolean desktopMode;
    private String homePage;
    private ValueCallback<Uri[]> fileChooserCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        homePage = getPreferencesStore().getString(KEY_HOME, "https://www.google.com/");
        desktopMode = getPreferencesStore().getBoolean(KEY_DESKTOP, true);

        buildUi();
        configureWebView();
        refreshSuggestions();

        Uri incoming = getIntent() != null ? getIntent().getData() : null;
        if (incoming != null) {
            loadInput(incoming.toString());
        } else {
            showStartPage();
        }
    }

    private android.content.SharedPreferences getPreferencesStore() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private Button makeButton(String label, String description) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(18);
        button.setContentDescription(description);
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setMinWidth(dp(54));
        button.setMinHeight(dp(50));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.setMargins(dp(2), 0, dp(2), 0);
        button.setLayoutParams(lp);
        return button;
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        LinearLayout vertical = new LinearLayout(this);
        vertical.setOrientation(LinearLayout.VERTICAL);
        vertical.setBackgroundColor(Color.rgb(13, 27, 42));

        toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(4), dp(3), dp(4), dp(3));
        toolbar.setMinimumHeight(dp(58));

        Button back = makeButton("‹", "Back");
        back.setOnClickListener(v -> {
            if (webView.canGoBack()) webView.goBack();
        });

        Button forward = makeButton("›", "Forward");
        forward.setOnClickListener(v -> {
            if (webView.canGoForward()) webView.goForward();
        });

        Button refresh = makeButton("↻", "Refresh");
        refresh.setOnClickListener(v -> webView.reload());

        Button home = makeButton("⌂", "Home");
        home.setOnClickListener(v -> showStartPage());

        addressBar = new AutoCompleteTextView(this);
        addressBar.setSingleLine(true);
        addressBar.setTextSize(18);
        addressBar.setHint("Search or enter address");
        addressBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressBar.setSelectAllOnFocus(true);
        addressBar.setThreshold(1);
        addressBar.setPadding(dp(12), 0, dp(12), 0);
        addressBar.setBackgroundColor(Color.WHITE);
        addressBar.setTextColor(Color.BLACK);
        addressBar.setHintTextColor(Color.DKGRAY);
        addressBar.setFocusable(true);
        addressBar.setFocusableInTouchMode(true);
        LinearLayout.LayoutParams addressLp = new LinearLayout.LayoutParams(0, dp(50), 1f);
        addressLp.setMargins(dp(4), 0, dp(4), 0);
        addressBar.setLayoutParams(addressLp);
        addressBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                loadInput(addressBar.getText().toString());
                return true;
            }
            return false;
        });
        addressBar.setOnItemClickListener((parent, view, position, id) -> {
            String value = String.valueOf(parent.getItemAtPosition(position));
            loadInput(value);
        });

        Button go = makeButton("Go", "Go");
        go.setOnClickListener(v -> loadInput(addressBar.getText().toString()));

        Button bookmark = makeButton("★", "Bookmark");
        bookmark.setOnClickListener(v -> bookmarkCurrentPage());
        bookmark.setOnLongClickListener(v -> {
            showBookmarks();
            return true;
        });

        Button menu = makeButton("⋮", "Menu");
        menu.setOnClickListener(v -> showMenu());

        toolbar.addView(back);
        toolbar.addView(forward);
        toolbar.addView(refresh);
        toolbar.addView(home);
        toolbar.addView(addressBar);
        toolbar.addView(go);
        toolbar.addView(bookmark);
        toolbar.addView(menu);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        vertical.addView(toolbar);
        vertical.addView(progressBar);
        vertical.addView(webView);
        root.addView(vertical);
        setContentView(root);

        // Good initial focus for TV remotes while still allowing one press to reach the page.
        addressBar.requestFocus();
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        applyUserAgent();

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
                if ("http".equals(scheme) || "https".equals(scheme)) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(MainActivity.this, "No app can open this link.", Toast.LENGTH_SHORT).show();
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                addressBar.setText(url);
                addHistory(url);
                refreshSuggestions();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                if (view.getUrl() != null && !view.getUrl().startsWith("about:")) {
                    addHistory(view.getUrl());
                }
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                toolbar.setVisibility(View.GONE);
                progressBar.setVisibility(View.GONE);
                webView.setVisibility(View.GONE);
                root.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }

            @Override
            public void onHideCustomView() {
                hideCustomView();
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
                fileChooserCallback = filePathCallback;
                try {
                    startActivityForResult(fileChooserParams.createIntent(), 9001);
                    return true;
                } catch (ActivityNotFoundException e) {
                    fileChooserCallback = null;
                    return false;
                }
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) ->
                startDownload(url, userAgent, contentDisposition, mimeType));
    }

    private void applyUserAgent() {
        WebSettings s = webView.getSettings();
        if (desktopMode) {
            s.setUserAgentString("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36");
        } else {
            s.setUserAgentString(null);
        }
    }

    private void loadInput(String raw) {
        String input = raw == null ? "" : raw.trim();
        if (input.isEmpty()) {
            addressBar.requestFocus();
            return;
        }

        String url;
        if (input.matches("(?i)^https?://.+")) {
            url = input;
        } else if (input.matches("(?i)^[a-z0-9.-]+\\.[a-z]{2,}([/:?#].*)?$")) {
            url = "https://" + input;
        } else {
            url = "https://www.google.com/search?q=" + Uri.encode(input);
        }
        webView.loadUrl(url);
        webView.requestFocus();
    }

    private void showStartPage() {
        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>body{font-family:sans-serif;background:#eaf4ff;color:#10243e;text-align:center;margin:0;padding:8vh 5vw}"
                + "h1{font-size:7vh;margin:0 0 5vh}.tiles{display:flex;justify-content:center;gap:3vw;flex-wrap:wrap}"
                + "a{display:block;min-width:22vw;padding:5vh 2vw;background:white;border:4px solid #1976d2;border-radius:18px;"
                + "font-size:4vh;text-decoration:none;color:#0d47a1}p{font-size:2.5vh}</style></head>"
                + "<body><h1>Champak TV Browser</h1><div class='tiles'>"
                + "<a href='https://www.google.com/'>Google</a>"
                + "<a href='https://www.youtube.com/'>YouTube</a>"
                + "<a href='" + escapeHtml(homePage) + "'>Homepage</a></div>"
                + "<p>Press ↑ to reach the address bar. Use the remote D-pad and OK button to browse.</p></body></html>";
        webView.loadDataWithBaseURL("https://start.learnwithchampak.live/", html, "text/html", "UTF-8", null);
        addressBar.setText("");
    }

    private String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("'", "&#39;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void startDownload(String url, String userAgent, String contentDisposition, String mimeType) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            String name = URLUtil.guessFileName(url, contentDisposition, mimeType);
            request.setTitle(name);
            request.setDescription("Downloading from Champak TV Browser");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) request.addRequestHeader("Cookie", cookie);
            if (userAgent != null) request.addRequestHeader("User-Agent", userAgent);
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            manager.enqueue(request);
            Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Download failed", Toast.LENGTH_LONG).show();
        }
    }

    private void bookmarkCurrentPage() {
        String url = webView.getUrl();
        if (url == null || url.startsWith("data:")) return;
        JSONArray items = readArray(KEY_BOOKMARKS);
        try {
            for (int i = 0; i < items.length(); i++) {
                if (url.equals(items.getJSONObject(i).optString("url"))) {
                    Toast.makeText(this, "Already bookmarked", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            JSONObject o = new JSONObject();
            o.put("url", url);
            o.put("title", webView.getTitle() == null ? url : webView.getTitle());
            items.put(o);
            saveArray(KEY_BOOKMARKS, items);
            refreshSuggestions();
            Toast.makeText(this, "Bookmark saved", Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {}
    }

    private void addHistory(String url) {
        if (url == null || url.startsWith("data:") || url.startsWith("about:")) return;
        JSONArray old = readArray(KEY_HISTORY);
        JSONArray next = new JSONArray();
        try {
            JSONObject first = new JSONObject();
            first.put("url", url);
            first.put("title", webView.getTitle() == null ? url : webView.getTitle());
            next.put(first);
            int count = 1;
            for (int i = 0; i < old.length() && count < 50; i++) {
                JSONObject o = old.getJSONObject(i);
                if (!url.equals(o.optString("url"))) {
                    next.put(o);
                    count++;
                }
            }
            saveArray(KEY_HISTORY, next);
        } catch (Exception ignored) {}
    }

    private JSONArray readArray(String key) {
        try {
            return new JSONArray(getPreferencesStore().getString(key, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private void saveArray(String key, JSONArray array) {
        getPreferencesStore().edit().putString(key, array.toString()).apply();
    }

    private void refreshSuggestions() {
        LinkedHashSet<String> suggestions = new LinkedHashSet<>();
        collectUrls(suggestions, readArray(KEY_BOOKMARKS));
        collectUrls(suggestions, readArray(KEY_HISTORY));
        ArrayList<String> list = new ArrayList<>(suggestions);
        addressBar.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, list));
    }

    private void collectUrls(LinkedHashSet<String> target, JSONArray array) {
        try {
            for (int i = 0; i < array.length(); i++) {
                String url = array.getJSONObject(i).optString("url");
                if (!url.isEmpty()) target.add(url);
            }
        } catch (Exception ignored) {}
    }

    private void showBookmarks() {
        JSONArray array = readArray(KEY_BOOKMARKS);
        showStoredPages("Bookmarks", array, true);
    }

    private void showHistory() {
        JSONArray array = readArray(KEY_HISTORY);
        showStoredPages("History", array, false);
    }

    private void showStoredPages(String title, JSONArray array, boolean allowDelete) {
        if (array.length() == 0) {
            Toast.makeText(this, title + " is empty", Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<String> urls = new ArrayList<>();
        try {
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                String t = o.optString("title", o.optString("url"));
                String u = o.optString("url");
                labels.add(t + "\n" + u);
                urls.add(u);
            }
        } catch (Exception ignored) {}

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(labels.toArray(new String[0]), (d, which) -> loadInput(urls.get(which)))
                .setNegativeButton("Close", null)
                .create();

        if (allowDelete) {
            dialog.setOnShowListener(d -> {
                // Long-press deletion is intentionally deferred to keep TV interaction predictable.
            });
        }
        dialog.show();
    }

    private void showMenu() {
        String[] items = {
                "Bookmarks",
                "History",
                "Downloads",
                desktopMode ? "Switch to Mobile mode" : "Switch to Desktop mode",
                "Zoom in",
                "Zoom out",
                "Reset zoom",
                "Set homepage",
                "Clear history",
                "Clear cookies & site data",
                "Android WebView settings",
                "About"
        };
        new AlertDialog.Builder(this)
                .setTitle("Browser menu")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0: showBookmarks(); break;
                        case 1: showHistory(); break;
                        case 2:
                            try { startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)); }
                            catch (Exception e) { Toast.makeText(this, "Downloads screen unavailable", Toast.LENGTH_SHORT).show(); }
                            break;
                        case 3:
                            desktopMode = !desktopMode;
                            getPreferencesStore().edit().putBoolean(KEY_DESKTOP, desktopMode).apply();
                            applyUserAgent();
                            webView.reload();
                            break;
                        case 4: webView.zoomIn(); break;
                        case 5: webView.zoomOut(); break;
                        case 6: webView.setInitialScale(0); webView.reload(); break;
                        case 7: editHomepage(); break;
                        case 8:
                            saveArray(KEY_HISTORY, new JSONArray());
                            refreshSuggestions();
                            Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
                            break;
                        case 9:
                            webView.clearCache(true);
                            webView.clearFormData();
                            CookieManager.getInstance().removeAllCookies(null);
                            CookieManager.getInstance().flush();
                            Toast.makeText(this, "Site data cleared", Toast.LENGTH_SHORT).show();
                            break;
                        case 10:
                            try {
                                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:" + getPackageName()));
                                startActivity(i);
                            } catch (Exception ignored) {}
                            break;
                        case 11:
                            new AlertDialog.Builder(this)
                                    .setTitle("Champak TV Browser")
                                    .setMessage("Version 1.0.0\n\nA lightweight Android TV browser by Learn With Champak.\n\nRemote-first, single-tab first release.")
                                    .setPositiveButton("OK", null)
                                    .show();
                            break;
                    }
                })
                .show();
    }

    private void editHomepage() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(homePage);
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle("Homepage")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String value = input.getText().toString().trim();
                    if (!value.isEmpty()) {
                        if (!value.matches("(?i)^https?://.+")) value = "https://" + value;
                        homePage = value;
                        getPreferencesStore().edit().putString(KEY_HOME, value).apply();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void hideCustomView() {
        if (customView == null) return;
        root.removeView(customView);
        customView = null;
        toolbar.setVisibility(View.VISIBLE);
        webView.setVisibility(View.VISIBLE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (customViewCallback != null) customViewCallback.onCustomViewHidden();
        customViewCallback = null;
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            hideCustomView();
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 9001) {
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) results[i] = data.getClipData().getItemAt(i).getUri();
                } else if (data.getData() != null) {
                    results = new Uri[]{data.getData()};
                }
            }
            if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(results);
            fileChooserCallback = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }
}
