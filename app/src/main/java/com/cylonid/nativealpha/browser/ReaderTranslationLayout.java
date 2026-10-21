package com.cylonid.nativealpha.browser;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

/** Keeps long translations scrollable without covering the entire reading viewport. */
public final class ReaderTranslationLayout extends LinearLayout {
    private int availableHeight;
    public ReaderTranslationLayout(Context context, AttributeSet attributes) { super(context, attributes); }
    public void setAvailableHeight(int height) { availableHeight = height; requestLayout(); }
    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float density = getResources().getDisplayMetrics().density;
        int available = availableHeight > 0 ? availableHeight : getResources().getDisplayMetrics().heightPixels;
        int limit = Math.min(Math.round(560 * density), Math.round(available * (available / density < 480 ? .82f : .55f)));
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED)
            limit = Math.min(limit, MeasureSpec.getSize(heightMeasureSpec));
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(Math.max(0, limit), MeasureSpec.AT_MOST));
    }
}
