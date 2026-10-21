package com.cylonid.nativealpha.model;

import org.junit.Test;
import com.google.gson.JsonParser;
import static org.junit.Assert.*;

public class PreferenceMergeTest {
    @Test public void unrelatedEditsDoNotMoveAnAppBackToItsOldSandbox() {
        String baseline = "[{\"ID\":4,\"title\":\"Mail\",\"containerId\":0},{\"ID\":8,\"title\":\"Other\"}]";
        String edited = "[{\"ID\":4,\"title\":\"Inbox\",\"containerId\":0},{\"ID\":8,\"title\":\"Other\"}]";
        String latest = "[{\"ID\":4,\"title\":\"Mail\",\"containerId\":2},{\"ID\":8,\"title\":\"Updated\"},{\"ID\":9,\"title\":\"New\"}]";
        String merged = PreferenceMerge.apps(baseline, edited, latest);
        assertEquals(2, PreferenceMerge.byId(merged).get(4).getAsJsonObject().get("containerId").getAsInt());
        assertEquals("Inbox", PreferenceMerge.byId(merged).get(4).getAsJsonObject().get("title").getAsString());
        assertEquals("Updated", PreferenceMerge.byId(merged).get(8).getAsJsonObject().get("title").getAsString());
        assertTrue(PreferenceMerge.byId(merged).containsKey(9));
    }

    @Test public void staleWindowDoesNotResurrectDeletedApp() {
        assertEquals("[]", PreferenceMerge.apps("[{\"ID\":1,\"title\":\"Old\"}]",
                "[{\"ID\":1,\"title\":\"Changed\"}]", "[]"));
    }

    @Test public void independentNestedGlobalEditsAreMerged() {
        String baseline = "{\"themeId\":0,\"globalWebApp\":{\"isAllowJs\":true,\"isRequestDesktop\":false}}";
        String edited = "{\"themeId\":1,\"globalWebApp\":{\"isAllowJs\":true,\"isRequestDesktop\":false}}";
        String latest = "{\"themeId\":0,\"globalWebApp\":{\"isAllowJs\":false,\"isRequestDesktop\":false}}";
        var merged = JsonParser.parseString(PreferenceMerge.app(baseline, edited, latest)).getAsJsonObject();
        assertEquals(1, merged.get("themeId").getAsInt());
        assertFalse(merged.getAsJsonObject("globalWebApp").get("isAllowJs").getAsBoolean());
    }
}
