package com.layla.core;

/** Pequeña utilidad para flags de entorno de test/headless. */
public final class TestEnv {
    private TestEnv() {}
    public static boolean isHeadless() {
        return Boolean.getBoolean("testfx.headless");
    }
    public static boolean disableMedia() {
        // Puedes usar cualquiera de los dos flags
        return Boolean.getBoolean("testfx.headless") || Boolean.getBoolean("testfx.disableMedia");
    }
}
