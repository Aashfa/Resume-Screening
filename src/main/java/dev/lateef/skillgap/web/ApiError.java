package dev.lateef.skillgap.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * One consistent error shape for every failure the API can return.
 *
 * <p>The value of a single error type is that clients can parse one structure instead of
 * guessing. Spring Boot's built-in error body is fine for a demo, but it exposes whatever
 * the framework felt like including, which varies by exception type.
 *
 * @param timestamp   when the failure happened
 * @param status      HTTP status code, duplicated in the body so logs and captured
 *                    payloads are self-describing
 * @param error       the status reason phrase, e.g. "Bad Request"
 * @param message     a human-readable explanation safe to show a caller
 * @param path        the request path that failed
 * @param fieldErrors field name -> message, present only for validation failures
 */
@JsonInclude(JsonInclude.Include.NON_NULL) // omit fieldErrors entirely when there are none,
                                           // rather than emitting a confusing "fieldErrors": null
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors) {

    public ApiError(Instant timestamp, int status, String error, String message, String path) {
        this(timestamp, status, error, message, path, null);
    }
}
