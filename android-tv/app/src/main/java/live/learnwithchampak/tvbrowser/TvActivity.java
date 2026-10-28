package live.learnwithchampak.tvbrowser;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
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
    private LinearLayout actionBar;
    private HorizontalScrollView tabScroll;
    private Button modeButton;
    private BrowserTab activeTab;
    private boolean focusMode = false;
    private boolean fullScreen = false;
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
        b.setPadding(dp(15), 0, dp(15), 0);
    }

    private void styleSecondary(Button b) {
        b.setTextColor(Color.rgb(18, 55, 105));
        b.setBackground(roundedBackground(Color.WHITE, Color.rgb(188, 214, 238), 12));
        b.setPadding(dp(14), 0, dp(14), 0);
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
        root.setBackgroundColor(Color.rgb(246, 250, 255));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);

        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(8), dp(6), dp(8), dp(6));
        topBar.setBackgroundColor(Color.rgb(243, 249, 255));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.champak_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(48), dp(48));
        logoLp.setMargins(0, 0, dp(4), 0);
        logo.setLayoutParams(logoLp);

        addressBar = new TvEditText(this);
        addressBar.setSingleLine(true);
        addressBar.setHint("Enter address or search");
        addressBar.setTextSize(18);
        addressBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressBar.setPadding(dp(15), 0, dp(15), 0);
        addressBar.setTextColor(Color.rgb(25, 45, 65));
        addressBar.setHintTextColor(Color.rgb(105, 125, 145));
        addressBar.setBackground(roundedBackground(Color.WHITE, Color.rgb(165, 198, 228), 14));
        LinearLayout.LayoutParams addressLp = new LinearLayout.LayoutParams(0, dp(50), 1f);
        addressLp.setMargins(dp(6), 0, dp(6), 0);
        addressBar.setLayoutParams(addressLp);
        addressBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                loadFromAddressBar();
                hideKeyboardAndClearAddressFocus();
                return true;
            }
            return false;
        });

        Button go = button("Go");
        stylePrimary(go);
        go.setOnClickListener(v -> {
            loadFromAddressBar();
            hideKeyboardAndClearAddressFocus();
        });

        Button reload = button("Reload");
        reload.setOnClickListener(v -> {
            if (activeTab != null) activeTab.webView.reload();
        });

        topBar.addView(logo);
        topBar.addView(brandText());
        topBar.addView(addressBar);
        topBar.addView(go);
        topBar.addView(reload);

        actionBar = new LinearLayout(this);
        actionBar.setOrientation(LinearLayout.HORIZONTAL);
        actionBar.setGravity(Gravity.CENTER);
        actionBar.setPadding(dp(8), dp(4), dp(8), dp(4));
        actionBar.setBackgroundColor(Color.rgb(248, 252, 255));

        Button home = button("Home");
        home.setOnClickListener(v -> showStartPage(activeTab.webView));

        modeButton = button("Pointer");
        modeButton.setOnClickListener(v -> toggleNavigationMode());

        Button voice = button("Voice");
        voice.setOnClickListener(v -> startVoiceSearch());

        Button full = button("Full Screen");
        full.setOnClickListener(v -> setFullScreen(true));

        Button qr = button("QR to Phone");
        qr.setOnClickListener(v -> showQrForCurrentPage());

        Button newTab = button("+ New Tab");
        newTab.setOnClickListener(v -> newTab());

        Button closeAll = button("Close All Tabs");
        closeAll.setOnClickListener(v -> closeAllTabs());

        actionBar.addView(home);
        actionBar.addView(modeButton);
        actionBar.addView(voice);
        actionBar.addView(full);
        actionBar.addView(qr);
        actionBar.addView(newTab);
        actionBar.addView(closeAll);

        tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.setBackgroundColor(Color.rgb(235, 246, 255));
        tabStrip = new LinearLayout(this);
        tabStrip.setOrientation(LinearLayout.HORIZONTAL);
        tabStrip.setPadding(dp(8), dp(4), dp(8), dp(4));
        tabScroll.addView(tabStrip, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        webContainer = new FrameLayout(this);
        webContainer.setBackgroundColor(Color.WHITE);
        webContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        page.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(62)));
        page.addView(actionBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));
        page.addView(tabScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
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
                + "<style>body{font-family:sans-serif;background:linear-gradient(135deg,#edf7ff,#ffffff);color:#17365d;text-align:center;padding:6vh 4vw;margin:0}"
                + "h1{font-size:6vh;margin:0 0 1vh}.sub{font-size:2.5vh;color:#56738d;margin-bottom:4vh}"
                + ".grid{display:grid;grid-template-columns:repeat(3,1fr);gap:2.3vh 2vw;max-width:78vw;margin:auto}"
                + ".tile{display:flex;align-items:center;justify-content:center;min-height:12vh;background:#fff;border:2px solid #b8daf6;border-radius:22px;"
                + "font-size:2.7vh;font-weight:700;color:#0b5fae;text-decoration:none;box-shadow:0 8px 24px rgba(30,80,130,.10)}"
                + ".tile:focus{outline:5px solid #0969da;transform:scale(1.04)}"
                + ".hint{margin-top:3vh;font-size:2.1vh;color:#6a8094}</style></head>"
                + "<body><h1>Champak TV Browser</h1><div class='sub'>Choose a favourite or search above</div>"
                + "<div class='grid'>"
                + "<a class='tile' href='https://www.google.com'>Google</a>"
                + "<a class='tile' href='https://www.youtube.com'>YouTube</a>"
                + "<a class='tile' href='https://www.learnwithchampak.live'>Learn With Champak</a>"
                + "<a class='tile' href='https://dsa.learnwithchampak.live'>DSA</a>"
                + "<a class='tile' href='https://aiml.learnwithchampak.live'>AI / ML</a>"
                + "<a class='tile' href='https://programmer-s-picnic.github.io'>Programmers Picnic</a>"
                + "</div><div class='hint'>Pointer mode for free movement • Focus mode for D-pad navigation</div></body></html>";
        web.loadDataWithBaseURL("https://start.learnwithchampak.live/", html, "text/html", "UTF-8", null);
        addressBar.setText("");
    }

    private void toggleNavigationMode() {
        focusMode = !focusMode;
        if (modeButton != null) modeButton.setText(focusMode ? "Focus" : "Pointer");
        if (pointer != null) pointer.setVisibility(focusMode ? View.GONE : View.VISIBLE);
        if (activeTab != null) {
            activeTab.webView.requestFocus();
            if (focusMode) {
                activeTab.webView.evaluateJavascript(
                        "(function(){window.__champakFocus=0;var e=[...document.querySelectorAll('a,button,input,select,textarea,[tabindex]')].filter(x=>!x.disabled&&x.offsetParent!==null);"
                        + "if(e.length){e[0].focus();e[0].scrollIntoView({block:'center'});}})();", null);
            }
        }
        Toast.makeText(this, focusMode ? "Focus mode" : "Pointer mode", Toast.LENGTH_SHORT).show();
    }

    private void moveFocus(int direction) {
        if (activeTab == null) return;
        String js = "(function(){var e=[...document.querySelectorAll('a,button,input,select,textarea,[tabindex]')].filter(x=>!x.disabled&&x.offsetParent!==null);"
                + "if(!e.length)return;window.__champakFocus=(window.__champakFocus||0)+" + direction + ";"
                + "if(window.__champakFocus<0)window.__champakFocus=e.length-1;if(window.__champakFocus>=e.length)window.__champakFocus=0;"
                + "e[window.__champakFocus].focus();e[window.__champakFocus].scrollIntoView({block:'center',behavior:'smooth'});})();";
        activeTab.webView.evaluateJavascript(js, null);
    }

    private void clickFocused() {
        if (activeTab == null) return;
        activeTab.webView.evaluateJavascript(
                "(function(){var e=document.activeElement;if(e&&e!==document.body){e.click();}})();", null);
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
        int visibility = enabled ? View.GONE : View.VISIBLE;
        if (topBar != null) topBar.setVisibility(visibility);
        if (actionBar != null) actionBar.setVisibility(visibility);
        if (tabScroll != null) tabScroll.setVisibility(visibility);
        if (enabled) {
            Toast.makeText(this, "Full screen — press Back to restore controls", Toast.LENGTH_LONG).show();
        }
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
            url = "https://www.google.com/search?q=" + Uri.encode(input);
        }
        activeTab.webView.loadUrl(url);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int key = event.getKeyCode();

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

            if (addressBar != null && addressBar.hasFocus()) {
                return super.dispatchKeyEvent(event);
            }

            if (focusMode) {
                switch (key) {
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                    case KeyEvent.KEYCODE_DPAD_UP:
                        moveFocus(-1); return true;
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        moveFocus(1); return true;
                    case KeyEvent.KEYCODE_DPAD_CENTER:
                    case KeyEvent.KEYCODE_ENTER:
                    case KeyEvent.KEYCODE_BUTTON_A:
                        clickFocused(); return true;
                }
            } else {
                float step = dp(28);
                switch (key) {
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
                        clickPointer(); return true;
                }
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
