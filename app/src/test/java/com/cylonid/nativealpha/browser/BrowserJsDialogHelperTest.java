package com.cylonid.nativealpha.browser;

import android.app.Activity;
import android.os.Looper;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.util.ReflectionHelpers;

import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class BrowserJsDialogHelperTest {

    private ActivityController<Activity> activityController;
    private Activity activity;
    private BrowserJsDialogHelper helper;

    @Before
    public void setUp() {
        activityController = Robolectric.buildActivity(Activity.class).create().start().resume();
        activity = activityController.get();
        activity.setTheme(com.cylonid.nativealpha.R.style.AppTheme);
        helper = new BrowserJsDialogHelper(activity);
    }

    @After
    public void tearDown() {
        if (activityController != null && !activity.isDestroyed()) {
            activityController.destroy();
        }
    }

    private static EditText findEditText(android.view.View root) {
        if (root instanceof EditText) return (EditText) root;
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                EditText found = findEditText(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private Object createDummyResultReceiver() throws Exception {
        Class<?> receiverClass = Class.forName("android.webkit.JsResult$ResultReceiver");
        return Proxy.newProxyInstance(
                receiverClass.getClassLoader(),
                new Class<?>[]{receiverClass},
                (proxy, method, args) -> null);
    }

    private JsResult createJsResult() throws Exception {
        Class<?> receiverClass = Class.forName("android.webkit.JsResult$ResultReceiver");
        return ReflectionHelpers.callConstructor(
                JsResult.class,
                ReflectionHelpers.ClassParameter.from(receiverClass, createDummyResultReceiver()));
    }

    private JsPromptResult createJsPromptResult() throws Exception {
        Class<?> receiverClass = Class.forName("android.webkit.JsResult$ResultReceiver");
        return ReflectionHelpers.callConstructor(
                JsPromptResult.class,
                ReflectionHelpers.ClassParameter.from(receiverClass, createDummyResultReceiver()));
    }

    @Test
    public void jsAlertDisplaysDialogAndConfirmInvoked() throws Exception {
        JsResult result = createJsResult();
        boolean handled = helper.handleJsAlert(null, "https://example.com", "Test Alert", result);
        assertTrue(handled);

        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();

        boolean confirmed = ReflectionHelpers.callInstanceMethod(result, "getResult");
        assertTrue(confirmed);
    }

    @Test
    public void jsAlertCancelsWhenActivityIsFinishing() throws Exception {
        activity.finish();
        assertTrue(helper.isActivityUnavailable());

        JsResult result = createJsResult();
        boolean handled = helper.handleJsAlert(null, "https://example.com", "Test Alert", result);
        assertTrue(handled);

        boolean confirmed = ReflectionHelpers.callInstanceMethod(result, "getResult");
        assertFalse(confirmed);
    }

    @Test
    public void jsConfirmDisplaysDialogAndNegativeButtonCancels() throws Exception {
        JsResult result = createJsResult();
        boolean handled = helper.handleJsConfirm(null, "https://example.com", "Test Confirm", result);
        assertTrue(handled);

        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();

        boolean confirmed = ReflectionHelpers.callInstanceMethod(result, "getResult");
        assertFalse(confirmed);
    }

    @Test
    public void jsConfirmPositiveButtonConfirms() throws Exception {
        JsResult result = createJsResult();
        boolean handled = helper.handleJsConfirm(null, "https://example.com", "Test Confirm", result);
        assertTrue(handled);

        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();

        boolean confirmed = ReflectionHelpers.callInstanceMethod(result, "getResult");
        assertTrue(confirmed);
    }

    @Test
    public void jsPromptSubmitsCustomInput() throws Exception {
        JsPromptResult result = createJsPromptResult();
        AlertDialog dialog = helper.showJsPrompt(null, "https://example.com", "Enter name:", "Alice", result);
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());

        EditText input = findEditText(dialog.getWindow() != null ? dialog.getWindow().getDecorView() : null);
        assertNotNull(input);
        input.setText("Bob");

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();

        String stringResult = ReflectionHelpers.callInstanceMethod(result, "getStringResult");
        assertEquals("Bob", stringResult);
    }

    @Test
    public void jsPromptNegativeButtonCancels() throws Exception {
        JsPromptResult result = createJsPromptResult();
        AlertDialog dialog = helper.showJsPrompt(null, "https://example.com", "Enter name:", "Alice", result);
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();

        boolean confirmed = ReflectionHelpers.callInstanceMethod(result, "getResult");
        assertFalse(confirmed);
    }

    @Test
    public void jsBeforeUnloadPositiveButtonConfirms() throws Exception {
        JsResult result = createJsResult();
        boolean handled = helper.handleJsBeforeUnload(null, "https://example.com", "Unsaved changes!", result);
        assertTrue(handled);

        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();

        boolean confirmed = ReflectionHelpers.callInstanceMethod(result, "getResult");
        assertTrue(confirmed);
    }

    @Test
    public void jsBeforeUnloadNegativeButtonCancels() throws Exception {
        JsResult result = createJsResult();
        boolean handled = helper.handleJsBeforeUnload(null, "https://example.com", null, result);
        assertTrue(handled);

        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();

        boolean confirmed = ReflectionHelpers.callInstanceMethod(result, "getResult");
        assertFalse(confirmed);
    }

    @Test
    public void dismissActiveDialogCancelsResultAndDismissesDialog() throws Exception {
        JsResult result = createJsResult();
        AlertDialog dialog = helper.showJsAlert(null, "https://example.com", "Active Dialog", result);
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());
        assertEquals(dialog, helper.getActiveDialog());

        helper.dismissActiveDialog();
        shadowOf(Looper.getMainLooper()).idle();

        assertNull(helper.getActiveDialog());
        assertFalse(dialog.isShowing());
        boolean confirmed = ReflectionHelpers.callInstanceMethod(result, "getResult");
        assertFalse(confirmed);
    }
}
