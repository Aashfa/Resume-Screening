package dev.lateef.skillgap.web;

import dev.lateef.skillgap.dto.AnalyzeRequest;
import dev.lateef.skillgap.dto.AnalyzeResponse;
import dev.lateef.skillgap.parse.TextExtractionException;
import dev.lateef.skillgap.service.AnalysisService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * HTTP entry point. Its entire job is translating between HTTP and the service layer:
 * no business logic, no calculations, no validation code.
 *
 * <p>If you find yourself writing an {@code if} in a controller that is not about HTTP,
 * it probably belongs in the service.
 */
@RestController
@RequestMapping("/api")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    /**
     * Analyses raw skills against raw job description text in a single call.
     *
     * <p>Stateless by design: nothing has to be created or saved first, which makes manual
     * testing with curl a one-liner.
     *
     * <p>{@code @Valid} is the important annotation. It tells Spring to run the Bean
     * Validation constraints declared on {@link AnalyzeRequest} <em>before</em> this method
     * body runs. On failure Spring throws {@code MethodArgumentNotValidException}, which
     * never reaches here: {@link GlobalExceptionHandler} converts it to a 400. That is why
     * there is not a single null check in this method.
     */
    @PostMapping("/analyze")
    public AnalyzeResponse analyze(@Valid @RequestBody AnalyzeRequest request) {
        return analysisService.analyze(request);
    }

    /**
     * Analyses an uploaded PDF resume against a job description.
     *
     * <p>Consumes {@code multipart/form-data} rather than JSON, because JSON has no binary
     * type. Sending a file as base64 inside a JSON body is possible but inflates it by
     * about a third and buffers the whole thing as a string. Multipart is what the format
     * is for, and what a plain HTML {@code <form>} sends natively.
     *
     * <p><b>Note the two different annotations, which are not interchangeable.</b>
     * <ul>
     *   <li>{@code @RequestPart} for the file. It resolves a genuine multipart part,
     *       consulting the configured message converters for its content type. Correct for
     *       binary uploads, and a missing part raises
     *       {@code MissingServletRequestPartException}, which we handle by name.</li>
     *   <li>{@code @RequestParam} for the text. A browser calling
     *       {@code formData.append("jobDescriptionText", text)} sends an ordinary form
     *       field, <em>not</em> a typed part. Annotating it {@code @RequestPart} makes
     *       Spring look for a part that is not there and reject every request with 400.
     *       This exact mistake caused seven failing tests while building this endpoint.</li>
     * </ul>
     *
     * <p>Validation here is imperative rather than annotation-driven. Bean Validation
     * shines on a JSON object bound to a DTO; for a two-field upload the checks are few and
     * reading them inline is clearer than inventing a wrapper type to hang annotations on.
     * Knowing when <em>not</em> to reach for the framework feature matters as much as
     * knowing it exists.
     */
    @PostMapping(value = "/analyze/resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AnalyzeResponse analyzeResume(
            @RequestPart("file") MultipartFile file,
            @RequestParam("jobDescriptionText") String jobDescriptionText) throws IOException {

        if (file.isEmpty()) {
            throw new TextExtractionException("No file was uploaded, or the file is empty.");
        }
        if (jobDescriptionText == null || jobDescriptionText.isBlank()) {
            throw new IllegalArgumentException("jobDescriptionText must not be blank");
        }
        if (jobDescriptionText.length() < 10) {
            throw new IllegalArgumentException(
                    "jobDescriptionText must be at least 10 characters");
        }

        return analysisService.analyzeResume(
                file.getBytes(), file.getOriginalFilename(), jobDescriptionText);
    }

    /** Every skill the dictionary recognises. Handy for seeing what the tool can detect. */
    @GetMapping("/skills")
    public Map<String, Object> knownSkills() {
        List<String> skills = analysisService.knownSkills();
        return Map.of("count", skills.size(), "skills", skills);
    }

    /** Liveness check. */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
