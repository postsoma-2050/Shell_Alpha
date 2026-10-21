package com.cylonid.nativealpha.browser;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;
import com.cylonid.nativealpha.R;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.json.JSONObject;

/** On-demand reader: extract a cloned DOM; preserve the original WebView and its scroll/session. */
public final class ReaderModeController {
    private final Context context;
    private final FrameLayout content;
    private final View controls;
    private final Runnable layoutChanged;
    private final Runnable beforeViewChange;
    private final Consumer<String> navigate;
    private final SharedPreferences preferences;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService parser = Executors.newSingleThreadExecutor();
    private WebView original;
    private WebView reader;
    private ReaderTranslationPanel translationPanel;
    private com.google.android.material.button.MaterialButton translationAction;
    private boolean translationEnabled;
    private ReaderDocument document;
    private boolean loading;
    private boolean destroyed;
    private long generation;
    private int theme;
    private int fontSize;
    private float scrollFraction;
    private int originalScrollX;
    private int originalScrollY;
    private String originalUrl;
    private final Runnable timeout = () -> { if (loading) { close(); unavailable(); } };

    public ReaderModeController(FrameLayout content, View controls, Runnable layoutChanged, Runnable beforeViewChange, Consumer<String> navigate) {
        this.content = content;
        this.context = content.getContext();
        this.controls = controls;
        this.layoutChanged = layoutChanged;
        this.beforeViewChange = beforeViewChange;
        this.navigate = navigate;
        preferences = context.getSharedPreferences("reader_presentation", Context.MODE_PRIVATE);
        int defaultTheme = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES ? 2 : 0;
        theme = Math.max(0, Math.min(3, preferences.getInt("theme", defaultTheme)));
        fontSize = Math.max(16, Math.min(32, preferences.getInt("fontSize", 20)));
        translationEnabled = preferences.getBoolean("translationEnabled", true);
        controls.findViewById(R.id.btnReaderTranslation).setOnClickListener(v -> toggleTranslation());
        controls.findViewById(R.id.btnReaderClose).setOnClickListener(v -> close());
        controls.findViewById(R.id.btnReaderSmaller).setOnClickListener(v -> resize(-2));
        controls.findViewById(R.id.btnReaderLarger).setOnClickListener(v -> resize(2));
        controls.findViewById(R.id.btnReaderTheme).setOnClickListener(v -> cycleTheme());
    }

    public int getTheme() { return theme; }

    public void setTheme(int newTheme) {
        theme = Math.max(0, Math.min(3, newTheme));
        preferences.edit().putInt("theme", theme).apply();
        render();
    }

    public void cycleTheme() {
        setTheme((theme + 1) % 4);
    }

    public int getFontSize() { return fontSize; }

    public void setFontSize(int newFontSize) {
        fontSize = Math.max(16, Math.min(32, newFontSize));
        preferences.edit().putInt("fontSize", fontSize).apply();
        render();
    }

    public void resize(int delta) {
        setFontSize(fontSize + delta);
    }

    public boolean isTranslationEnabled() { return translationEnabled; }

    public void toggleTranslation() {
        setTranslationEnabled(!translationEnabled);
    }

    public void setTranslationEnabled(boolean enabled) {
        translationEnabled = enabled;
        preferences.edit().putBoolean("translationEnabled", translationEnabled).apply();
        if (reader instanceof ReaderWebView) ((ReaderWebView) reader).setTranslationEnabled(translationEnabled);
        closeTranslation();
        updateTranslationControl();
        if (translationEnabled) Toast.makeText(context, R.string.reader_translation_selection_hint, Toast.LENGTH_LONG).show();
    }

    public boolean isOpen() { return reader != null; }
    public boolean isActive() { return isOpen() || loading; }
    public WebView getWebView() { return reader; }

