package com.cylonid.nativealpha.util;

import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class LegacySandboxStorageTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private Context context;
    private File dataDir;

    @Before
    public void setUp() throws IOException {
        context = RuntimeEnvironment.getApplication();
        dataDir = tempFolder.newFolder("app_data");
        LegacySandboxStorage.clearAllForTest(dataDir);
    }

    @After
    public void tearDown() {
        LegacySandboxStorage.clearAllForTest(dataDir);
    }

    @Test
    public void testSafeSuffixAndPathInjectionValidation() {
        assertTrue(LegacySandboxStorage.isSafeSuffix("0reddit_10"));
        assertTrue(LegacySandboxStorage.isSafeSuffix("7twitter_202"));
        assertTrue(LegacySandboxStorage.isSafeSuffix("sandbox_0"));
        assertTrue(LegacySandboxStorage.isSafeSuffix("sandbox_7"));
        assertTrue(LegacySandboxStorage.isSafeSuffix("3siteAlnum123_45"));

        assertFalse(LegacySandboxStorage.isSafeSuffix(null));
        assertFalse(LegacySandboxStorage.isSafeSuffix(""));
        assertFalse(LegacySandboxStorage.isSafeSuffix("../traversal"));
        assertFalse(LegacySandboxStorage.isSafeSuffix("0site/sub"));
        assertFalse(LegacySandboxStorage.isSafeSuffix("0site\\sub"));
        assertFalse(LegacySandboxStorage.isSafeSuffix("0site\0null"));
        assertFalse(LegacySandboxStorage.isSafeSuffix("0site%20encoded"));
        assertFalse(LegacySandboxStorage.isSafeSuffix("0site-dash_1"));
    }

    @Test
    public void testLegacyPatternSlotExtraction() {
        assertEquals(0, LegacySandboxStorage.extractContainerIdFromLegacySuffix("0reddit_10"));
        assertEquals(7, LegacySandboxStorage.extractContainerIdFromLegacySuffix("7twitter_99"));
        assertEquals(3, LegacySandboxStorage.extractContainerIdFromLegacySuffix("3mySite123_45"));

        assertEquals(-1, LegacySandboxStorage.extractContainerIdFromLegacySuffix("sandbox_0"));
        assertEquals(-1, LegacySandboxStorage.extractContainerIdFromLegacySuffix("sandbox_7"));
        assertEquals(-1, LegacySandboxStorage.extractContainerIdFromLegacySuffix("8outOfBounds_1"));
        assertEquals(-1, LegacySandboxStorage.extractContainerIdFromLegacySuffix("0reddit"));
        assertEquals(-1, LegacySandboxStorage.extractContainerIdFromLegacySuffix("0reddit_"));
        assertEquals(-1, LegacySandboxStorage.extractContainerIdFromLegacySuffix("0reddit_abc"));
        assertEquals(-1, LegacySandboxStorage.extractContainerIdFromLegacySuffix("../0reddit_1"));
    }

    @Test
    public void testSlotZeroAndSevenBoundaryIsolation() {
        assertTrue(LegacySandboxStorage.isLegacySuffixForContainer(0, "0reddit_10"));
        assertFalse(LegacySandboxStorage.isLegacySuffixForContainer(0, "7reddit_10"));
        assertFalse(LegacySandboxStorage.isLegacySuffixForContainer(0, "1reddit_10"));

        assertTrue(LegacySandboxStorage.isLegacySuffixForContainer(7, "7twitter_99"));
        assertFalse(LegacySandboxStorage.isLegacySuffixForContainer(7, "0twitter_99"));
        assertFalse(LegacySandboxStorage.isLegacySuffixForContainer(7, "6twitter_99"));
    }

    @Test
    public void testEmptyDirectoryReturnsDefaultSuffix() {
        String suffix = LegacySandboxStorage.resolveSuffix(context, dataDir, 0);
        assertEquals("sandbox_0", suffix);

        List<String> legacy = LegacySandboxStorage.getLegacySuffixes(context, dataDir, 0);
        assertTrue(legacy.isEmpty());

        String selected = LegacySandboxStorage.getSelectedSuffix(context, dataDir, 0);
        assertEquals("sandbox_0", selected);
    }

    @Test
    public void testSingleLegacyDirectoryAutoSelectedAndPersisted() {
        File legacyDir = new File(dataDir, "app_webview_0reddit_10");
        assertTrue(legacyDir.mkdirs());

        String resolved = LegacySandboxStorage.resolveSuffix(context, dataDir, 0);
        assertEquals("0reddit_10", resolved);

        String selected = LegacySandboxStorage.getSelectedSuffix(context, dataDir, 0);
        assertEquals("0reddit_10", selected);

        List<String> legacyList = LegacySandboxStorage.getLegacySuffixes(context, dataDir, 0);
        assertEquals(1, legacyList.size());
        assertEquals("0reddit_10", legacyList.get(0));

        // Reboot simulation: resolving again returns the same stable key
        String rebootResolved = LegacySandboxStorage.resolveSuffix(context, dataDir, 0);
        assertEquals("0reddit_10", rebootResolved);
    }

    @Test
    public void testSelectedMappingRetainedEvenIfCanonicalSandboxDirCreatedLater() {
        File legacyDir = new File(dataDir, "app_webview_0reddit_10");
        assertTrue(legacyDir.mkdirs());

        // First run resolves to legacy directory and persists mapping
        String resolvedFirst = LegacySandboxStorage.resolveSuffix(context, dataDir, 0);
        assertEquals("0reddit_10", resolvedFirst);

        // Later, a canonical sandbox_0 directory is created on disk
        File canonicalDir = new File(dataDir, "app_webview_sandbox_0");
        assertTrue(canonicalDir.mkdirs());

        // resolveSuffix must maintain the established legacy mapping and NOT flip to sandbox_0
        String resolvedAfterCanonical = LegacySandboxStorage.resolveSuffix(context, dataDir, 0);
        assertEquals("0reddit_10", resolvedAfterCanonical);
    }

    @Test
    public void testMultipleLegacyDirectoriesConflictReturnsDefaultAndSavesList() {
        File dir1 = new File(dataDir, "app_webview_0reddit_10");
        File dir2 = new File(dataDir, "app_webview_0github_20");
        assertTrue(dir1.mkdirs());
        assertTrue(dir2.mkdirs());

        String resolved = LegacySandboxStorage.resolveSuffix(context, dataDir, 0);
        // Conflict: never auto-pick, return sandbox_0
        assertEquals("sandbox_0", resolved);

        // Both directories preserved on disk; no deletion, no merge
        assertTrue(dir1.exists() && dir1.isDirectory());
        assertTrue(dir2.exists() && dir2.isDirectory());

        // Legacy list saved and sorted for UI recovery
        List<String> legacyList = LegacySandboxStorage.getLegacySuffixes(context, dataDir, 0);
        assertEquals(2, legacyList.size());
        assertEquals("0github_20", legacyList.get(0));
        assertEquals("0reddit_10", legacyList.get(1));

        // No auto-selected legacy suffix established yet
        assertNull(LegacySandboxStorage.getSelectedSuffix(context, dataDir, 0));
    }

    @Test
    public void testExplicitSelectionWorksForNextStart() {
        File dir1 = new File(dataDir, "app_webview_0reddit_10");
        File dir2 = new File(dataDir, "app_webview_0github_20");
        assertTrue(dir1.mkdirs());
        assertTrue(dir2.mkdirs());

        // User explicitly selects dir2 via recovery interface
        boolean selected = LegacySandboxStorage.selectLegacySuffixForNextStart(
                context, dataDir, 0, "0github_20");
        assertTrue(selected);
        assertEquals("0github_20", LegacySandboxStorage.getSelectedSuffix(context, dataDir, 0));

        // Next launch resolves to the chosen directory
        String resolved = LegacySandboxStorage.resolveSuffix(context, dataDir, 0);
        assertEquals("0github_20", resolved);

        // Other legacy directories remain untouched
        assertTrue(dir1.exists());
        assertTrue(dir2.exists());

        // Nonexistent suffix rejected
        assertFalse(LegacySandboxStorage.selectLegacySuffixForNextStart(
                context, dataDir, 0, "0nonexistent_99"));

        // Wrong container suffix rejected
        assertFalse(LegacySandboxStorage.selectLegacySuffixForNextStart(
                context, dataDir, 0, "1other_10"));
    }

    @Test
    public void testTargetExistsPriorityOverFreshLegacyDiscovery() {
        // Target sandbox directory already exists prior to any saved mapping
        File targetDir = new File(dataDir, "app_webview_sandbox_0");
        File legacyDir = new File(dataDir, "app_webview_0reddit_10");
        assertTrue(targetDir.mkdirs());
        assertTrue(legacyDir.mkdirs());

        String resolved = LegacySandboxStorage.resolveSuffix(context, dataDir, 0);
        assertEquals("sandbox_0", resolved);
        assertEquals("sandbox_0", LegacySandboxStorage.getSelectedSuffix(context, dataDir, 0));
    }

    @Test
    public void testInvalidContainerIdsHandledGracefully() {
        assertEquals("sandbox_-1", LegacySandboxStorage.resolveSuffix(context, dataDir, -1));
        assertEquals("sandbox_8", LegacySandboxStorage.resolveSuffix(context, dataDir, 8));
        assertEquals("sandbox_99", LegacySandboxStorage.resolveSuffix(context, dataDir, 99));

        assertTrue(LegacySandboxStorage.getLegacySuffixes(context, dataDir, -1).isEmpty());
        assertTrue(LegacySandboxStorage.getLegacySuffixes(context, dataDir, 8).isEmpty());

        assertNull(LegacySandboxStorage.getSelectedSuffix(context, dataDir, -1));
        assertNull(LegacySandboxStorage.getSelectedSuffix(context, dataDir, 8));

        assertFalse(LegacySandboxStorage.selectLegacySuffixForNextStart(context, dataDir, -1, "0reddit_10"));
        assertFalse(LegacySandboxStorage.selectLegacySuffixForNextStart(context, dataDir, 8, "0reddit_10"));
    }

    @Test
    public void testStaleSavedDirectoryClearedWhenDeleted() {
        File legacyDir = new File(dataDir, "app_webview_0reddit_10");
        assertTrue(legacyDir.mkdirs());

        LegacySandboxStorage.resolveSuffix(context, dataDir, 0);
        assertEquals("0reddit_10", LegacySandboxStorage.getSelectedSuffix(context, dataDir, 0));

        // Delete directory from outside
        assertTrue(legacyDir.delete());

        // getSelectedSuffix notices missing directory and clears stale entry
        assertNull(LegacySandboxStorage.getSelectedSuffix(context, dataDir, 0));
        assertEquals("sandbox_0", LegacySandboxStorage.resolveSuffix(context, dataDir, 0));
    }

    @Test
    public void testPathInjectionRejectedInSelectLegacySuffix() {
        File legacyDir = new File(dataDir, "app_webview_0reddit_10");
        assertTrue(legacyDir.mkdirs());

        assertFalse(LegacySandboxStorage.selectLegacySuffixForNextStart(
                context, dataDir, 0, "../../etc/passwd"));
        assertFalse(LegacySandboxStorage.selectLegacySuffixForNextStart(
                context, dataDir, 0, "0reddit/hack_10"));
        assertFalse(LegacySandboxStorage.selectLegacySuffixForNextStart(
                context, dataDir, 0, "0reddit\0hack_10"));
    }

    @Test
    public void testPerSlotAtomicFileIsolation() {
        File legacy0 = new File(dataDir, "app_webview_0site_1");
        File legacy7 = new File(dataDir, "app_webview_7site_2");
        assertTrue(legacy0.mkdirs());
        assertTrue(legacy7.mkdirs());

        LegacySandboxStorage.resolveSuffix(context, dataDir, 0);
        LegacySandboxStorage.resolveSuffix(context, dataDir, 7);

        File storageDir = LegacySandboxStorage.getStorageDir(dataDir);
        File slot0File = LegacySandboxStorage.getSlotConfigFile(storageDir, 0);
        File slot7File = LegacySandboxStorage.getSlotConfigFile(storageDir, 7);

        assertTrue(slot0File.exists());
        assertTrue(slot7File.exists());

        assertEquals("0site_1", LegacySandboxStorage.getSelectedSuffix(context, dataDir, 0));
        assertEquals("7site_2", LegacySandboxStorage.getSelectedSuffix(context, dataDir, 7));

        LegacySandboxStorage.clearSavedSuffix(dataDir, 0);
        assertNull(LegacySandboxStorage.getSelectedSuffix(context, dataDir, 0));
        assertEquals("7site_2", LegacySandboxStorage.getSelectedSuffix(context, dataDir, 7));
    }

    @Test
    public void testAtomicFileBackupRestoredWhenBaseMissing() throws IOException {
        File legacyDir = new File(dataDir, "app_webview_0reddit_10");
        assertTrue(legacyDir.mkdirs());

        File storageDir = LegacySandboxStorage.getStorageDir(dataDir);
        assertTrue(storageDir.mkdirs());
        File slot0File = LegacySandboxStorage.getSlotConfigFile(storageDir, 0);
        File slot0Bak = new File(slot0File.getPath() + ".bak");

        // Write valid backup JSON when base file is missing (simulating write crash before cleanup)
        String json = "{\"containerId\":0,\"selectedSuffix\":\"0reddit_10\",\"legacySuffixes\":[\"0reddit_10\"]}";
        try (FileOutputStream fos = new FileOutputStream(slot0Bak)) {
            fos.write(json.getBytes(StandardCharsets.UTF_8));
        }

        assertFalse(slot0File.exists());
        assertTrue(slot0Bak.exists());

        // Suffix resolution and reading must recover from .bak and not discard mapping
        String selected = LegacySandboxStorage.getSelectedSuffix(context, dataDir, 0);
        assertEquals("0reddit_10", selected);

        String resolved = LegacySandboxStorage.resolveSuffix(context, dataDir, 0);
        assertEquals("0reddit_10", resolved);
    }

    @Test
    public void testFieldMutationDoesNotOverwriteOtherFields() {
        File dir1 = new File(dataDir, "app_webview_0reddit_10");
        File dir2 = new File(dataDir, "app_webview_0github_20");
        assertTrue(dir1.mkdirs());
        assertTrue(dir2.mkdirs());

        // First resolve: detects conflict, writes legacySuffixes list
        LegacySandboxStorage.resolveSuffix(context, dataDir, 0);
        List<String> initialLegacy = LegacySandboxStorage.getLegacySuffixes(context, dataDir, 0);
        assertEquals(2, initialLegacy.size());

        // Mutate selectedSuffix: must preserve legacySuffixes
        assertTrue(LegacySandboxStorage.selectLegacySuffixForNextStart(context, dataDir, 0, "0reddit_10"));
        assertEquals("0reddit_10", LegacySandboxStorage.getSelectedSuffix(context, dataDir, 0));
        List<String> afterSelectLegacy = LegacySandboxStorage.getLegacySuffixes(context, dataDir, 0);
        assertEquals(2, afterSelectLegacy.size());
        assertEquals("0github_20", afterSelectLegacy.get(0));
        assertEquals("0reddit_10", afterSelectLegacy.get(1));

        // Clear selectedSuffix: must preserve legacySuffixes list
        LegacySandboxStorage.clearSavedSuffix(dataDir, 0);
        assertNull(LegacySandboxStorage.getSelectedSuffix(context, dataDir, 0));
        List<String> afterClearLegacy = LegacySandboxStorage.getLegacySuffixes(context, dataDir, 0);
        assertEquals(2, afterClearLegacy.size());
    }

    @Test
    public void testCorruptFileDoesNotSilentlyOverwriteLegacySession() throws IOException {
        File legacyDir = new File(dataDir, "app_webview_0reddit_10");
        assertTrue(legacyDir.mkdirs());

        File storageDir = LegacySandboxStorage.getStorageDir(dataDir);
        assertTrue(storageDir.mkdirs());
        File slot0File = LegacySandboxStorage.getSlotConfigFile(storageDir, 0);

        // Write corrupt bytes
        try (FileOutputStream fos = new FileOutputStream(slot0File)) {
            fos.write("CORRUPT_NOT_JSON".getBytes(StandardCharsets.UTF_8));
        }

        // Resolving with corrupt config file should fallback gracefully to disk scan
        // and reuse the existing single legacy directory rather than crashing or clearing disk
        String resolved = LegacySandboxStorage.resolveSuffix(context, dataDir, 0);
        assertEquals("0reddit_10", resolved);
    }
}
