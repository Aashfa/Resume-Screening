package dev.lateef.skillgap.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-layer tests: status codes, JSON shape, validation and error handling.
 *
 * <p>Unlike the core tests, these <em>do</em> boot Spring, because the thing under test is
 * the Spring wiring itself: request mapping, {@code @Valid}, Jackson serialisation and the
 * {@code @RestControllerAdvice}. That is also why there are relatively few of them. The
 * algorithm's behaviour is covered far more cheaply in the core test classes; duplicating
 * it here would only make the suite slower without testing anything new.
 *
 * <p>{@code MockMvc} sends requests through the full Spring MVC stack without opening a
 * real socket, so these run faster than a {@code webEnvironment = RANDOM_PORT} test.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String JD = "We are looking for a Java developer with Spring Boot, "
            + "SQL and Git experience to join our backend team.";

    private static String body(String skillsJson, String jdText) {
        return "{\"candidateSkills\":" + skillsJson + ",\"jobDescriptionText\":\"" + jdText + "\"}";
    }

    @Test
    @DisplayName("POST /api/analyze returns 200 with the full result shape")
    void analyzeReturnsResult() throws Exception {
        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("[\"Java\",\"SQL\"]", JD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchScore").exists())
                .andExpect(jsonPath("$.matchedSkills").isArray())
                .andExpect(jsonPath("$.missingSkills").isArray())
                .andExpect(jsonPath("$.extraSkills").isArray())
                .andExpect(jsonPath("$.extraSkillCount").exists())
                .andExpect(jsonPath("$.requiredSkillCount").exists())
                .andExpect(jsonPath("$.candidateSkillCount").exists());
    }

    @Test
    @DisplayName("full overlap scores 100 with an empty gap")
    void fullOverlapScores100() throws Exception {
        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("[\"Java\",\"Git\"]",
                                "We need a Java developer who knows Git well.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchScore").value(100.0))
                .andExpect(jsonPath("$.missingSkills").isEmpty());
    }

    @Test
    @DisplayName("no overlap scores 0 and every requirement appears in the gap")
    void noOverlapScoresZero() throws Exception {
        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("[\"Photoshop\"]",
                                "We need a Java developer who knows Git well.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchScore").value(0.0))
                .andExpect(jsonPath("$.matchedSkills").isEmpty())
                .andExpect(jsonPath("$.missingSkills.length()").value(2))
                .andExpect(jsonPath("$.extraSkills[0]").value("Photoshop"));
    }

    @Test
    @DisplayName("extra skills are reported but do not inflate the score")
    void extrasDoNotInflateScore() throws Exception {
        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("[\"Java\",\"Kubernetes\",\"Kafka\",\"Redis\"]",
                                "We need a Java developer for this role.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchScore").value(100.0))
                .andExpect(jsonPath("$.extraSkillCount").value(3));
    }

    @Test
    @DisplayName("empty candidateSkills is rejected with 400 and a field error")
    void emptySkillsIsRejected() throws Exception {
        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("[]", JD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors.candidateSkills").exists());
    }

    @Test
    @DisplayName("a blank entry inside the list is rejected by the container element constraint")
    void blankSkillEntryIsRejected() throws Exception {
        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("[\"Java\",\"   \"]", JD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['candidateSkills[1]']").exists());
    }

    @Test
    @DisplayName("a too-short job description is rejected with 400")
    void shortJobDescriptionIsRejected() throws Exception {
        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("[\"Java\"]", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.jobDescriptionText").exists());
    }

    @Test
    @DisplayName("malformed JSON returns 400 without leaking parser internals")
    void malformedJsonIsRejected() throws Exception {
        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateSkills\":[\"Java\",}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Request body is missing or is not valid JSON"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    @DisplayName("a job description with no known skills returns 422, not 400 and not a fake 0%")
    void unrecognisableJobDescriptionReturns422() throws Exception {
        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("[\"Java\"]",
                                "We are a fast paced team that values curiosity and empathy.")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("No known skills were found")));
    }

    @Test
    @DisplayName("errors omit fieldErrors entirely rather than emitting null")
    void errorBodyOmitsNullFieldErrors() throws Exception {
        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("[\"Java\"]", "Nothing technical in this sentence at all.")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/api/analyze"));
    }

    @Test
    @DisplayName("candidate aliases are canonicalised, so no false gap is reported")
    void aliasesDoNotCreateFalseGaps() throws Exception {
        mockMvc.perform(post("/api/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("[\"springboot\"]",
                                "Looking for Spring Boot developers to join the team.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchScore").value(100.0))
                .andExpect(jsonPath("$.missingSkills").isEmpty());
    }

    @Test
    @DisplayName("GET /api/skills lists the dictionary")
    void listsKnownSkills() throws Exception {
        mockMvc.perform(get("/api/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").exists())
                .andExpect(jsonPath("$.skills").isArray());
    }

    @Test
    @DisplayName("GET /api/health reports UP")
    void healthIsUp() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
