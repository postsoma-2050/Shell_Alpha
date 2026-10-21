package com.cylonid.nativealpha.util;

import android.content.Context;
import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LegacySandboxStorage {

    public static final String STORAGE_DIR_NAME = "sandbox_storage";
    public static final String SLOT_FILE_PREFIX = "sandbox_slot_";
    public static final String SLOT_FILE_EXT = ".json";
    public static final String SLOT_LOCK_EXT = ".lock";
    public static final String WEBVIEW_DIR_PREFIX = "app_webview_";
    public static final String SANDBOX_PREFIX = "sandbox_";

    public static final int MIN_CONTAINER_ID = 0;
    public static final int MAX_CONTAINER_ID = 7;

    private static final Pattern[] CONTAINER_LEGACY_PATTERNS =
            new Pattern[MAX_CONTAINER_ID - MIN_CONTAINER_ID + 1];

    static {
        for (int i = MIN_CONTAINER_ID; i <= MAX_CONTAINER_ID; i++) {
            CONTAINER_LEGACY_PATTERNS[i] = Pattern.compile("^" + i + "([a-zA-Z0-9]+)_(\\d+)$");
        }
    }

    private static final Pattern GENERIC_LEGACY_PATTERN =
            Pattern.compile("^([0-7])([a-zA-Z0-9]+)_(\\d+)$");

    private static final Pattern SAFE_SUFFIX_PATTERN =
            Pattern.compile("^[a-zA-Z0-9_]+$");

    private static final Object[] PROCESS_LOCKS =
            new Object[MAX_CONTAINER_ID - MIN_CONTAINER_ID + 1];

    static {
        for (int i = 0; i < PROCESS_LOCKS.length; i++) {
            PROCESS_LOCKS[i] = new Object();
        }
    }

    private interface SlotModifier {
        void modify(JSONObject json) throws JSONException;
    }

    private LegacySandboxStorage() {}

    public static String resolveSuffix(Context context, int containerId) {
        return resolveSuffix(context, getDataDir(context), containerId);
    }

    public static String resolveSuffix(Context context, File dataDir, int containerId) {
        if (!isValidContainerId(containerId)) {
            return defaultSuffix(containerId);
        }

        String fallbackSuffix = defaultSuffix(containerId);

        // 1. Check previously persisted or explicit selection directly from disk.
        SlotConfig config = readSlotConfig(dataDir, containerId);
        String savedSuffix = config.selectedSuffix;
        if (savedSuffix != null) {
            if (savedSuffix.equals(fallbackSuffix)) {
                return fallbackSuffix;
            }
            if (isLegacySuffixForContainer(containerId, savedSuffix)) {
                File legacyDir = getWebviewDir(dataDir, savedSuffix);
                if (legacyDir != null && legacyDir.isDirectory() && isSafelyWithinDataDir(dataDir, legacyDir)) {
                    return savedSuffix;
                } else if (dataDir != null && dataDir.exists()) {
                    clearSavedSuffix(dataDir, containerId);
                }
            } else {
                clearSavedSuffix(dataDir, containerId);
            }
        }

        // 2. Target exists priority
        File targetNewDir = getWebviewDir(dataDir, fallbackSuffix);
        if (targetNewDir != null && targetNewDir.isDirectory() && isSafelyWithinDataDir(dataDir, targetNewDir)) {
            saveSelectedSuffix(dataDir, containerId, fallbackSuffix);
            List<String> existingLegacy = findLegacySuffixesOnDisk(dataDir, containerId);
            if (!existingLegacy.isEmpty()) {
                saveLegacySuffixes(dataDir, containerId, existingLegacy);
            }
            return fallbackSuffix;
        }

        // 3. Scan disk for legacy directories
        List<String> legacySuffixes = findLegacySuffixesOnDisk(dataDir, containerId);
        saveLegacySuffixes(dataDir, containerId, legacySuffixes);

        if (legacySuffixes.isEmpty()) {
            saveSelectedSuffix(dataDir, containerId, fallbackSuffix);
            return fallbackSuffix;
        }

        if (legacySuffixes.size() == 1) {
            String singleSuffix = legacySuffixes.get(0);
            saveSelectedSuffix(dataDir, containerId, singleSuffix);
            return singleSuffix;
        }

        // Conflict (> 1): Return default sandbox_N without selecting
        return fallbackSuffix;
    }

    public static List<String> getLegacySuffixes(Context context, int containerId) {
        return getLegacySuffixes(context, getDataDir(context), containerId);
    }

    public static List<String> getLegacySuffixes(Context context, File dataDir, int containerId) {
        if (!isValidContainerId(containerId)) {
            return Collections.emptyList();
        }
        List<String> diskList = findLegacySuffixesOnDisk(dataDir, containerId);
        if (!diskList.isEmpty()) {
            saveLegacySuffixes(dataDir, containerId, diskList);
            return Collections.unmodifiableList(diskList);
        }
        if (dataDir != null && dataDir.exists()) {
            saveLegacySuffixes(dataDir, containerId, Collections.emptyList());
            return Collections.emptyList();
        }
        SlotConfig config = readSlotConfig(dataDir, containerId);
        return Collections.unmodifiableList(config.legacySuffixes);
    }

    public static String getSelectedSuffix(Context context, int containerId) {
        return getSelectedSuffix(context, getDataDir(context), containerId);
    }

    public static String getSelectedSuffix(Context context, File dataDir, int containerId) {
        if (!isValidContainerId(containerId)) {
            return null;
        }
        SlotConfig config = readSlotConfig(dataDir, containerId);
        String saved = config.selectedSuffix;
        if (saved == null) {
            return null;
        }
        if (saved.equals(defaultSuffix(containerId))) {
            return saved;
        }
        if (isLegacySuffixForContainer(containerId, saved)) {
            File dir = getWebviewDir(dataDir, saved);
            if (dir != null && dir.isDirectory() && isSafelyWithinDataDir(dataDir, dir)) {
                return saved;
            }
            if (dataDir != null && dataDir.exists()) {
                clearSavedSuffix(dataDir, containerId);
                return null;
            }
            return saved;
        }
        return null;
    }

    public static boolean selectLegacySuffixForNextStart(Context context, int containerId, String suffix) {
        return selectLegacySuffixForNextStart(context, getDataDir(context), containerId, suffix);
    }

    public static boolean selectLegacySuffixForNextStart(Context context, File dataDir, int containerId, String suffix) {
        if (!isValidContainerId(containerId) || suffix == null || suffix.trim().isEmpty()) {
            return false;
        }
        String trimmed = suffix.trim();
        if (!isSafeSuffix(trimmed)) {
            return false;
        }

        boolean isDefault = trimmed.equals(defaultSuffix(containerId));
        boolean isLegacy = isLegacySuffixForContainer(containerId, trimmed);

        if (!isDefault && !isLegacy) {
            return false;
        }

        File target = getWebviewDir(dataDir, trimmed);
        if (target == null || !target.isDirectory() || !isSafelyWithinDataDir(dataDir, target)) {
            return false;
        }

        return saveSelectedSuffix(dataDir, containerId, trimmed);
    }

    public static List<String> findLegacySuffixesOnDisk(File dataDir, int containerId) {
        if (!isValidContainerId(containerId) || dataDir == null || !dataDir.exists() || !dataDir.isDirectory()) {
            return Collections.emptyList();
        }
        File[] files = dataDir.listFiles();
        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }
        List<String> list = new ArrayList<>();
        for (File file : files) {
            if (!file.isDirectory() || !isSafelyWithinDataDir(dataDir, file)) {
                continue;
            }
            String name = file.getName();
            if (!name.startsWith(WEBVIEW_DIR_PREFIX)) {
                continue;
            }
            String suffix = name.substring(WEBVIEW_DIR_PREFIX.length());
            if (isLegacySuffixForContainer(containerId, suffix)) {
                list.add(suffix);
            }
        }
        Collections.sort(list);
        return list;
    }

    public static boolean isSafeSuffix(String suffix) {
        if (suffix == null || suffix.isEmpty() || containsPathInjection(suffix)) {
            return false;
        }
        return SAFE_SUFFIX_PATTERN.matcher(suffix).matches();
    }

    public static boolean isValidContainerId(int containerId) {
        return containerId >= MIN_CONTAINER_ID && containerId <= MAX_CONTAINER_ID;
    }

    public static String defaultSuffix(int containerId) {
        return SANDBOX_PREFIX + containerId;
    }

    public static boolean isLegacySuffixForContainer(int containerId, String suffix) {
        if (!isValidContainerId(containerId) || suffix == null || suffix.isEmpty() || !isSafeSuffix(suffix) || suffix.startsWith(SANDBOX_PREFIX)) {
            return false;
        }
        return CONTAINER_LEGACY_PATTERNS[containerId].matcher(suffix).matches();
    }

    public static boolean isValidLegacySuffix(String suffix) {
        if (suffix == null || suffix.isEmpty() || !isSafeSuffix(suffix) || suffix.startsWith(SANDBOX_PREFIX)) {
            return false;
        }
        return GENERIC_LEGACY_PATTERN.matcher(suffix).matches();
    }

    public static int extractContainerIdFromLegacySuffix(String suffix) {
        if (suffix == null || suffix.isEmpty() || !isSafeSuffix(suffix) || suffix.startsWith(SANDBOX_PREFIX)) {
            return -1;
        }
        Matcher matcher = GENERIC_LEGACY_PATTERN.matcher(suffix);
        if (matcher.matches()) {
            return Integer.parseInt(matcher.group(1));
        }
        return -1;
    }

    public static File getWebviewDir(File dataDir, String suffix) {
        if (dataDir == null || suffix == null || !isSafeSuffix(suffix)) {
            return null;
        }
        return new File(dataDir, WEBVIEW_DIR_PREFIX + suffix);
    }

    public static File getDataDir(Context context) {
        if (context == null) {
            return null;
        }
        File dir = context.getDataDir();
        if (dir != null) {
            return dir;
        }
        if (context.getApplicationInfo() != null && context.getApplicationInfo().dataDir != null) {
            return new File(context.getApplicationInfo().dataDir);
        }
        return context.getFilesDir() != null ? context.getFilesDir().getParentFile() : null;
    }

    public static boolean containsPathInjection(String suffix) {
        if (suffix == null) {
            return true;
        }
        return suffix.contains("/")
                || suffix.contains("\\")
                || suffix.contains("..")
                || suffix.contains("\0")
                || suffix.contains("%");
    }

    public static boolean isSafelyWithinDataDir(File dataDir, File targetDir) {
        if (dataDir == null || targetDir == null) {
            return false;
        }
        try {
            File canonicalDataDir = dataDir.getCanonicalFile();
            File canonicalTargetDir = targetDir.getCanonicalFile();
            File parent = canonicalTargetDir.getParentFile();
            return parent != null && parent.equals(canonicalDataDir);
        } catch (IOException e) {
            return false;
        }
    }

    public static void clearSavedSuffix(Context context, int containerId) {
        clearSavedSuffix(getDataDir(context), containerId);
    }

    public static void clearSavedSuffix(File dataDir, int containerId) {
        mutateSlotConfig(dataDir, containerId, new SlotModifier() {
            @Override
            public void modify(JSONObject json) {
                json.remove("selectedSuffix");
            }
        });
    }

    public static void clearAllForTest(Context context) {
        clearAllForTest(getDataDir(context));
    }

    public static void clearAllForTest(File dataDir) {
        if (dataDir == null) {
            return;
        }
        File storageDir = getStorageDir(dataDir);
        if (!storageDir.exists() || !storageDir.isDirectory()) {
            return;
        }
        File[] files = storageDir.listFiles();
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
    }

    public static File getStorageDir(File dataDir) {
        if (dataDir == null) {
            return null;
        }
        return new File(dataDir, STORAGE_DIR_NAME);
    }

    public static File getSlotConfigFile(File storageDir, int containerId) {
        return new File(storageDir, SLOT_FILE_PREFIX + containerId + SLOT_FILE_EXT);
    }

    public static File getSlotLockFile(File storageDir, int containerId) {
        return new File(storageDir, SLOT_FILE_PREFIX + containerId + SLOT_LOCK_EXT);
    }

    private static boolean saveSelectedSuffix(File dataDir, int containerId, final String suffix) {
        return mutateSlotConfig(dataDir, containerId, new SlotModifier() {
            @Override
            public void modify(JSONObject json) throws JSONException {
                if (suffix != null) {
                    json.put("selectedSuffix", suffix);
                } else {
                    json.remove("selectedSuffix");
                }
            }
        });
    }

    private static boolean saveLegacySuffixes(File dataDir, int containerId, final List<String> suffixes) {
        return mutateSlotConfig(dataDir, containerId, new SlotModifier() {
            @Override
            public void modify(JSONObject json) throws JSONException {
                JSONArray arr = new JSONArray();
                if (suffixes != null) {
                    for (String s : suffixes) {
                        arr.put(s);
                    }
                }
                json.put("legacySuffixes", arr);
            }
        });
    }

    private static SlotConfig readSlotConfig(File dataDir, int containerId) {
        if (!isValidContainerId(containerId) || dataDir == null) {
            return new SlotConfig(null, Collections.emptyList());
        }
        File storageDir = getStorageDir(dataDir);
        File lockFile = getSlotLockFile(storageDir, containerId);
        File configFile = getSlotConfigFile(storageDir, containerId);
        File bakFile = new File(configFile.getPath() + ".bak");

        if (!configFile.exists() && !bakFile.exists()) {
            return new SlotConfig(null, Collections.emptyList());
        }

        synchronized (PROCESS_LOCKS[containerId]) {
            try (RandomAccessFile raf = new RandomAccessFile(lockFile, "rw");
                 FileLock lock = raf.getChannel().lock(0L, Long.MAX_VALUE, true)) {
                AtomicFile atomicFile = new AtomicFile(configFile);
                byte[] bytes = atomicFile.readFully();
                if (bytes == null || bytes.length == 0) {
                    return new SlotConfig(null, Collections.emptyList());
                }
                String jsonStr = new String(bytes, StandardCharsets.UTF_8);
                JSONObject obj = new JSONObject(jsonStr);
                String selected = obj.optString("selectedSuffix", null);
                if (selected != null && selected.trim().isEmpty()) {
                    selected = null;
                }
                List<String> legacyList = new ArrayList<>();
                JSONArray arr = obj.optJSONArray("legacySuffixes");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        legacyList.add(arr.getString(i));
                    }
                }
                return new SlotConfig(selected, legacyList);
            } catch (Exception e) {
                return new SlotConfig(null, Collections.emptyList());
            }
        }
    }

    private static boolean mutateSlotConfig(File dataDir, int containerId, SlotModifier modifier) {
        if (!isValidContainerId(containerId) || dataDir == null || modifier == null) {
            return false;
        }
        File storageDir = getStorageDir(dataDir);
        if (!storageDir.exists() && !storageDir.mkdirs()) {
            // directory creation failed or already exists
        }
        File lockFile = getSlotLockFile(storageDir, containerId);
        File configFile = getSlotConfigFile(storageDir, containerId);
        File bakFile = new File(configFile.getPath() + ".bak");

        synchronized (PROCESS_LOCKS[containerId]) {
            try (RandomAccessFile raf = new RandomAccessFile(lockFile, "rw");
                 FileLock lock = raf.getChannel().lock(0L, Long.MAX_VALUE, false)) {

                JSONObject json;
                boolean fileExisted = configFile.exists() || bakFile.exists();

                if (fileExisted) {
                    try {
                        AtomicFile atomicFile = new AtomicFile(configFile);
                        byte[] bytes = atomicFile.readFully();
                        if (bytes != null && bytes.length > 0) {
                            json = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
                        } else {
                            json = new JSONObject();
                        }
                    } catch (Exception e) {
                        // Existing file failed to read: do NOT overwrite/wipe out state
                        return false;
                    }
                } else {
                    json = new JSONObject();
                }

                json.put("containerId", containerId);
                modifier.modify(json);
                json.put("updatedAt", System.currentTimeMillis());

                byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
                AtomicFile atomicFile = new AtomicFile(configFile);
                FileOutputStream fos = null;
                try {
                    fos = atomicFile.startWrite();
                    fos.write(bytes);
                    atomicFile.finishWrite(fos);
                    return true;
                } catch (IOException e) {
                    if (fos != null) {
                        atomicFile.failWrite(fos);
                    }
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        }
    }

    private static final class SlotConfig {
        final String selectedSuffix;
        final List<String> legacySuffixes;

        SlotConfig(String selectedSuffix, List<String> legacySuffixes) {
            this.selectedSuffix = selectedSuffix;
            this.legacySuffixes = legacySuffixes != null ? legacySuffixes : Collections.emptyList();
        }
    }
}
