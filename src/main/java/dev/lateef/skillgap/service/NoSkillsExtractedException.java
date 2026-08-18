package dev.lateef.skillgap.service;

/**
 * Thrown when the job description text contained no skill this dictionary recognises.
 *
 * <p>Why an exception rather than returning a 0% score: a score of 0 with an empty
 * missing-skills list is indistinguishable, to whoever reads the response, from "this
 * candidate matches nothing". The truth is different and more useful, namely "we could
 * not find anything to score against". Those deserve different HTTP statuses, so the
 * caller can tell a bad candidate from an unparseable input.
 *
 * <p>Extends {@link RuntimeException} rather than {@link Exception} so it need not be
 * declared on every method signature between here and the controller. The
 * {@code @RestControllerAdvice} catches it centrally.
 */
public class NoSkillsExtractedException extends RuntimeException {

    public NoSkillsExtractedException(String message) {
        super(message);
    }
}
