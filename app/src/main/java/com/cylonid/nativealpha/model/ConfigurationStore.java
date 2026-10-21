package com.cylonid.nativealpha.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import com.cylonid.nativealpha.util.App;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** IPC facade. No production sandbox reads another process's SharedPreferences cache. */
public final class ConfigurationStore {
    private ConfigurationStore() { }
    public static Uri uri(Context context) { return Uri.parse("content://" + context.getPackageName() + ".configuration"); }

    static Bundle call(String method, String namespace, Bundle extras) {
        Context context = App.getAppContext();
        Bundle result = context.getContentResolver().call(uri(context), method, namespace, extras);
        if (result == null) throw new IllegalStateException("Configuration provider unavailable");
        return result;
    }

    public static SharedPreferences preferences(String namespace) {
        return new SnapshotPreferences(namespace, call("read", namespace, null));
    }

    public static void scheduleMonitors(boolean immediate) {
        Bundle args = new Bundle();
        args.putBoolean("immediate", immediate);
        call("schedule_monitors", "WEBSITEDATA", args);
    }

    static void saveApps(String baseline, String edited, int maxId, int nextContainer) {
        Bundle values = new Bundle();
        values.putString("WEBSITEDATA", edited);
        values.putInt("MAX_ID", maxId);
        values.putInt("NEXT_CONTAINER", nextContainer);
        Bundle args = new Bundle();
        args.putBundle("values", values);
        args.putString("apps_baseline", baseline);
        call("edit", "WEBSITEDATA", args);
    }

    static String patchApp(WebApp app, String baseline) {
        Bundle args = new Bundle();
        args.putInt("id", app.getID());
        args.putString("baseline", baseline);
        args.putString("edited", new com.google.gson.Gson().toJson(app));
        Bundle result = call("patch_app", "WEBSITEDATA", args);
        String apps = result.getBundle("values").getString("WEBSITEDATA", "[]");
        return PreferenceMerge.byId(apps).get(app.getID()).toString();
    }

    static int reserveId() { return call("reserve_id", "WEBSITEDATA", null).getBundle("values").getInt("MAX_ID"); }

    static void saveGlobal(String baseline, String edited) {
        Bundle values = new Bundle();
        values.putString("GLOBALSETTINGS", edited);
        values.putBoolean("globalSettingsStoredAsJson", true);
        Bundle args = new Bundle();
        args.putBundle("values", values);
        args.putString("global_baseline", baseline);
        call("edit", "WEBSITEDATA", args);
    }

    @SuppressWarnings("unchecked")
    static void put(Bundle bundle, String key, Object value) {
        if (value instanceof String) bundle.putString(key, (String) value);
        else if (value instanceof Integer) bundle.putInt(key, (Integer) value);
        else if (value instanceof Long) bundle.putLong(key, (Long) value);
        else if (value instanceof Boolean) bundle.putBoolean(key, (Boolean) value);
        else if (value instanceof Float) bundle.putFloat(key, (Float) value);
        else if (value instanceof Set) bundle.putStringArrayList(key, new ArrayList<>((Set<String>) value));
        else throw new IllegalArgumentException("Unsupported preference type");
    }

    private static final class SnapshotPreferences implements SharedPreferences {
        final String namespace;
        Bundle values;
        final Set<OnSharedPreferenceChangeListener> listeners = new HashSet<>();
        SnapshotPreferences(String namespace, Bundle snapshot) {
            this.namespace = namespace;
            values = snapshot.getBundle("values");
        }
        @Override public Map<String, ?> getAll() {
            Map<String, Object> map = new HashMap<>();
            for (String key : values.keySet()) {
                Object value = values.get(key);
                map.put(key, value instanceof ArrayList ? new HashSet<>((ArrayList<?>) value) : value);
            }
            return map;
        }
        @Override public String getString(String key, String fallback) { return values.containsKey(key) ? values.getString(key) : fallback; }
        @Override public Set<String> getStringSet(String key, Set<String> fallback) { return values.containsKey(key) ? new HashSet<>(values.getStringArrayList(key)) : fallback; }
        @Override public int getInt(String key, int fallback) { return values.getInt(key, fallback); }
        @Override public long getLong(String key, long fallback) { return values.getLong(key, fallback); }
        @Override public float getFloat(String key, float fallback) { return values.getFloat(key, fallback); }
        @Override public boolean getBoolean(String key, boolean fallback) { return values.getBoolean(key, fallback); }
        @Override public boolean contains(String key) { return values.containsKey(key); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { listeners.add(listener); }
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { listeners.remove(listener); }
        @Override public Editor edit() {
            return new Editor() {
                final Bundle changes = new Bundle();
                final ArrayList<String> removed = new ArrayList<>();
                boolean clear;
                @Override public Editor putString(String key, String value) { if (value == null) return remove(key); changes.putString(key, value); removed.remove(key); return this; }
                @Override public Editor putStringSet(String key, Set<String> value) { if (value == null) return remove(key); changes.putStringArrayList(key, new ArrayList<>(value)); removed.remove(key); return this; }
                @Override public Editor putInt(String key, int value) { changes.putInt(key, value); removed.remove(key); return this; }
                @Override public Editor putLong(String key, long value) { changes.putLong(key, value); removed.remove(key); return this; }
                @Override public Editor putFloat(String key, float value) { changes.putFloat(key, value); removed.remove(key); return this; }
                @Override public Editor putBoolean(String key, boolean value) { changes.putBoolean(key, value); removed.remove(key); return this; }
                @Override public Editor remove(String key) { changes.remove(key); if (!removed.contains(key)) removed.add(key); return this; }
                @Override public Editor clear() { clear = true; return this; }
                @Override public boolean commit() {
                    Bundle args = new Bundle();
                    args.putBundle("values", changes);
                    args.putStringArrayList("removed", removed);
                    args.putBoolean("clear", clear);
                    values = call("edit", namespace, args).getBundle("values");
                    Set<String> keys = new HashSet<>(changes.keySet());
                    keys.addAll(removed);
                    for (OnSharedPreferenceChangeListener listener : new HashSet<>(listeners)) {
                        for (String key : keys) listener.onSharedPreferenceChanged(SnapshotPreferences.this, key);
                    }
                    return true;
                }
                @Override public void apply() { commit(); }
            };
        }
    }
}
