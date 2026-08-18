package dev.lateef.skillgap.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for the nested-alias false-gap bug.
 *
 * <p>These use the <b>real</b> {@code skills.json}, on purpose. The bug they cover was
 * invisible to {@link SkillExtractorTest} because that class builds small, tidy
 * dictionaries by hand, and the ambiguity only exists in the shipped one: "spring" maps to
 * Spring Framework while "spring boot" maps to Spring Boot.
 *
 * <p>The lesson generalises. A test fixture cleaner than your production data will hide
 * every bug that only production data can trigger. Test the real dictionary too.
 */
class SkillExtractorOverlapTest {

    private final SkillExtractor extractor =
            new SkillExtractor(SkillDictionary.loadFromClasspath("/skills.json"));

    @Test
    @DisplayName("the longest window wins: 'Spring Boot' is not also reported as Spring Framework")
    void longestWindowWins() {
        assertThat(extractor.extract("We are hiring someone with Spring Boot experience."))
                .contains("Spring Boot")
                .doesNotContain("Spring Framework");
    }

    @Test
    @DisplayName("every alias of a skill canonicalises to the same set: the property the bug broke")
    void aliasesAreConsistent() {
        Set<String> spaced = extractor.extract("Spring Boot");
        Set<String> joined = extractor.extract("springboot");
        Set<String> hyphenated = extractor.extract("spring-boot");

        assertThat(spaced).isEqualTo(joined).isEqualTo(hyphenated);
    }

    @Test
    @DisplayName("no false gap: knowing Spring Boot does not leave Spring Framework missing")
    void noFalseGap() {
        MatchScorer scorer = new MatchScorer();

        Set<String> candidate = extractor.canonicaliseSkillList(List.of("springboot"));
        Set<String> required = extractor.extract("Looking for Spring Boot developers to join us.");

        MatchResult result = scorer.score(candidate, required);

        // Before the fix this was 50.0 with missing = [Spring Framework].
        assertThat(result.matchScore()).isEqualTo(100.0);
        assertThat(result.missing()).isEmpty();
    }

    @Test
    @DisplayName("'Spring Data JPA' beats both 'spring data' and 'spring'")
    void longestOfThreeNestedAliasesWins() {
        Set<String> found = extractor.extract("You will use Spring Data JPA every day.");

        assertThat(found).contains("Spring Data JPA");
        assertThat(found).doesNotContain("Spring Framework");
    }

    @Test
    @DisplayName("'Java Script' is JavaScript, not Java plus JavaScript")
    void javaScriptIsNotAlsoJava() {
        assertThat(extractor.extract("Frontend work in Java Script is involved."))
                .contains("JavaScript")
                .doesNotContain("Java");
    }

    @Test
    @DisplayName("a bare 'Spring' still resolves to Spring Framework")
    void bareSpringStillWorks() {
        // The fix must not break the shorter alias when it genuinely stands alone.
        assertThat(extractor.extract("We use Spring for dependency injection."))
                .contains("Spring Framework");
    }

    @Test
    @DisplayName("two distinct Spring skills in one sentence are both found")
    void adjacentDistinctSkillsBothFound() {
        assertThat(extractor.extract("Experience with Spring Boot and Spring Data JPA required."))
                .contains("Spring Boot", "Spring Data JPA");
    }

    @Test
    @DisplayName("greedy matching does not break the special-character pass")
    void specialCharactersStillWork() {
        Set<String> found = extractor.extract(
                "Roles involve C++, C#, .NET Core and Node.js work alongside Java.");

        assertThat(found).contains("C++", "C#", ".NET Core", "Node.js", "Java");
        assertThat(found).doesNotContain(".NET");
    }

    @Test
    @DisplayName("canonicalising a candidate list is idempotent")
    void canonicalisationIsIdempotent() {
        // Feeding canonical names back in must not change the set. If it did, the score
        // would depend on how many times the data had been through the pipeline.
        Set<String> once = extractor.canonicaliseSkillList(
                List.of("springboot", "core java", "my sql", "c++"));
        Set<String> twice = extractor.canonicaliseSkillList(once);

        assertThat(twice).isEqualTo(once);
    }
}
