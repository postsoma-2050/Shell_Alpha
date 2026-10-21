package com.cylonid.nativealpha.model;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

/** All configuration reads and writes execute in the host process, including sandbox callers. */
public final class ConfigurationProvider extends ContentProvider {
    static final String REVISION = "_configuration_revision";
    private final Object lock = new Object();

    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String namespace, Bundle extras) {
        if (Binder.getCallingUid() != Process.myUid()) throw new SecurityException("Private configuration");
        if (!java.util.Arrays.asList("WEBSITEDATA", "com.cylonid.nativealpha.GENERAL_INFO", "SANDBOX_SETTINGS")
                .contains(namespace)) throw new IllegalArgumentException("Unknown configuration namespace");
        if ("schedule_monitors".equals(method)) {
            if (extras != null && extras.getBoolean("immediate")) {
                com.cylonid.nativealpha.monitors.WebMonitorWorker.enqueueImmediateCheck(providerContext());
            } else com.cylonid.nativealpha.monitors.WebMonitorWorker.syncPeriodicWork(providerContext());
            return new Bundle();
        }
        Bundle result;
        synchronized (lock) {
            SharedPreferences prefs = providerContext().getSharedPreferences(namespace, Context.MODE_PRIVATE);
            if ("read".equals(method)) return snapshot(prefs);
            SharedPreferences.Editor editor = prefs.edit();
            if ("reserve_id".equals(method)) {
                int max = prefs.getInt("MAX_ID", -1);
                for (Integer id : PreferenceMerge.byId(prefs.getString("WEBSITEDATA", "[]")).keySet()) {
                    max = Math.max(max, id);
                }
                editor.putInt("MAX_ID", max + 1);
            } else if ("patch_app".equals(method)) {
                if (extras == null) throw new IllegalArgumentException("Missing app change");
                Map<Integer, JsonElement> apps = PreferenceMerge.byId(prefs.getString("WEBSITEDATA", "[]"));
                int id = extras.getInt("id", -1);
                JsonElement current = apps.get(id);
                if (current == null) throw new IllegalArgumentException("Lite App no longer exists: " + id);
                String merged = PreferenceMerge.app(extras.getString("baseline"),
                        extras.getString("edited"), current.toString());
                apps.put(id, com.google.gson.JsonParser.parseString(merged));
                JsonArray array = new JsonArray();
                apps.values().forEach(array::add);
                editor.putString("WEBSITEDATA", array.toString());
            } else if ("edit".equals(method)) {
                if (extras == null) throw new IllegalArgumentException("Missing preference change");
                boolean clear = extras.getBoolean("clear");
                if (clear) editor.clear();
                ArrayList<String> removed = extras.getStringArrayList("removed");
                if (removed != null) for (String key : removed) editor.remove(key);
                Bundle values = extras.getBundle("values");
                if (values != null) for (String key : values.keySet()) {
                    Object value = values.get(key);
                    if (!clear && "WEBSITEDATA".equals(key) && extras.containsKey("apps_baseline")) {
                        value = PreferenceMerge.apps(extras.getString("apps_baseline"), (String) value,
                                prefs.getString(key, "[]"));
                    } else if (!clear && "GLOBALSETTINGS".equals(key) && extras.containsKey("global_baseline")) {
                        value = PreferenceMerge.app(extras.getString("global_baseline"), (String) value,
                                prefs.getString(key, null));
                    } else if (!clear && "MAX_ID".equals(key) && value instanceof Integer) {
                        value = Math.max(prefs.getInt(key, -1), (Integer) value);
                    }
                    put(editor, key, value);
                }
            } else throw new IllegalArgumentException("Unknown configuration operation");
            editor.putLong(REVISION, prefs.getLong(REVISION, 0) + 1);
            if (!editor.commit()) throw new IllegalStateException("Could not persist configuration");
            result = snapshot(prefs);
        }
        providerContext().getContentResolver().notifyChange(ConfigurationStore.uri(providerContext()), null);
        return result;
    }

    private Context providerContext() {
        Context context = getContext();
        if (context == null) throw new IllegalStateException("Configuration provider not attached");
        return context;
    }

    static Bundle snapshot(SharedPreferences prefs) {
        Bundle values = new Bundle();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (!REVISION.equals(entry.getKey())) ConfigurationStore.put(values, entry.getKey(), entry.getValue());
        }
        Bundle result = new Bundle();
        result.putBundle("values", values);
        result.putLong("revision", prefs.getLong(REVISION, 0));
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void put(SharedPreferences.Editor editor, String key, Object value) {
        if (value == null) editor.remove(key);
        else if (value instanceof String) editor.putString(key, (String) value);
        else if (value instanceof Integer) editor.putInt(key, (Integer) value);
        else if (value instanceof Long) editor.putLong(key, (Long) value);
        else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        else if (value instanceof Float) editor.putFloat(key, (Float) value);
        else if (value instanceof ArrayList) editor.putStringSet(key, new java.util.HashSet<>((ArrayList<String>) value));
        else throw new IllegalArgumentException("Unsupported configuration value");
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) { throw new UnsupportedOperationException(); }
    @Override public String getType(Uri uri) { return "vnd.android.cursor.item/vnd.shellalpha.configuration"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
