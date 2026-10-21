package com.cylonid.nativealpha.browser;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.cylonid.nativealpha.R;
import com.cylonid.nativealpha.model.WebApp;

/**
 * Controller responsible for managing WebKit printing and PDF export via system PrintManager
 * and PrintDocumentAdapter.
 * Aligned with Hermit Lite Apps print / save-as-PDF architecture.
 */
public class BrowserPrintController {

    public interface Callback {
        @Nullable
        WebApp getWebApp();

        @Nullable
        WebView getActiveWebView();
    }

    public interface PrintExecutor {
        boolean print(@NonNull String printJobName, @NonNull PrintDocumentAdapter printAdapter, @NonNull PrintAttributes attributes);
    }

    private final Activity activity;
    private final Callback callback;
    private PrintExecutor printExecutor;
    private Boolean printSupportedOverride;

    public BrowserPrintController(@NonNull Activity activity, @NonNull Callback callback) {
        this.activity = activity;
        this.callback = callback;
        this.printExecutor = this::executeSystemPrint;
    }

    @VisibleForTesting
    public void setPrintExecutor(@Nullable PrintExecutor printExecutor) {
        this.printExecutor = printExecutor != null ? printExecutor : this::executeSystemPrint;
    }

    @VisibleForTesting
    public void setPrintSupportedOverride(@Nullable Boolean supported) {
        this.printSupportedOverride = supported;
    }

    public boolean isPrintSupported() {
        if (printSupportedOverride != null) {
            return printSupportedOverride;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return false;
        }
        try {
            PrintManager pm = (PrintManager) activity.getSystemService(Context.PRINT_SERVICE);
            return pm != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Triggers print or PDF export on the active WebView.
     *
     * @return true if printing was started, false otherwise.
     */
    public boolean print() {
        WebView webView = callback.getActiveWebView();
        if (webView == null) {
            Toast.makeText(activity, R.string.browser_print_unavailable, Toast.LENGTH_SHORT).show();
            return false;
        }
        String documentTitle = webView.getTitle();
        if (documentTitle == null || documentTitle.trim().isEmpty()) {
            WebApp webapp = callback.getWebApp();
            if (webapp != null && webapp.getTitle() != null && !webapp.getTitle().trim().isEmpty()) {
                documentTitle = webapp.getTitle();
            }
        }
        return print(webView, documentTitle);
    }

    /**
     * Triggers print or PDF export on the specified WebView with a given document title.
     *
     * @param webView       The source WebView.
     * @param documentTitle Proposed title for the printed document.
     * @return true if printing was initiated, false otherwise.
     */
    public boolean print(@Nullable WebView webView, @Nullable String documentTitle) {
        if (webView == null || !isPrintSupported()) {
            Toast.makeText(activity, R.string.browser_print_unavailable, Toast.LENGTH_SHORT).show();
            return false;
        }

        String fallback = activity.getString(R.string.app_name);
        String jobName = sanitizeJobName(documentTitle, fallback);

        try {
            PrintDocumentAdapter printAdapter = createPrintDocumentAdapter(webView, jobName);
            PrintAttributes attributes = new PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.UNKNOWN_PORTRAIT)
                    .build();

            boolean success = printExecutor.print(jobName, printAdapter, attributes);
            if (success) {
                Toast.makeText(activity, R.string.browser_print_started, Toast.LENGTH_SHORT).show();
                return true;
            } else {
                Toast.makeText(activity, R.string.browser_print_unavailable, Toast.LENGTH_SHORT).show();
                return false;
            }
        } catch (Throwable t) {
            Toast.makeText(activity, R.string.browser_print_unavailable, Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private boolean executeSystemPrint(@NonNull String printJobName, @NonNull PrintDocumentAdapter printAdapter, @NonNull PrintAttributes attributes) {
        try {
            PrintManager printManager = (PrintManager) activity.getSystemService(Context.PRINT_SERVICE);
            if (printManager == null) return false;
            return printManager.print(printJobName, printAdapter, attributes) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Creates a {@link PrintDocumentAdapter} from the given WebView.
     */
    @NonNull
    public PrintDocumentAdapter createPrintDocumentAdapter(@NonNull WebView webView, @NonNull String documentName) {
        PrintDocumentAdapter adapter = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            adapter = webView.createPrintDocumentAdapter(documentName);
        } else {
            adapter = webView.createPrintDocumentAdapter();
        }
        if (adapter == null) {
            adapter = new PrintDocumentAdapter() {
                @Override
                public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes,
                                     android.os.CancellationSignal cancellationSignal, LayoutResultCallback callback, android.os.Bundle extras) {
                    if (callback != null) {
                        callback.onLayoutFinished(new android.print.PrintDocumentInfo.Builder(documentName).build(), false);
                    }
                }

                @Override
                public void onWrite(android.print.PageRange[] pages, android.os.ParcelFileDescriptor destination,
                                    android.os.CancellationSignal cancellationSignal, WriteResultCallback callback) {
                    if (callback != null) {
                        callback.onWriteFinished(new android.print.PageRange[]{android.print.PageRange.ALL_PAGES});
                    }
                }
            };
        }
        return adapter;
    }

    /**
     * Sanitizes the title into a safe print job name without illegal path / filename characters.
     */
    @NonNull
    public static String sanitizeJobName(@Nullable String title, @NonNull String fallback) {
        if (title == null || title.trim().isEmpty()) {
            return fallback;
        }
        // Remove illegal filesystem / print job characters: \ / : * ? " < > |
        String sanitized = title.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (sanitized.isEmpty()) {
            return fallback;
        }
        // Limit length to prevent extreme header names in system dialog
        if (sanitized.length() > 60) {
            sanitized = sanitized.substring(0, 60).trim();
        }
        return sanitized;
    }
}
