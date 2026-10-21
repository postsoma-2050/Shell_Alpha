package com.cylonid.nativealpha.browser;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.Context;
import android.net.Uri;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.view.Gravity;
import android.view.ViewGroup;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import com.google.android.material.button.MaterialButton;
import com.cylonid.nativealpha.R;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Contextual Reader panel in the existing window: no modal scrim or separate system bars. */
public final class ReaderTranslationPanel {
    private final Context context;
    private final FrameLayout host;
    private final ReaderAppearance appearance;
    private boolean closed;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ReaderTranslation.Request request;
    private Future<?> work;
    private ReaderTranslationLayout panel;
    private MaterialButton copy;
    private TextView result;
    private String text;
    private String language;
    private String translated;
    private long generation;
    private SharedPreferences preferences;
    private final Runnable timeout = () -> {
        cancelRequest();
        showResult(null, R.string.reader_translation_timeout);
    };

    private final View.OnLayoutChangeListener layoutListener = (v, l, t, r, b, ol, ot, or, ob) -> {
        if (r - l != or - ol || b - t != ob - ot) updateBounds();
    };

    public ReaderTranslationPanel(FrameLayout host, int theme) {
        this.host = host;
        this.context = host.getContext();
        this.appearance = ReaderAppearance.of(theme);
    }

