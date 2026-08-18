package dev.lateef.skillgap.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class MatchScorerTest {

    private final MatchScorer scorer = new MatchScorer();

    @Nested
    @DisplayName("set arithmetic")
    class SetArithmetic {

        @Test
        @DisplayName("splits skills into matched, missing and extra")
        void splitsIntoThreeSets() {
            MatchResult result = scorer.score(
                    Set.of("Java", "SQL", "Git", "C++"),
                    Set.of("Java", "SQL", "Spring Boot", "REST API"));

            assertThat(result.matched()).containsExactly("Java", "SQL");
            assertThat(result.missing()).containsExactly("REST API", "Spring Boot");
            assertThat(result.extra()).containsExactly("C++", "Git");
        }

        @Test
        @DisplayName("all three lists are sorted alphabetically for a deterministic response")
        void sortsAllLists() {
            MatchResult result = scorer.score(
                    new LinkedHashSet<>(List.of("Zsh", "Ansible", "Maven")),
                    new LinkedHashSet<>(List.of("Maven", "Docker", "Bash")));

            assertThat(result.matched()).containsExactly("Maven");
            assertThat(result.missing()).containsExactly("Bash", "Docker");
            assertThat(result.extra()).containsExactly("Ansible", "Zsh");
        }
    }

    @Nested
    @DisplayName("score calculation")
    class ScoreCalculation {

        @Test
        @DisplayName("full overlap scores 100")
        void fullOverlap() {
            MatchResult result = scorer.score(
                    Set.of("Java", "SQL", "Git"),
                    Set.of("Java", "SQL", "Git"));

            assertThat(result.matchScore()).isEqualTo(100.0);
            assertThat(result.missing()).isEmpty();
            assertThat(result.extra()).isEmpty();
        }

        @Test
        @DisplayName("no overlap scores 0 and every requirement is a gap")
        void noOverlap() {
            MatchResult result = scorer.score(
                    Set.of("C++", "C#"),
                    Set.of("Java", "Spring Boot"));

            assertThat(result.matchScore()).isEqualTo(0.0);
            assertThat(result.matched()).isEmpty();
            assertThat(result.missing()).containsExactly("Java", "Spring Boot");
            assertThat(result.extra()).containsExactly("C#", "C++");
        }

        @Test
        @DisplayName("partial overlap scores the covered fraction")
        void partialOverlap() {
            MatchResult result = scorer.score(
                    Set.of("Java", "SQL"),
                    Set.of("Java", "SQL", "Spring Boot", "REST API"));

            assertThat(result.matchScore()).isEqualTo(50.0);
        }

        @Test
        @DisplayName("score is rounded to one decimal place")
        void roundsToOneDecimal() {
            // 1 of 3 = 33.333... -> 33.3
            MatchResult result = scorer.score(
                    Set.of("Java"),
                    Set.of("Java", "SQL", "Git"));

            assertThat(result.matchScore()).isCloseTo(33.3, within(0.0001));
        }

        @Test
        @DisplayName("the denominator is the job's requirements, not the candidate's skill count")
        void denominatorIsRequirements() {
            // Candidate has 10 skills but the job asked for 2, one of which they have.
            MatchResult result = scorer.score(
                    Set.of("Java", "A", "B", "C", "D", "E", "F", "G", "H", "I"),
                    Set.of("Java", "Spring Boot"));

            assertThat(result.matchScore()).isEqualTo(50.0);
        }
    }

    @Nested
    @DisplayName("extra skills are reported but never scored")
    class ExtraSkillsPolicy {

        @Test
        @DisplayName("extra skills do not raise the score")
        void extrasDoNotRaiseScore() {
            MatchResult withoutExtras = scorer.score(
                    Set.of("Java", "SQL"),
                    Set.of("Java", "SQL", "Spring Boot", "REST API"));

            Set<String> withPadding = new LinkedHashSet<>(Set.of("Java", "SQL"));
            for (int i = 0; i < 45; i++) {
                withPadding.add("Irrelevant Skill " + i);
            }
            MatchResult withExtras = scorer.score(withPadding,
                    Set.of("Java", "SQL", "Spring Boot", "REST API"));

            assertThat(withExtras.matchScore()).isEqualTo(withoutExtras.matchScore());
            assertThat(withExtras.extraSkillCount()).isEqualTo(45);
        }

        @Test
        @DisplayName("CV padding cannot beat a perfect match: the anomaly we designed out")
        void paddingCannotOutrankAPerfectMatch() {
            Set<String> required = Set.of("Java", "Spring Boot", "SQL", "REST API", "Git");

            MatchResult perfect = scorer.score(required, required);

            Set<String> padded = new LinkedHashSet<>(Set.of("Java", "Spring Boot", "SQL"));
            for (int i = 0; i < 45; i++) {
                padded.add("Padding " + i);
            }
            MatchResult keywordStuffed = scorer.score(padded, required);

            assertThat(perfect.matchScore()).isEqualTo(100.0);
            assertThat(keywordStuffed.matchScore()).isEqualTo(60.0);
            assertThat(keywordStuffed.matchScore()).isLessThan(perfect.matchScore());
        }

        @Test
        @DisplayName("the score can never exceed 100")
        void scoreIsCappedByDefinition() {
            Set<String> huge = new LinkedHashSet<>(Set.of("Java"));
            for (int i = 0; i < 500; i++) {
                huge.add("Skill " + i);
            }
            assertThat(scorer.score(huge, Set.of("Java")).matchScore()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("extraSkillCount always agrees with the extra list")
        void extraCountMatchesList() {
            MatchResult result = scorer.score(
                    Set.of("Java", "Docker", "Kafka"),
                    Set.of("Java"));

            assertThat(result.extraSkillCount()).isEqualTo(result.extra().size()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        @DisplayName("empty candidate skills scores 0 and everything is a gap")
        void emptyCandidate() {
            MatchResult result = scorer.score(Set.of(), Set.of("Java", "SQL"));

            assertThat(result.matchScore()).isEqualTo(0.0);
            assertThat(result.matched()).isEmpty();
            assertThat(result.missing()).containsExactly("Java", "SQL");
            assertThat(result.extra()).isEmpty();
        }

        @Test
        @DisplayName("no requirements extracted reports 0, not a misleading 100")
        void emptyRequirements() {
            MatchResult result = scorer.score(Set.of("Java", "SQL"), Set.of());

            // Vacuously the candidate satisfies everything, but reporting "100% match"
            // for a job description we extracted nothing from would read as a perfect
            // candidate. The empty missing list is what tells the two cases apart.
            assertThat(result.matchScore()).isEqualTo(0.0);
            assertThat(result.missing()).isEmpty();
            assertThat(result.extra()).containsExactly("Java", "SQL");
        }

        @Test
        @DisplayName("both sides empty does not divide by zero")
        void bothEmpty() {
            MatchResult result = scorer.score(Set.of(), Set.of());

            assertThat(result.matchScore()).isEqualTo(0.0);
            assertThat(result.matched()).isEmpty();
            assertThat(result.missing()).isEmpty();
            assertThat(result.extra()).isEmpty();
        }

        @Test
        @DisplayName("null inputs are treated as empty rather than throwing")
        void nullsAreTolerated() {
            assertThat(scorer.score(null, null).matchScore()).isEqualTo(0.0);
            assertThat(scorer.score(null, Set.of("Java")).missing()).containsExactly("Java");
            assertThat(scorer.score(Set.of("Java"), null).extra()).containsExactly("Java");
        }
    }

    @Nested
    @DisplayName("result immutability")
    class Immutability {

        @Test
        @DisplayName("returned lists cannot be mutated by the caller")
        void listsAreUnmodifiable() {
            MatchResult result = scorer.score(Set.of("Java"), Set.of("Java", "SQL"));

            assertThatThrownBy(() -> result.matched().add("Injected"))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> result.missing().add("Injected"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("integration with the extractor: both sides share one vocabulary")
    class SharedVocabulary {

        @Test
        @DisplayName("candidate aliases are canonicalised before comparison")
        void aliasesOnBothSidesLineUp() {
            SkillExtractor extractor = new SkillExtractor(
                    SkillDictionary.loadFromClasspath("/skills.json"));

            // The candidate types "springboot"; the advert says "Spring Boot".
            // Without canonicalising both sides this would be a false gap.
            Set<String> candidate = extractor.canonicaliseSkillList(
                    List.of("springboot", "core java", "my sql"));
            Set<String> required = extractor.extract(
                    "We need Spring Boot, Java and MySQL experience.");

            MatchResult result = scorer.score(candidate, required);

            assertThat(result.matched()).contains("Spring Boot", "Java", "MySQL");
            assertThat(result.missing()).doesNotContain("Spring Boot", "Java", "MySQL");
        }

        @Test
        @DisplayName("an unrecognised candidate skill survives as an extra rather than vanishing")
        void unknownCandidateSkillBecomesExtra() {
            SkillExtractor extractor = new SkillExtractor(
                    SkillDictionary.loadFromClasspath("/skills.json"));

            Set<String> candidate = extractor.canonicaliseSkillList(List.of("Java", "Photoshop"));
            Set<String> required = extractor.extract("Java developer wanted.");

            MatchResult result = scorer.score(candidate, required);

            assertThat(result.matched()).contains("Java");
            assertThat(result.extra()).contains("Photoshop");
        }
    }
}