    public void open(WebView source) {
        if (destroyed || isActive()) return;
        if (!source.getSettings().getJavaScriptEnabled()) {
            Toast.makeText(context, R.string.browser_reader_requires_js, Toast.LENGTH_LONG).show();
            return;
        }
        final String url = source.getUrl();
        if (BrowserOriginPolicy.origin(url) == null) { unavailable(); return; }
        final String script;
        try {
            script = readAsset("reader/Readability.js") + "\n" + readAsset("reader/ExtractArticle.js");
        } catch (Exception e) { unavailable(); return; }
        original = source;
        originalScrollX = source.getScrollX();
        originalScrollY = source.getScrollY();
        originalUrl = url;
        loading = true;
        final long request = ++generation;
        handler.postDelayed(timeout, 12000);
        Toast.makeText(context, R.string.browser_reader_loading, Toast.LENGTH_SHORT).show();
        source.evaluateJavascript("(function(){try{" + script + "\n"
                + "var article=shellAlphaExtractArticle(document," + JSONObject.quote(url) + ");"
                + "if(!article||!article.content||article.content.length>350000)return null;"
                + "return JSON.stringify({title:article.title,byline:article.byline||'',dir:article.dir||'',content:article.content});"
                + "}catch(e){return null;}})()", value -> {
            if (request != generation || original != source || !loading) return;
            parser.execute(() -> {
                ReaderDocument result = null;
                try { result = ReaderDocument.fromJavascript(value, url); } catch (Exception ignored) {}
                final ReaderDocument parsed = result;
                handler.post(() -> {
                    if (request != generation || original != source || !loading) return;
                    handler.removeCallbacks(timeout);
                    loading = false;
                    if (parsed == null || !url.equals(source.getUrl())) { original = null; unavailable(); return; }
                    document = parsed;
                    show();
                });
            });
        });
    }