    public void show(CharSequence selection) {
        if (closed) return;
        if (selection == null
                || selection.length() > ReaderTranslation.MAX_TEXT || selection.toString().trim().isEmpty()) {
            Toast.makeText(context, R.string.reader_translation_invalid, Toast.LENGTH_LONG).show();
            close();
            return;
        }
        buildContent(selection.toString().trim());
        host.addView(panel, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        host.addOnLayoutChangeListener(layoutListener);
        updateBounds();
        ViewCompat.setAccessibilityPaneTitle(panel, context.getString(R.string.reader_translation_heading));
        translate();
    }

    private void buildContent(String selection) {
        text = selection;
        preferences = context.getSharedPreferences("reader_presentation", Context.MODE_PRIVATE);
        language = preferences.getString("translationLanguage", "zh-CN");
        if (!ReaderTranslation.LANGUAGES.contains(language)) language = "zh-CN";
        panel = (ReaderTranslationLayout) android.view.LayoutInflater.from(context).inflate(R.layout.reader_translation, host, false);
        View content = panel;
        GradientDrawable background = new GradientDrawable();
        background.setColor(appearance.surface);
        background.setCornerRadius(dp(16));
        background.setStroke(dp(1), appearance.line);
        panel.setBackground(background);
        panel.setClipToOutline(true);
        ((TextView) content.findViewById(R.id.readerTranslationSource)).setText(text);
        result = content.findViewById(R.id.readerTranslationResult);
        for (int id : new int[]{R.id.readerTranslationSource, R.id.readerTranslationLanguageLabel,
                R.id.readerTranslationAttribution}) ((TextView) content.findViewById(id)).setTextColor(appearance.muted);
        ((TextView) content.findViewById(R.id.readerTranslationHeading)).setTextColor(appearance.ink);
        result.setTextColor(appearance.ink);
        content.findViewById(R.id.readerTranslationDivider).setBackgroundColor(appearance.line);
        ((ProgressBar) content.findViewById(R.id.readerTranslationProgress))
                .setIndeterminateTintList(ColorStateList.valueOf(appearance.accent));
        for (int id : new int[]{R.id.readerTranslationCopy, R.id.readerTranslationClose,
                R.id.readerTranslationRetry, R.id.readerTranslationOpenGoogle, R.id.readerTranslationExpandSource})
            appearance.styleAction(content.findViewById(id));
        content.findViewById(R.id.readerTranslationClose).setOnClickListener(v -> close());
        content.findViewById(R.id.readerTranslationRetry).setOnClickListener(v -> translate());
        TextView source = content.findViewById(R.id.readerTranslationSource);
        MaterialButton expand = content.findViewById(R.id.readerTranslationExpandSource);
        expand.setVisibility(text.length() > 140 ? View.VISIBLE : View.GONE);
        expand.setOnClickListener(v -> {
            boolean expanded = source.getMaxLines() == Integer.MAX_VALUE;
            source.setMaxLines(expanded ? 3 : Integer.MAX_VALUE);
            source.setEllipsize(expanded ? android.text.TextUtils.TruncateAt.END : null);
            expand.setText(expanded ? R.string.reader_translation_show_original : R.string.reader_translation_hide_original);
        });
        copy = content.findViewById(R.id.readerTranslationCopy);
        copy.setOnClickListener(v -> {
            if (translated == null) return;
            context.getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText(
                    context.getString(R.string.reader_translation_title), translated));
            Toast.makeText(context, R.string.reader_translation_copied, Toast.LENGTH_SHORT).show();
        });
        content.findViewById(R.id.readerTranslationOpenGoogle).setOnClickListener(v -> {
            try {
                context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(ReaderTranslation.browserUrl(text, language)))
                        .addCategory(Intent.CATEGORY_BROWSABLE));
            } catch (android.content.ActivityNotFoundException error) {
                Toast.makeText(context, R.string.browser_external_internal_fallback, Toast.LENGTH_LONG).show();
            }
        });
        Spinner languages = content.findViewById(R.id.readerTranslationLanguage);
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(context,
                android.R.layout.simple_spinner_item, context.getResources().getTextArray(R.array.reader_translation_languages)) {
            private View style(View view, boolean dropdown) {
                if (view instanceof TextView) {
                    TextView label = (TextView) view;
                    label.setTextColor(appearance.ink);
                    label.setMinHeight(dp(48));
                    label.setGravity(Gravity.CENTER_VERTICAL);
                    if (dropdown) {
                        label.setPaddingRelative(dp(16), dp(10), dp(16), dp(10));
                    }
                }
                return view;
            }
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                return style(super.getView(position, convertView, parent), false);
            }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                return style(super.getDropDownView(position, convertView, parent), true);
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        languages.setAdapter(adapter);
        languages.setBackgroundTintList(ColorStateList.valueOf(appearance.muted));
        GradientDrawable popupBg = new GradientDrawable();
        popupBg.setColor(appearance.surface);
        popupBg.setCornerRadius(dp(8));
        popupBg.setStroke(dp(1), appearance.line);
        languages.setPopupBackgroundDrawable(popupBg);
        languages.setSelection(ReaderTranslation.LANGUAGES.indexOf(language));
        languages.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(AdapterView<?> parent) { }
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String chosen = ReaderTranslation.LANGUAGES.get(position);
                if (!chosen.equals(language)) {
                    language = chosen;
                    preferences.edit().putString("translationLanguage", language).apply();
                    translate();
                }
            }
        });
    }

    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }

    private void updateBounds() {
        if (panel == null || panel.getParent() != host) return;
        WindowInsetsCompat windowInsets = ViewCompat.getRootWindowInsets(host);
        Insets safe = windowInsets == null ? Insets.NONE : windowInsets.getInsets(
                WindowInsetsCompat.Type.systemGestures() | WindowInsetsCompat.Type.displayCutout());
        FrameLayout.LayoutParams bounds = (FrameLayout.LayoutParams) panel.getLayoutParams();
        int width = host.getWidth() > 0 ? host.getWidth() : context.getResources().getDisplayMetrics().widthPixels;
        bounds.width = Math.max(0, Math.min(dp(600), width - dp(24) - safe.left - safe.right));
        bounds.leftMargin = dp(12) + safe.left;
        bounds.rightMargin = dp(12) + safe.right;
        bounds.bottomMargin = dp(12) + safe.bottom;
        panel.setAvailableHeight(Math.max(0, host.getHeight() - bounds.bottomMargin));
        panel.setLayoutParams(bounds);
    }

    public boolean isShowing() { return !closed && panel != null && panel.getParent() == host; }

    private void translate() {
        if (!isShowing()) return;
        cancelRequest();
        translated = null;
        result.setText(R.string.reader_translation_loading);
        result.setTextColor(appearance.muted);
        panel.findViewById(R.id.readerTranslationProgress).setVisibility(View.VISIBLE);
        panel.findViewById(R.id.readerTranslationErrorActions).setVisibility(View.GONE);
        copy.setEnabled(false);
        final long token = generation;
        final String target = language;
        final ReaderTranslation.Request operation = new ReaderTranslation.Request(context);
        request = operation;
        handler.postDelayed(timeout, 9000);
        work = worker.submit(() -> {
            String response = null;
            int failure = R.string.reader_translation_failed;
            try { response = operation.execute(text, target); } catch (Exception error) {
                // Never log selected text, request URLs, or provider response bodies.
                String reason = error.getClass().getSimpleName();
                if (error instanceof ReaderTranslation.ProviderException) {
                    reason = error.getMessage();
                    failure = R.string.reader_translation_provider_rejected;
                } else if (error instanceof java.net.SocketTimeoutException) {
                    failure = R.string.reader_translation_timeout;
                } else if (error instanceof org.json.JSONException || error instanceof IllegalArgumentException) {
                    failure = R.string.reader_translation_bad_response;
                }
                android.util.Log.w("ReaderTranslation", "Translation failed: " + reason);
            }
            final String value = response;
            final int failureMessage = failure;
            handler.post(() -> {
                if (token != generation || closed) return;
                handler.removeCallbacks(timeout);
                request = null;
                showResult(value, failureMessage);
            });
        });
    }

    private void showResult(String value, int failureMessage) {
        if (!isShowing()) return;
        translated = value;
        result.setText(value == null ? context.getString(failureMessage) : value);
        result.setTextColor(value == null ? appearance.error : appearance.ink);
        panel.findViewById(R.id.readerTranslationProgress).setVisibility(View.GONE);
        panel.findViewById(R.id.readerTranslationErrorActions).setVisibility(value == null ? View.VISIBLE : View.GONE);
        copy.setEnabled(value != null);
    }

    private void cancelRequest() {
        generation++;
        handler.removeCallbacks(timeout);
        if (request != null) request.cancel();
        if (work != null) work.cancel(true);
        request = null;
        work = null;
    }

    public void close() {
        if (closed) return;
        closed = true;
        cancelRequest();
        worker.shutdownNow();
        handler.removeCallbacksAndMessages(null);
        host.removeOnLayoutChangeListener(layoutListener);
        if (panel != null) host.removeView(panel);
    }
}
