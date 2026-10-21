package com.cylonid.nativealpha;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.cylonid.nativealpha.model.DataManager;
import com.cylonid.nativealpha.model.WebApp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Runs against actual Chromium cookie/storage stores and Android IPC, not WebView shadows. */
@RunWith(AndroidJUnit4.class)
public class SandboxSessionIntegrationTest {
    private Context context;
    private HandlerThread replies;
    private final List<ServiceConnection> connections = new ArrayList<>();
    private Messenger first;
    private Messenger second;

    @Before public void connect() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        replies = new HandlerThread("sandbox-test-replies");
        replies.start();
        first = bind(0);
        second = bind(1);
    }

    @After public void disconnect() {
        for (ServiceConnection connection : connections) context.unbindService(connection);
        if (replies != null) replies.quitSafely();
    }

    private Messenger bind(int id) throws Exception {
        LinkedBlockingQueue<Messenger> connected = new LinkedBlockingQueue<>();
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) { connected.add(new Messenger(binder)); }
            @Override public void onServiceDisconnected(ComponentName name) { }
        };
        Intent intent = new Intent().setClassName(context,
                "com.cylonid.nativealpha.testing.SandboxProbeService$Sandbox" + id);
        assertTrue(context.bindService(intent, connection, Context.BIND_AUTO_CREATE));
        connections.add(connection);
        Messenger service = connected.poll(30, TimeUnit.SECONDS);
        assertNotNull("Sandbox service did not connect", service);
        return service;
    }

    private String request(Messenger service, String operation, String client, String value, int id) throws Exception {
        LinkedBlockingQueue<Bundle> responses = new LinkedBlockingQueue<>();
        Message message = Message.obtain();
        Bundle args = new Bundle();
        args.putString("op", operation);
        args.putString("client", client);
        args.putString("value", value);
        args.putInt("id", id);
        message.setData(args);
        message.replyTo = new Messenger(new Handler(replies.getLooper(), response -> {
            responses.add(response.getData()); return true;
        }));
        service.send(message);
        Bundle response = responses.poll(30, TimeUnit.SECONDS);
        assertNotNull("No response for " + operation, response);
        assertNull(response.getString("error"), response.getString("error"));
        return response.getString("value");
    }

    @Test public void cookiesAreSharedWithinProfileAndIsolatedAcrossProfiles() throws Exception {
        String suffix = Long.toString(System.nanoTime());
        assertEquals("true", request(first, "cookie_set", "one", "personal" + suffix, 0));
        assertEquals("true", request(second, "cookie_set", "one", "work" + suffix, 0));
        assertTrue(request(first, "cookie_get", "two", null, 0).contains("personal" + suffix));
        assertTrue(request(second, "cookie_get", "two", null, 0).contains("work" + suffix));
        assertFalse(request(first, "cookie_get", "two", null, 0).contains("work" + suffix));
    }

    @Test public void twoWebViewsShareOriginStorageOnlyInsideTheirProfile() throws Exception {
        String value = "first-" + System.nanoTime();
        assertEquals("\"" + value + "\"", request(first, "storage_set", "app-one", value, 0));
        assertEquals("\"" + value + "\"", request(first, "storage_get", "app-two", null, 0));
        assertNotEquals("\"" + value + "\"", request(second, "storage_get", "app-two", null, 0));
    }

    @Test public void staleSandboxWriteCannotUndoAHostProfileChange() throws Exception {
        DataManager manager = DataManager.getInstance();
        manager.loadAppData();
        int id = manager.getWebsites().get(0).getID();
        request(first, "capture_app", "one", null, id);
        WebApp current = manager.getWebAppIgnoringGlobalOverride(id, true);
        current.setContainerId(1);
        current.setUseContainer(true);
        manager.replaceWebApp(current);
        assertEquals("1", request(first, "rename_captured", "one", "IPC verified", id));
        WebApp stored = manager.getWebAppIgnoringGlobalOverride(id, true);
        assertEquals(1, stored.getContainerId());
        assertEquals("IPC verified", stored.getTitle());
    }
}