    private String readAsset(String name) throws Exception {
        try (InputStream asset = context.getAssets().open(name)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int length;
            while ((length = asset.read(chunk)) != -1) buffer.write(chunk, 0, length);
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private void unavailable() {
        Toast.makeText(context, R.string.browser_reader_unavailable, Toast.LENGTH_LONG).show();
    }

    private void show() {
        ((android.view.inputmethod.InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(content.getWindowToken(), 0);
        reader = new ReaderWebView(context, text -> {
            if (!translationEnabled || reader == null || destroyed) return;
            closeTranslation();
            translationPanel = new ReaderTranslationPanel(content, theme);
            translationPanel.show(text);
        });
        reader.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) closeTranslation();
            return false;
        });
        ((ReaderWebView) reader).setTranslationEnabled(translationEnabled);
        translationAction = new com.google.android.material.button.MaterialButton(context);
        translationAction.setId(R.id.readerTranslateSelection);
        translationAction.setText(R.string.reader_translate_selection);
        translationAction.setIconResource(R.drawable.ic_reader_translate);
        translationAction.setFocusable(false);
        translationAction.setVisibility(View.GONE);
        translationAction.setOnClickListener(v -> {
            if (reader instanceof ReaderWebView) ((ReaderWebView) reader).translateSelection();
        });
        ((ReaderWebView) reader).setSelectionListener(available -> {
            if (translationAction != null) translationAction.setVisibility(available ? View.VISIBLE : View.GONE);
        });
        reader.getSettings().setBuiltInZoomControls(true);
        reader.getSettings().setDisplayZoomControls(false);
        reader.getSettings().setTextZoom(Math.round(context.getResources().getConfiguration().fontScale * 100));
        reader.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                view.postVisualStateCallback(0, new WebView.VisualStateCallback() {
                    @Override public void onComplete(long id) {
                        if (view != reader) return;
                        int range = Math.max(0, (int) (view.getContentHeight() * view.getScale()) - view.getHeight());
                        view.scrollTo(0, Math.round(range * scrollFraction));
                    }
                });
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (request.isForMainFrame() && BrowserOriginPolicy.origin(url) != null) {
                    close();
                    navigate.accept(url);
                }
                return true;
            }
        });
        original.setVisibility(View.GONE);
        original.onPause();
        content.addView(reader, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams actionLayout = new FrameLayout.LayoutParams(-2, -2,
                android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
        actionLayout.bottomMargin = Math.round(16 * context.getResources().getDisplayMetrics().density);
        content.addView(translationAction, actionLayout);
        controls.setVisibility(View.VISIBLE);
        updateTranslationControl();
        layoutChanged.run();
        render();
    }

    private void render() {
        if (reader == null || document == null) return;
        closeTranslation();
        ((ReaderWebView) reader).clearSelectionAction();
        beforeViewChange.run();
        ReaderAppearance appearance = ReaderAppearance.of(theme);
        reader.setBackgroundColor(appearance.paper);
        controls.setBackgroundColor(appearance.paper);
        controls.setElevation(2 * context.getResources().getDisplayMetrics().density);
        ((android.widget.TextView) controls.findViewById(R.id.txtReaderModeTitle)).setTextColor(appearance.ink);
        for (int id : new int[]{R.id.btnReaderTranslation, R.id.btnReaderSmaller, R.id.btnReaderLarger,
                R.id.btnReaderTheme, R.id.btnReaderClose}) {
            com.google.android.material.button.MaterialButton button = controls.findViewById(id);
            appearance.styleAction(button);
            button.setTextColor(appearance.muted);
            button.setIconTint(android.content.res.ColorStateList.valueOf(appearance.muted));
        }
        appearance.styleAction(translationAction);
        translationAction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(appearance.surface));
        translationAction.setCornerRadius(Math.round(24 * context.getResources().getDisplayMetrics().density));
        translationAction.setStrokeColor(android.content.res.ColorStateList.valueOf(appearance.line));
        translationAction.setStrokeWidth(Math.max(1, Math.round(context.getResources().getDisplayMetrics().density)));
        translationAction.setElevation(6 * context.getResources().getDisplayMetrics().density);
        updateTranslationControl();
        float range = reader.getContentHeight() * reader.getScale() - reader.getHeight();
        if (reader.getProgress() == 100) scrollFraction = range > 0 ? Math.min(1f, reader.getScrollY() / range) : 0;
        controls.findViewById(R.id.btnReaderSmaller).setEnabled(fontSize > 16);
        controls.findViewById(R.id.btnReaderLarger).setEnabled(fontSize < 32);
        int[] names = {R.string.browser_reader_theme_light, R.string.browser_reader_theme_sepia,
                R.string.browser_reader_theme_dark, R.string.browser_reader_theme_black};
        controls.findViewById(R.id.btnReaderTheme).setContentDescription(context.getString(R.string.browser_reader_theme)
                + ": " + context.getString(names[theme]));
        reader.loadDataWithBaseURL("https://reader.shellalpha.invalid/", document.html(theme, fontSize), "text/html", "UTF-8", "https://reader.shellalpha.invalid/");
    }

    public void close() {
        closeTranslation();
        boolean restoreScroll = reader != null && !destroyed;
        generation++;
        loading = false;
        handler.removeCallbacks(timeout);
        if (reader != null) {
            ((ReaderWebView) reader).deactivate();
            beforeViewChange.run();
            content.removeView(reader);
            reader.stopLoading();
            reader.setWebViewClient(null);
            reader.destroy();
            reader = null;
        }
        if (translationAction != null) content.removeView(translationAction);
        translationAction = null;
        if (original != null) {
            WebView source = original;
            final long closedGeneration = generation;
            final int x = originalScrollX;
            final int y = originalScrollY;
            final String url = originalUrl;
            if (!destroyed) {
                source.setVisibility(View.VISIBLE);
                source.onResume();
            }
            // Restore after the reader bar has been removed and the website has laid out again.
            if (restoreScroll) source.postOnAnimation(() -> source.postOnAnimation(() -> {
                if (generation == closedGeneration && reader == null && source.isAttachedToWindow()
                        && url != null && url.equals(source.getUrl())) {
                    source.scrollTo(x, y);
                }
            }));
            original = null;
        }
        document = null;
        if (controls.getVisibility() != View.GONE) {
            controls.setVisibility(View.GONE);
            layoutChanged.run();
        }
    }

    private void closeTranslation() {
        if (translationPanel != null) translationPanel.close();
        translationPanel = null;
    }

    public boolean dismissTranslation() {
        if (translationPanel == null || !translationPanel.isShowing()) return false;
        closeTranslation();
        return true;
    }

    private void updateTranslationControl() {
        View button = controls.findViewById(R.id.btnReaderTranslation);
        button.setSelected(translationEnabled);
        button.setAlpha(translationEnabled ? 1f : .5f);
        if (button instanceof com.google.android.material.button.MaterialButton)
            ((com.google.android.material.button.MaterialButton) button).setIconTint(android.content.res.ColorStateList.valueOf(
                    translationEnabled ? ReaderAppearance.of(theme).accent : ReaderAppearance.of(theme).muted));
        button.setContentDescription(context.getString(translationEnabled
                ? R.string.reader_translation_enabled : R.string.reader_translation_disabled));
    }

    public void destroy() {
        destroyed = true;
        close();
        parser.shutdownNow();
    }
}
