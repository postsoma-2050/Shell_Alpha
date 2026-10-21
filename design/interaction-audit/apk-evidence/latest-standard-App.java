package com.cylonid.nativealpha.util;

import android.app.Application;
import android.content.Context;
import android.webkit.WebView;
import androidx.work.Configuration;
import androidx.work.WorkManager;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* loaded from: classes3.dex */
public class App extends Application {
    private static final Pattern SANDBOX_PROCESS_PATTERN = Pattern.compile(":web_sandbox_(\\d+)$");
    private static Context context;

    @Override // android.app.Application
    public void onCreate() {
        super.onCreate();
        String processName = Application.getProcessName();
        if (processName != null) {
            Matcher matcher = SANDBOX_PROCESS_PATTERN.matcher(processName);
            if (matcher.find()) {
                WebView.setDataDirectorySuffix("sandbox_" + matcher.group(1));
            }
        }
        context = getApplicationContext();
        if (!WorkManager.isInitialized()) {
            WorkManager.initialize(this, new Configuration.Builder().build());
        }
    }

    public static Context getAppContext() {
        return context;
    }
}
