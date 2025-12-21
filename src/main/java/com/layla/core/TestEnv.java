package com.layla.core;

/**
 * Utilidad para detectar flags de entorno relacionados con tests o ejecución headless.
 *
 * Se usa típicamente para:
 * - Evitar inicializar JavaFX Media en entornos donde falla (CI, headless, sin codecs).
 * - Desactivar música/vídeo durante pruebas automatizadas.
 *
 * Flags soportados (por properties de JVM):
 * - -Dtestfx.headless=true
 * - -Dtestfx.disableMedia=true
 */
public final class TestEnv {

    private static final String HEADLESS_FLAG = "testfx.headless";
    private static final String DISABLE_MEDIA_FLAG = "testfx.disableMedia";

    private TestEnv() {}

    /**
     * Indica si estamos en modo headless (sin entorno gráfico).
     */
    public static boolean isHeadless() {
        return Boolean.getBoolean(HEADLESS_FLAG);
    }

    /**
     * Indica si debemos desactivar Media (música/vídeo/SFX con MediaPlayer).
     * Útil para evitar errores en máquinas sin soporte multimedia.
     */
    public static boolean disableMedia() {
        return isHeadless() || Boolean.getBoolean(DISABLE_MEDIA_FLAG);
    }
}
