package com.cylonid.nativealpha.browser;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

/** Uses DrawerLayout's native drag/cancel handling; frameless access starts only at the lower-right edge. */
public class BrowserDrawerLayout extends DrawerLayout {
    private boolean toolsOnly;
    private boolean eligible;

    public BrowserDrawerLayout(Context context) { super(context); }
    public BrowserDrawerLayout(Context context, AttributeSet attrs) { super(context, attrs); }
    public BrowserDrawerLayout(Context context, AttributeSet attrs, int style) { super(context, attrs, style); }

    public void setToolsOnly(boolean toolsOnly) { this.toolsOnly = toolsOnly; }

    public boolean isToolsGestureStart(float x, float y) {
        float density = getResources().getDisplayMetrics().density;
        return x >= getWidth() - 24 * density && x <= getWidth()
                && y >= Math.max(0, getHeight() - 224 * density) && y < getHeight() - 24 * density;
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            eligible = !toolsOnly || isDrawerVisible(GravityCompat.END)
                    || isToolsGestureStart(event.getX(), event.getY());
        }
        if (toolsOnly && !eligible && !isDrawerVisible(GravityCompat.END)) return false;
        return super.onInterceptTouchEvent(event);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (toolsOnly && !eligible && !isDrawerVisible(GravityCompat.END)) return false;
        return super.onTouchEvent(event);
    }
}
