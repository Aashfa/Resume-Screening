package dev.lateef.skillgap.parse;

/**
 * The file was the right format but its text could not be read: encrypted, corrupt, or an
 * image-only scan with no text layer.
 *
 * <p>Maps to HTTP 422 Unprocessable Entity. The messages carried by this exception are
 * written to be shown to a user and to tell them what to do next, so
 * {@code GlobalExceptionHandler} passes them through rather than replacing them with a
 * generic string.
 */
public class TextExtractionException extends RuntimeException {

    public TextExtractionException(String message) {
        super(message);
    }

    public TextExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
