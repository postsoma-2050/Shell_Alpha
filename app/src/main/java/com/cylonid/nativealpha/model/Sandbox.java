package com.cylonid.nativealpha.model;

import java.util.HashSet;
import java.util.Set;

public class Sandbox {
    private final int sIndex;
    private final Set<Integer> registeredWebapps = new HashSet<>();

    public Sandbox(int sIndex) {
        this.sIndex = sIndex;
    }

    public int getCurrentlyRegisteredWebapp() {
        return registeredWebapps.isEmpty() ? -1 : registeredWebapps.iterator().next();
    }

    public boolean isUnoccupied() {
        return registeredWebapps.isEmpty();
    }

    public boolean containsWebApp(int webAppId) {
        return registeredWebapps.contains(webAppId);
    }

    public void registerWebApp(int webAppId) {
        registeredWebapps.add(webAppId);
    }

    public void unregisterWebApp(int webAppId) {
        registeredWebapps.remove(webAppId);
    }

    public void unregisterAllWebApps() {
        registeredWebapps.clear();
    }

    public int getRegisteredWebAppCount() {
        return registeredWebapps.size();
    }

}
