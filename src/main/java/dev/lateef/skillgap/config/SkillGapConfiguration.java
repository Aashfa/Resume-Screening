package dev.lateef.skillgap.config;

import dev.lateef.skillgap.core.MatchScorer;
import dev.lateef.skillgap.core.SkillDictionary;
import dev.lateef.skillgap.core.SkillExtractor;
import dev.lateef.skillgap.parse.PdfTextExtractor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The seam between the framework-free core and Spring.
 *
 * <p>This class is the answer to an obvious question: if {@code SkillExtractor} and
 * {@code MatchScorer} have no {@code @Component} annotation, how does Spring inject them?
 *
 * <p>Two ways exist to make something a Spring bean:
 * <ol>
 *   <li>Annotate the class with {@code @Component} / {@code @Service} and let component
 *       scanning find it. This requires the class to import Spring.</li>
 *   <li>Write a {@code @Bean} factory method here. Spring calls the method and manages
 *       the returned object. <b>The class itself stays completely unaware of Spring.</b></li>
 * </ol>
 * We use (2) on purpose. It is what lets the core package be unit tested with
 * {@code new SkillExtractor(dictionary)} and no application context, while still being
 * injectable into services. The dependency points from Spring towards our core, never
 * the other way round.
 */
@Configuration
public class SkillGapConfiguration {

    /**
     * Loaded once at startup rather than per request. Parsing the JSON on every call
     * would be wasteful, and the dictionary is immutable so sharing it is safe.
     *
     * <p>If the file is missing, {@code loadFromClasspath} throws and the application
     * refuses to start. That is intended: an analyzer with no dictionary would return
     * "0% match, no skills required" for every request, which looks like a working
     * service returning bad answers. Far better to fail at startup than to serve
     * plausible nonsense.
     */
    @Bean
    public SkillDictionary skillDictionary(
            @Value("${skillgap.dictionary-path:/skills.json}") String dictionaryPath) {
        return SkillDictionary.loadFromClasspath(dictionaryPath);
    }

    /** Stateless and thread-safe, so one shared singleton serves every request. */
    @Bean
    public SkillExtractor skillExtractor(SkillDictionary skillDictionary) {
        return new SkillExtractor(skillDictionary);
    }

    /** Also stateless: every method is a pure function of its arguments. */
    @Bean
    public MatchScorer matchScorer() {
        return new MatchScorer();
    }

    /**
     * PDF resume parsing. Stateless, so one shared instance is fine.
     *
     * <p>Same pattern as above: {@code PdfTextExtractor} knows nothing about Spring, and is
     * made available to the container here rather than by annotating the class.
     */
    @Bean
    public PdfTextExtractor pdfTextExtractor() {
        return new PdfTextExtractor();
    }
}
