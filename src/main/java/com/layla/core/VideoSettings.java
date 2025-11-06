package com.layla.core;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

/**
 * Singleton that stores the virtual or "world" resolution used by the camera/viewport.
 */
public final class VideoSettings {

    private static final VideoSettings INSTANCE = new VideoSettings();

    private final IntegerProperty virtualWidth = new SimpleIntegerProperty(1920);
    private final IntegerProperty virtualHeight = new SimpleIntegerProperty(1080);

    private VideoSettings() {}

    public static VideoSettings get() {return INSTANCE;}

    public int getVirtualWidth() {return virtualWidth.get();}

    public void setVirtualWidth(int width) {virtualWidth.set(width);}

    public IntegerProperty virtualWidthProperty() {return virtualWidth;}

    public int getVirtualHeight() {return virtualHeight.get();}

    public void setVirtualHeight(int height) {virtualHeight.set(height);}

    public IntegerProperty virtualHeightProperty() {return virtualHeight;}

    public void setResolution(int width, int height) {
        setVirtualWidth(width);
        setVirtualHeight(height);
    }
}
