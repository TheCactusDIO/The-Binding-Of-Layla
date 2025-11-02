package com.layla.ui;

/** Controllers que deseen recibir onEnter()/onExit() automáticos. */
public interface ViewLifecycle {
    /** Llamado justo después de que la vista se coloque en escena. */
    default void onEnter() {}
    /** Llamado justo antes de abandonar la vista actual. */
    default void onExit() {}
}
