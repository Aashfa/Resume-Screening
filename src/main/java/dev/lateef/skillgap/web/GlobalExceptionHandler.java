package dev.lateef.skillgap.web;

import dev.lateef.skillgap.parse.TextExtractionException;
import dev.lateef.skillgap.parse.UnsupportedFileTypeException;
import dev.lateef.skillgap.service.NoSkillsExtractedException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralised error handling for every controller in the application.
 *
 * <p>{@code @RestControllerAdvice} is {@code @ControllerAdvice} plus
 * {@code @ResponseBody}: the returned objects are serialised as JSON instead of being
 * treated as view names. Spring registers these handlers globally, so an exception thrown
 * anywhere below the controller is routed to the matching method here.
 *
 * <p>The reason this class exists rather than try/catch blocks in each controller:
 * <ul>
 *   <li>Error shape is defined once, so every failure looks the same to a client.</li>
 *   <li>Controllers stay readable, containing only the happy path.</li>
 *   <li>Adding a new error case is one method here, not an edit to every endpoint.</li>
 * </ul>
 *
 * <p>Handlers are matched most-specific-first, so the catch-all {@code Exception} method
 * at the bottom only runs when nothing above it applies.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Thrown by Spring when {@code @Valid} finds constraint violations on a request body.
     * Returns 400 with a field-by-field breakdown, so the caller learns everything that is
     * wrong in one round trip instead of fixing one problem at a time.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationErrors(MethodArgumentNotValidException ex,
                                                           HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        // Constraints not tied to one field, e.g. class-level checks.
        ex.getBindingResult().getGlobalErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));

        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Request validation failed",
                request.getRequestURI(),
                fieldErrors);

        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Malformed JSON, or a body that cannot be bound to the target type at all.
     * We deliberately do not echo the parser's internal message, which can leak class
     * names and package structure.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                         HttpServletRequest request) {
        log.warn("Rejected malformed request body on {}: {}", request.getRequestURI(), ex.getMessage());

        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Request body is missing or is not valid JSON",
                request.getRequestURI());

        return ResponseEntity.badRequest().body(body);
    }

    /**
     * The job description parsed fine but contained nothing we recognise.
     *
     * <p>422 Unprocessable Entity rather than 400: the request was syntactically valid and
     * we understood it. We simply could not do anything useful with the content. A 400
     * would wrongly suggest the client formatted something incorrectly.
     */
    @ExceptionHandler(NoSkillsExtractedException.class)
    public ResponseEntity<ApiError> handleNoSkillsExtracted(NoSkillsExtractedException ex,
                                                            HttpServletRequest request) {
        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI());

        return ResponseEntity.unprocessableEntity().body(body);
    }

    /**
     * The upload was not a PDF.
     *
     * <p>415 Unsupported Media Type, which is specifically "I understand the request but
     * refuse this payload format". Returning 400 here would tell the client it had
     * malformed its request, which is not what happened.
     */
    @ExceptionHandler(UnsupportedFileTypeException.class)
    public ResponseEntity<ApiError> handleUnsupportedFileType(UnsupportedFileTypeException ex,
                                                              HttpServletRequest request) {
        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI());

        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(body);
    }

    /**
     * The file was a PDF but its text could not be read: encrypted, corrupt, or a scan.
     *
     * <p>These messages are written for a user and say what to do next, so unlike the
     * catch-all below we pass {@code ex.getMessage()} straight through. That is safe here
     * precisely because we wrote every one of those strings ourselves.
     */
    @ExceptionHandler(TextExtractionException.class)
    public ResponseEntity<ApiError> handleTextExtractionFailure(TextExtractionException ex,
                                                                HttpServletRequest request) {
        log.warn("Resume text extraction failed on {}: {}", request.getRequestURI(), ex.getMessage());

        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI());

        return ResponseEntity.unprocessableEntity().body(body);
    }

    /**
     * The upload exceeded {@code spring.servlet.multipart.max-file-size}.
     *
     * <p>Spring raises this before the controller runs, so the oversized file is never
     * fully buffered by our code.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadTooLarge(MaxUploadSizeExceededException ex,
                                                         HttpServletRequest request) {
        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.PAYLOAD_TOO_LARGE.value(),
                HttpStatus.PAYLOAD_TOO_LARGE.getReasonPhrase(),
                "The uploaded file is too large. The limit is 5 MB.",
                request.getRequestURI());

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(body);
    }

    /** A required multipart part was absent, e.g. the form posted no file at all. */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiError> handleMissingPart(MissingServletRequestPartException ex,
                                                      HttpServletRequest request) {
        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Required part '" + ex.getRequestPartName() + "' is missing from the request",
                request.getRequestURI());

        return ResponseEntity.badRequest().body(body);
    }

    /** Manual argument checks in the controller, e.g. a blank job description on the upload path. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex,
                                                          HttpServletRequest request) {
        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI());

        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Anything unforeseen. The stack trace goes to the log, where developers can see it;
     * the client gets a generic message. Echoing {@code ex.getMessage()} here is a common
     * mistake that leaks internal detail to callers.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {}", request.getRequestURI(), ex);

        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "An unexpected internal error occurred",
                request.getRequestURI());

        return ResponseEntity.internalServerError().body(body);
    }
}
