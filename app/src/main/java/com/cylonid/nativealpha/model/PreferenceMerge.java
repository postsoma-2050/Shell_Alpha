package com.cylonid.nativealpha.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Merges the fields a reader actually changed, preserving unrelated newer writes. */
public final class PreferenceMerge {
    private PreferenceMerge() { }

    public static JsonElement object(JsonElement before, JsonElement edited, JsonElement latest) {
        if (Objects.equals(before, edited)) return latest == null ? null : latest.deepCopy();
        if (before != null && before.isJsonObject() && edited != null && edited.isJsonObject()
                && latest != null && latest.isJsonObject()) {
            JsonObject result = latest.deepCopy().getAsJsonObject();
            JsonObject old = before.getAsJsonObject();
            JsonObject next = edited.getAsJsonObject();
            for (String key : old.keySet()) {
                if (!next.has(key)) result.remove(key);
            }
            for (Map.Entry<String, JsonElement> field : next.entrySet()) {
                String key = field.getKey();
                JsonElement value = object(old.get(key), field.getValue(), result.get(key));
                if (value != null) result.add(key, value);
            }
            return result;
        }
        return edited == null ? null : edited.deepCopy();
    }

    public static String apps(String baseline, String edited, String latest) {
        Map<Integer, JsonElement> before = byId(baseline);
        Map<Integer, JsonElement> after = byId(edited);
        Map<Integer, JsonElement> current = byId(latest);
        for (Integer id : before.keySet()) {
            if (!after.containsKey(id)) current.remove(id);
        }
        for (Map.Entry<Integer, JsonElement> item : after.entrySet()) {
            int id = item.getKey();
            if (before.containsKey(id) && !current.containsKey(id)) continue; // deleted elsewhere
            current.put(id, object(before.get(id), item.getValue(), current.get(id)));
        }
        JsonArray result = new JsonArray();
        current.values().forEach(result::add);
        return result.toString();
    }

    public static String app(String baseline, String edited, String latest) {
        JsonElement previous = parse(baseline);
        JsonElement submitted = parse(edited);
        JsonElement stored = parse(latest);
        JsonElement merged = object(previous, submitted, stored);
        return merged == null ? "null" : merged.toString();
    }

    static Map<Integer, JsonElement> byId(String json) {
        Map<Integer, JsonElement> result = new LinkedHashMap<>();
        JsonElement parsed = parse(json);
        if (parsed == null || !parsed.isJsonArray()) return result;
        for (JsonElement item : parsed.getAsJsonArray()) {
            if (item.isJsonObject() && item.getAsJsonObject().has("ID")) {
                result.put(item.getAsJsonObject().get("ID").getAsInt(), item);
            }
        }
        return result;
    }

    private static JsonElement parse(String json) {
        return json == null || json.isEmpty() ? null : JsonParser.parseString(json);
    }
}
