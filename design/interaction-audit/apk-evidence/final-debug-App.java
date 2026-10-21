package com.cylonid.nativealpha.util;

import android.app.Application;
import android.content.Context;
import androidx.work.Configuration;
import androidx.work.WorkManager;

/* loaded from: classes3.dex */
public class App extends Application {
    private static Context context;

    @Override // android.app.Application
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        if (!WorkManager.isInitialized()) {
            WorkManager.initialize(this, new Configuration.Builder().build());
        }
    }

    public static Context getAppContext() {
        return context;
    }
}
