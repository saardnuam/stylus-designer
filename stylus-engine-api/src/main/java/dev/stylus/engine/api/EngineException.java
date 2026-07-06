package dev.stylus.engine.api;

/** Engine failure with a message meant to be surfaced to the user (log pane / dialogs). */
public class EngineException extends RuntimeException {

    public EngineException(String message) {
        super(message);
    }

    public EngineException(String message, Throwable cause) {
        super(message, cause);
    }
}
