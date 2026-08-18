package dev.lateef.skillgap.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Incoming request for the stateless analysis endpoint. Nothing is saved; a request
 * carries everything needed to answer it.
 *
 * <p>A DTO, not a domain object. Keeping the HTTP shape separate from the core types
 * means the API contract can change without touching the algorithm, and the algorithm
 * cannot accidentally leak internal fields to the outside world.
 *
 * <p>Validation lives here as annotations rather than as {@code if} statements in the
 * controller. Spring runs these before your method body is ever entered, so the service
 * layer can assume its input is already well-formed.
 *
 * @param candidateSkills the candidate's skills as typed, e.g. ["Java", "springboot", "C++"]
 * @param jobDescriptionText the raw job advert text, unparsed
 */
public record AnalyzeRequest(

        /*
         * @NotEmpty rejects both null and an empty list.
         * Worth knowing the difference, it is a common interview question:
         *   @NotNull  - must not be null, but "" and [] are fine
         *   @NotEmpty - must not be null AND must have size > 0
         *   @NotBlank - strings only: not null, and not just whitespace
         *
         * The List<@NotBlank String> part is a container element constraint: it applies
         * @NotBlank to every element, so ["Java", "  "] is rejected.
         */
        @NotEmpty(message = "candidateSkills must contain at least one skill")
        @Size(max = 200, message = "candidateSkills must not contain more than 200 entries")
        List<@NotBlank(message = "candidateSkills must not contain blank entries") String> candidateSkills,

        @NotBlank(message = "jobDescriptionText must not be blank")
        @Size(min = 10, max = 50_000,
                message = "jobDescriptionText must be between 10 and 50000 characters")
        String jobDescriptionText
) {
}
