package com.cylonid.nativealpha.model;

import android.content.SharedPreferences;
import com.cylonid.nativealpha.R;
import com.cylonid.nativealpha.util.App;

/** Profile-wide preferences shared by every Lite App in that profile. */
public final class SandboxPreferences {
    private SandboxPreferences() { }
    private static SharedPreferences prefs() { return ConfigurationStore.preferences("SANDBOX_SETTINGS"); }
    private static void check(int id) {
        if (id != -1 && !SandboxManager.isValidContainerId(id)) throw new IllegalArgumentException("Invalid sandbox");
    }
    public static String getName(int id) {
        check(id);
        String name = prefs().getString("name_" + id, "");
        if (name != null && !name.trim().isEmpty()) return name;
        if (id == -1) return App.getAppContext().getString(R.string.sandbox_ui_default_name);
        int[] labels = {R.string.sandbox_ui_color_blue, R.string.sandbox_ui_color_green,
                R.string.sandbox_ui_color_orange, R.string.sandbox_ui_color_purple,
                R.string.sandbox_ui_color_teal, R.string.sandbox_ui_color_pink,
                R.string.sandbox_ui_color_amber, R.string.sandbox_ui_color_indigo};
        return App.getAppContext().getString(labels[id]);
    }
    public static void setName(int id, String name) {
        check(id);
        prefs().edit().putString("name_" + id, name == null ? "" : name.trim()).commit();
    }
    public static boolean isCookiesAllowed(int id) { check(id); return prefs().getBoolean("cookies_" + id, true); }
    public static void setCookiesAllowed(int id, boolean allowed) { check(id); prefs().edit().putBoolean("cookies_" + id, allowed).commit(); }
}
