package com.cylonid.nativealpha;

import android.view.MotionEvent;
import android.view.View;
import org.robolectric.RuntimeEnvironment;
import com.cylonid.nativealpha.browser.BrowserDrawerLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35})
public class BrowserDrawerLayoutTest {
    @Test public void onlyLowerRightEdgeStartsToolsGestureAndSystemBottomIsReserved() {
        for (int[] size : new int[][]{{360, 800}, {800, 360}}) {
            BrowserDrawerLayout drawer = new BrowserDrawerLayout(RuntimeEnvironment.getApplication());
            float density = drawer.getResources().getDisplayMetrics().density;
            int width = Math.round(size[0] * density), height = Math.round(size[1] * density);
            drawer.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            drawer.layout(0, 0, width, height);
            drawer.setToolsOnly(true);
            assertTrue(drawer.isToolsGestureStart(width - 2, height - 100 * density));
            assertFalse(drawer.isToolsGestureStart(width / 2f, height - 100 * density));
            assertFalse(drawer.isToolsGestureStart(width - 2, height - 10 * density));
            assertFalse(drawer.isToolsGestureStart(width - 2, 50 * density));
            MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, width / 2f, height / 2f, 0);
            assertFalse("Normal website touches must not be intercepted", drawer.onInterceptTouchEvent(down));
            down.recycle();
        }
    }
}
