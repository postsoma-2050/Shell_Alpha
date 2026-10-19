package com.cylonid.nativealpha.util;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.webkit.WebView;

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
                WebView.setDataDirectorySuffix("sandbox_" + matcher.group(1));
            }
        }

        App.context = getApplicationContext();
        if (!WorkManager.isInitialized()) {
            WorkManager.initialize(this, new Configuration.Builder().build());
        }
    }

    public static Context getAppContext() {
        return App.context;
    }
}
