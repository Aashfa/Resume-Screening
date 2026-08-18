package dev.lateef.skillgap.parse;

/**
 * The uploaded file is not a format we can parse.
 *
 * <p>Maps to HTTP 415 Unsupported Media Type: the request was well-formed and we
 * understood it, but the payload's format is one we refuse. That is a genuinely different
 * situation from 400 (you sent something malformed) and from 422 (the format was right but
 * the content was unusable), and using the right one lets a client react correctly.
 */
public class UnsupportedFileTypeException extends RuntimeException {

    public UnsupportedFileTypeException(String message) {
        super(message);
    }
}
