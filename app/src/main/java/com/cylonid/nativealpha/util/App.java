package com.cylonid.nativealpha.util;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.webkit.WebView;
import com.cylonid.nativealpha.model.SandboxManager;

import androidx.work.Configuration;
import androidx.work.WorkManager;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class App extends Application {

    private static final Pattern SANDBOX_PROCESS_PATTERN =
            Pattern.compile(":web_sandbox_(\\d+)$");

    @SuppressLint("StaticFieldLeak") //We are using app context which is never deleted during runtime, so this is not a leak per se.
    //https://stackoverflow.com/questions/2002288/static-way-to-get-context-in-android
    private static Context context;

    @Override
    public void onCreate() {
        super.onCreate();

        String processName = Application.getProcessName();
        if (processName != null) {
            Matcher matcher = SANDBOX_PROCESS_PATTERN.matcher(processName);
            if (matcher.find()) {
                // All Lite Apps launched in this process share the same container data.
                int containerId = Integer.parseInt(matcher.group(1));
                if (SandboxManager.isValidContainerId(containerId)) {
                    WebView.setDataDirectorySuffix(LegacySandboxStorage.resolveSuffix(this, containerId));
                }
            }
        }

        App.context = getApplicationContext();
        if (!WorkManager.isInitialized()) {
            Configuration config = new Configuration.Builder()
                    .setDefaultProcessName(getPackageName())
                    .build();
            try {
                WorkManager.initialize(this, config);
            } catch (IllegalStateException e) {
                // Ignore if initialized concurrently
            }
        }
    }

    public static Context getAppContext() {
        return App.context;
    }
}
