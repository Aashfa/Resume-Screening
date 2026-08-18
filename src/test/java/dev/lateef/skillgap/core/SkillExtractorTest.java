package dev.lateef.skillgap.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Note what is NOT here: no @SpringBootTest, no @Autowired, no application context.
 * These are plain JUnit 5 tests over plain Java objects, which is exactly why the
 * extraction logic was kept free of Spring. The whole class runs in milliseconds.
 */
class SkillExtractorTest {

    /** A small hand-built dictionary. Tests should not depend on the shipped skills.json. */
    private static SkillExtractor extractorWith(Map<String, List<String>> entries) {
        return new SkillExtractor(new SkillDictionary(entries));
    }

    private static final SkillExtractor DEFAULT = extractorWith(Map.of(
            "Java", List.of("java", "core java"),
            "Spring Boot", List.of("spring boot", "springboot"),
            "Spring Data JPA", List.of("spring data jpa"),
            "REST API", List.of("rest", "rest api", "restful"),
            "SQL", List.of("sql"),
            "Git", List.of("git")
    ));

    @Nested
    @DisplayName("n-gram window matching")
    class NgramMatching {

        @Test
        @DisplayName("finds a single-word skill")
        void findsUnigram() {
            assertThat(DEFAULT.extract("We are looking for a Java developer."))
                    .containsExactlyInAnyOrder("Java");
        }

        @Test
        @DisplayName("finds a two-word skill that a one-word scanner would miss")
        void findsBigram() {
            assertThat(DEFAULT.extract("Experience with Spring Boot is required."))
                    .contains("Spring Boot");
        }

        @Test
        @DisplayName("finds a three-word skill, the longest window we support")
        void findsTrigram() {
            assertThat(DEFAULT.extract("You will use Spring Data JPA daily."))
                    .contains("Spring Data JPA");
        }

        @Test
        @DisplayName("matching is case insensitive")
        void isCaseInsensitive() {
            assertThat(DEFAULT.extract("JAVA and SpRiNg BoOt"))
                    .containsExactlyInAnyOrder("Java", "Spring Boot");
        }

        @Test
        @DisplayName("punctuation attached to a word does not prevent a match")
        void handlesAttachedPunctuation() {
            assertThat(DEFAULT.extract("Skills: Java, SQL; Git."))
                    .containsExactlyInAnyOrder("Java", "SQL", "Git");
        }

        @Test
        @DisplayName("a skill is reported once no matter how often it appears")
        void deduplicates() {
            assertThat(DEFAULT.extract("Java Java Java, and more Java"))
                    .containsExactly("Java");
        }

        @Test
        @DisplayName("does not match a skill embedded inside a longer word")
        void doesNotMatchInsideAnotherWord() {
            // "javascript" tokenises as one word, so the "java" window never occurs.
            assertThat(DEFAULT.extract("We use javascript on the frontend."))
                    .doesNotContain("Java");
        }

        @Test
        @DisplayName("aliases collapse onto the canonical name")
        void mapsAliasesToCanonicalName() {
            assertThat(DEFAULT.extract("springboot and restful services"))
                    .containsExactlyInAnyOrder("Spring Boot", "REST API");
        }
    }

    @Nested
    @DisplayName("special-character pass")
    class SpecialCharacters {

        private final SkillExtractor extractor = extractorWith(Map.of(
                "C++", List.of("c++", "cpp"),
                "C#", List.of("c#", "csharp"),
                ".NET Core", List.of(".net core"),
                ".NET", List.of(".net"),
                "ASP.NET", List.of("asp.net"),
                "Node.js", List.of("node.js", "nodejs"),
                "Java", List.of("java")
        ));

        @Test
        @DisplayName("finds C++, which a plain tokeniser would reduce to \"c\"")
        void findsCPlusPlus() {
            assertThat(extractor.extract("Strong C++ background required."))
                    .contains("C++");
        }

        @Test
        @DisplayName("finds C#")
        void findsCSharp() {
            assertThat(extractor.extract("Built services in C# for three years."))
                    .contains("C#");
        }

        @Test
        @DisplayName("distinguishes C++ from C#")
        void distinguishesCPlusPlusFromCSharp() {
            Set<String> found = extractor.extract("We use C++ for the engine and C# for tooling.");
            assertThat(found).contains("C++", "C#");
        }

        @Test
        @DisplayName("finds .NET Core and does not also report bare .NET")
        void prefersLongerSpecialAlias() {
            // Longest-first ordering plus blanking is what makes this work: ".net core"
            // is consumed before ".net" is ever tried against that span.
            Set<String> found = extractor.extract("Migrating the platform to .NET Core this year.");
            assertThat(found).contains(".NET Core");
            assertThat(found).doesNotContain(".NET");
        }

        @Test
        @DisplayName("does not match .NET inside ASP.NET")
        void respectsLeftBoundary() {
            Set<String> found = extractor.extract("The team maintains an ASP.NET application.");
            assertThat(found).contains("ASP.NET");
            assertThat(found).doesNotContain(".NET");
        }

        @Test
        @DisplayName("finds Node.js")
        void findsNodeJs() {
            assertThat(extractor.extract("Some Node.js exposure is a plus."))
                    .contains("Node.js");
        }

        @Test
        @DisplayName("blanking prevents the leftover of C++ being re-read in pass 2")
        void blankingPreventsPhantomMatches() {
            // This is the regression guard for the whole two-pass design. If pass 1 did
            // not blank its hits, normalising "c++" would leave a stray "c" token behind.
            SkillExtractor withBareC = extractorWith(Map.of(
                    "C++", List.of("c++"),
                    "C", List.of("c")
            ));
            Set<String> found = withBareC.extract("We write C++ here.");
            assertThat(found).contains("C++");
            assertThat(found).doesNotContain("C");
        }
    }

    @Nested
    @DisplayName("input handling")
    class InputHandling {

        @Test
        @DisplayName("null text yields an empty set rather than throwing")
        void handlesNull() {
            assertThat(DEFAULT.extract(null)).isEmpty();
        }

        @Test
        @DisplayName("blank text yields an empty set")
        void handlesBlank() {
            assertThat(DEFAULT.extract("   \n\t  ")).isEmpty();
        }

        @Test
        @DisplayName("text with no known skills yields an empty set")
        void handlesNoMatches() {
            assertThat(DEFAULT.extract("We are a fast paced team that values curiosity."))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("realistic job description")
    class RealisticText {

        @Test
        @DisplayName("extracts the expected skill set from a full advert")
        void extractsFromFullAdvert() {
            SkillExtractor extractor = new SkillExtractor(
                    SkillDictionary.loadFromClasspath("/skills.json"));

            String jd = """
                    Trainee Software Developer (Java)

                    We are looking for a graduate with a solid grounding in Java and
                    object oriented programming. You will help build RESTful APIs with
                    Spring Boot, backed by Spring Data JPA and MySQL. Familiarity with
                    Git and Maven is expected. Exposure to Docker, C++ or C# is a bonus,
                    and any .NET Core or Node.js experience will be considered.
                    """;

            assertThat(extractor.extract(jd)).contains(
                    "Java", "OOP", "REST API", "Spring Boot", "Spring Data JPA",
                    "MySQL", "Git", "Maven", "Docker", "C++", "C#", ".NET Core", "Node.js");
        }
    }
}
