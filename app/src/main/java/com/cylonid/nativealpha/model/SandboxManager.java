package com.cylonid.nativealpha.model;

import android.util.Log;
import androidx.annotation.NonNull;

import java.util.stream.IntStream;

public class SandboxManager {

    private static final SandboxManager instance = new SandboxManager();
    private static final int NUM_OF_SANDBOXES = 8;
    private final Sandbox[] sandboxes = new Sandbox[NUM_OF_SANDBOXES];
    private int next_container;

    private SandboxManager()
    {
        next_container = 0;
        IntStream.range(0, NUM_OF_SANDBOXES).forEach(x -> sandboxes[x] = new Sandbox(x));
    }

    public static SandboxManager getInstance() {
        return instance;
    }

    public static int getSandboxCount() {
        return NUM_OF_SANDBOXES;
    }

    public static boolean isValidContainerId(int containerId) {
        return containerId >= 0 && containerId < NUM_OF_SANDBOXES;
    }

    @NonNull
    public WebApp normalizeSandbox(@NonNull WebApp webapp) {
        if (!isValidContainerId(webapp.getContainerId())) {
            webapp.setContainerId(com.cylonid.nativealpha.util.Const.NO_CONTAINER);
        }
        webapp.setUseContainer(isValidContainerId(webapp.getContainerId()));
        return webapp;
    }

    public int calculateNextFreeContainerId() {
        if (next_container < 0 || next_container >= NUM_OF_SANDBOXES) {
            next_container = 0;
        }
        return next_container++;
    }

    public void registerWebAppToSandbox(WebApp webapp) {
        normalizeSandbox(webapp);
        int sId = webapp.getContainerId();
        if (!isValidContainerId(sId)) return;
        sandboxes[sId].registerWebApp(webapp.getID());
        Log.d("SBX", "Sandbox " + sId + " contains "
                + sandboxes[sId].getRegisteredWebAppCount() + " app(s)");
    }

    public void unregisterWebAppFromSandbox(int sandboxId) {
        if (!isValidContainerId(sandboxId)) return;
        if (!sandboxes[sandboxId].isUnoccupied()) {
            WebApp currently_set_webapp = DataManager.getInstance().getWebApp(sandboxes[sandboxId].getCurrentlyRegisteredWebapp());
            sandboxes[sandboxId].unregisterAllWebApps();
            if (currently_set_webapp != null) {
                Log.d("SBX", "Sandbox " + sandboxId + " is left by " + currently_set_webapp.getTitle());
            }
        }
    }

    public int getNextContainer() {
        return next_container;
    }

    public void setNextContainer(int nextContainer) {
        this.next_container = nextContainer;
    }

}

