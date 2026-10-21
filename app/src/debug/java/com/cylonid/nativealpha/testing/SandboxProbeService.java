package com.cylonid.nativealpha.testing;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.cylonid.nativealpha.model.DataManager;
import com.cylonid.nativealpha.model.WebApp;
import java.util.HashMap;
import java.util.Map;

/** Debug-only, private to this application UID; no network or real credentials are used. */
public class SandboxProbeService extends Service {
    public static final String ORIGIN = "https://sandbox-session.example/";
    private final Map<String, WebView> clients = new HashMap<>();
    private WebApp captured;
    private final Messenger receiver = new Messenger(new Handler(Looper.getMainLooper(), message -> {
        Bundle args = message.getData();
        Messenger reply = message.replyTo;
        try {
            String op = args.getString("op");
            if ("cookie_set".equals(op)) {
                CookieManager cookies = CookieManager.getInstance();
                cookies.setAcceptCookie(true);
                cookies.setCookie(ORIGIN, "session=" + args.getString("value") + "; Path=/; Max-Age=3600; Secure",
                        accepted -> { cookies.flush(); respond(reply, Boolean.toString(accepted), null); });
            } else if ("cookie_get".equals(op)) {
                respond(reply, CookieManager.getInstance().getCookie(ORIGIN), null);
            } else if ("capture_app".equals(op)) {
                captured = new WebApp(DataManager.getInstance().getWebAppIgnoringGlobalOverride(args.getInt("id"), true));
                respond(reply, "captured", null);
            } else if ("rename_captured".equals(op)) {
                captured.setTitle(args.getString("value"));
                DataManager.getInstance().replaceWebApp(captured);
                respond(reply, Integer.toString(captured.getContainerId()), null);
            } else if ("storage_set".equals(op) || "storage_get".equals(op)) {
                String client = args.getString("client", "first");
                WebView view = clients.get(client);
                if (view == null) {
                    view = new WebView(this);
                    view.getSettings().setJavaScriptEnabled(true);
                    view.getSettings().setDomStorageEnabled(true);
                    clients.put(client, view);
                }
                String script = "storage_set".equals(op)
                        ? "localStorage.setItem('probe'," + org.json.JSONObject.quote(args.getString("value")) + ");localStorage.getItem('probe')"
                        : "localStorage.getItem('probe')";
                view.setWebViewClient(new WebViewClient() {
                    @Override public void onPageFinished(WebView page, String url) {
                        page.evaluateJavascript(script, value -> respond(reply, value, null));
                    }
                });
                view.loadDataWithBaseURL(ORIGIN, "<html><body>Session probe</body></html>", "text/html", "UTF-8", null);
            } else respond(reply, null, "Unknown probe operation");
        } catch (Exception failure) { respond(reply, null, failure.toString()); }
        return true;
    }));

    private static void respond(Messenger target, String value, String error) {
        Bundle data = new Bundle();
        data.putString("value", value);
        data.putString("error", error);
        Message response = Message.obtain();
        response.setData(data);
        try { target.send(response); } catch (android.os.RemoteException ignored) { }
    }

    @Override public IBinder onBind(Intent intent) { return receiver.getBinder(); }
    @Override public void onDestroy() {
        for (WebView view : clients.values()) view.destroy();
        clients.clear();
        super.onDestroy();
    }
    public static final class Sandbox0 extends SandboxProbeService { }
    public static final class Sandbox1 extends SandboxProbeService { }
}
