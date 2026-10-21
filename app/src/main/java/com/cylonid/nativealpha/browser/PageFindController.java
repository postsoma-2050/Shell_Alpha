package com.cylonid.nativealpha.browser;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.TextView;

import com.cylonid.nativealpha.R;

/** Owns native WebView search only while the find toolbar is visible. */
public final class PageFindController {
    private final View bar;
    private final EditText query;
    private final TextView count;
    private final View previous;
    private final View next;
    private final Runnable layoutChanged;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView target;
    private Runnable pending;
    private long generation;
    private int matches;

    public PageFindController(View bar, Runnable layoutChanged) {
        this.bar = bar;
        this.layoutChanged = layoutChanged;
        query = bar.findViewById(R.id.findPageQuery);
        count = bar.findViewById(R.id.findPageCount);
        previous = bar.findViewById(R.id.btnFindPrevious);
        next = bar.findViewById(R.id.btnFindNext);
        previous.setOnClickListener(v -> next(false));
        next.setOnClickListener(v -> next(true));
        bar.findViewById(R.id.btnFindClose).setOnClickListener(v -> close());
        query.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int length, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int length) { search(); }
            public void afterTextChanged(Editable text) {}
        });
        query.setOnEditorActionListener((v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH) return false;
            if (pending != null) {
                Runnable task = pending;
                handler.removeCallbacks(task);
                task.run();
            } else next(true);
            keyboard().hideSoftInputFromWindow(query.getWindowToken(), 0);
            return true;
        });
    }

    public boolean isOpen() { return target != null && bar.getVisibility() == View.VISIBLE; }

    public void open(WebView webView) {
        if (target != webView) close();
        target = webView;
        bar.setVisibility(View.VISIBLE);
        layoutChanged.run();
        search();
        query.requestFocus();
        query.selectAll();
        query.post(() -> {
            if (isOpen() && query.hasFocus()) keyboard().showSoftInput(query, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private InputMethodManager keyboard() {
        return (InputMethodManager) bar.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    }

    private void search() {
        generation++;
        if (pending != null) handler.removeCallbacks(pending);
        pending = null;
        matches = 0;
        updateNavigation(false);
        count.setText("");
        count.setContentDescription(null);
        if (target == null) return;
        target.setFindListener(null);
        target.clearMatches();
        String text = query.getText().toString();
        if (text.isEmpty()) return;
        final WebView view = target;
        final long request = generation;
        pending = () -> {
            pending = null;
            if (target != view || generation != request) return;
            view.setFindListener((active, total, done) -> {
                if (target != view || generation != request || !done) return;
                matches = total;
                count.setText(total == 0 ? bar.getContext().getString(R.string.browser_find_no_matches)
                        : bar.getContext().getString(R.string.browser_find_count, active + 1, total));
                count.setContentDescription(total == 0
                        ? bar.getContext().getString(R.string.browser_find_no_matches_description) : null);
                updateNavigation(total > 0);
            });
            view.findAllAsync(text);
        };
        handler.postDelayed(pending, 180);
    }

    private void next(boolean forward) {
        if (target != null && matches > 0) target.findNext(forward);
    }

    private void updateNavigation(boolean enabled) {
        previous.setEnabled(enabled);
        next.setEnabled(enabled);
        previous.setAlpha(enabled ? 1f : 0.38f);
        next.setAlpha(enabled ? 1f : 0.38f);
    }

    public void close() {
        boolean wasOpen = isOpen();
        generation++;
        if (pending != null) handler.removeCallbacks(pending);
        pending = null;
        if (target != null) {
            target.setFindListener(null);
            target.clearMatches();
        }
        target = null;
        bar.setVisibility(View.GONE);
        query.setText("");
        if (wasOpen) {
            keyboard().hideSoftInputFromWindow(query.getWindowToken(), 0);
            query.clearFocus();
            layoutChanged.run();
        }
    }
}
