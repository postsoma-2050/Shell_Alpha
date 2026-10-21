package com.cylonid.nativealpha.browser;

import android.content.Context;
import android.graphics.Rect;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.widget.Toast;
import com.cylonid.nativealpha.R;
import org.json.JSONTokener;
import java.util.function.Consumer;

/** Selection actions exist only on the sanitized, local Reader document. */
public final class ReaderWebView extends WebView {
    private final Consumer<String> translate;
    private boolean translationEnabled = true;
    private boolean active = true;
    private long generation;
    private ActionMode selectionMode;
    private String selectedText;
    private Consumer<Boolean> selectionChanged = available -> { };

    public ReaderWebView(Context context, Consumer<String> translate) {
        super(context);
        this.translate = translate;
        // Only sanitized ReaderDocument HTML is loaded. Its CSP forbids page
        // scripts; host evaluation reads a selection after an explicit tap.
        getSettings().setJavaScriptEnabled(true);
        getSettings().setAllowFileAccess(false);
        getSettings().setAllowContentAccess(false);
        getSettings().setBlockNetworkLoads(true);
    }

    public void setTranslationEnabled(boolean enabled) {
        translationEnabled = enabled;
        generation++;
        selectedText = null;
        selectionChanged.accept(false);
        if (selectionMode != null) selectionMode.invalidate();
    }

    public void setSelectionListener(Consumer<Boolean> listener) { selectionChanged = listener; }

    private static String decodeSelection(String value) {
        try {
            Object decoded = new JSONTokener(value).nextValue();
            if (decoded instanceof String) {
                String text = ((String) decoded).trim();
                if (!text.isEmpty() && text.length() <= ReaderTranslation.MAX_TEXT) return text;
            }
        } catch (Exception ignored) { }
        return null;
    }

    private void inspectSelection(ActionMode mode) {
        final long request = generation;
        postDelayed(() -> {
            if (!active || !translationEnabled || mode != selectionMode || request != generation) return;
            evaluateJavascript("String(window.getSelection ? window.getSelection() : '').slice(0,2001)", value -> {
                if (!active || !translationEnabled || mode != selectionMode || request != generation) return;
                selectedText = decodeSelection(value);
                selectionChanged.accept(selectedText != null);
            });
        }, 150);
    }

    public void translateSelection() {
        if (!active || !translationEnabled) return;
        if (selectedText != null && selectionMode == null) {
            String text = selectedText;
            clearSelectionAction();
            translate.accept(text);
        } else readSelection(selectionMode);
    }

    public void clearSelectionAction() {
        generation++;
        selectedText = null;
        selectionChanged.accept(false);
        if (selectionMode != null) selectionMode.finish();
    }

    public void deactivate() {
        active = false;
        clearSelectionAction();
    }

    @Override public ActionMode startActionMode(ActionMode.Callback callback) {
        return super.startActionMode(selectionCallback(callback));
    }

    @Override public ActionMode startActionMode(ActionMode.Callback callback, int type) {
        return super.startActionMode(selectionCallback(callback), type);
    }

    ActionMode.Callback selectionCallback(ActionMode.Callback callback) {
        return callback instanceof SelectionCallback ? callback : new SelectionCallback(callback);
    }

    private void readSelection(ActionMode mode) {
        if (!active || !translationEnabled) return;
        final long request = ++generation;
        // This host-owned expression runs once after a native menu click. It is
        // never injected into the original website or registered as a listener.
        evaluateJavascript("String(window.getSelection ? window.getSelection() : '').slice(0,2001)", value -> {
            if (!active || !translationEnabled || request != generation) return;
            String text = decodeSelection(value);
            if (mode != null) mode.finish();
            selectedText = null;
            selectionChanged.accept(false);
            if (text == null) {
                Toast.makeText(getContext(), R.string.reader_translation_invalid, Toast.LENGTH_LONG).show();
                return;
            }
            translate.accept(text);
        });
    }

    private final class SelectionCallback extends ActionMode.Callback2 {
        private final ActionMode.Callback delegate;
        private SelectionCallback(ActionMode.Callback delegate) { this.delegate = delegate; }
        private void update(Menu menu) {
            menu.removeItem(R.id.readerTranslateSelection);
            if (active && translationEnabled) menu.add(Menu.NONE, R.id.readerTranslateSelection, 0,
                    R.string.reader_translate_selection).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }
        @Override public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            if (!delegate.onCreateActionMode(mode, menu)) return false;
            selectionMode = mode;
            update(menu);
            inspectSelection(mode);
            return true;
        }
        @Override public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            delegate.onPrepareActionMode(mode, menu);
            update(menu);
            inspectSelection(mode);
            return true;
        }
        @Override public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            if (item.getItemId() == R.id.readerTranslateSelection) {
                translateSelection();
                return true;
            }
            return delegate.onActionItemClicked(mode, item);
        }
        @Override public void onDestroyActionMode(ActionMode mode) {
            if (selectionMode == mode) selectionMode = null;
            selectionChanged.accept(false);
            delegate.onDestroyActionMode(mode);
        }
        @Override public void onGetContentRect(ActionMode mode, View view, Rect rect) {
            if (delegate instanceof ActionMode.Callback2) ((ActionMode.Callback2) delegate).onGetContentRect(mode, view, rect);
            else super.onGetContentRect(mode, view, rect);
        }
    }
}
