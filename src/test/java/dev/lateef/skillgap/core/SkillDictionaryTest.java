package dev.lateef.skillgap.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillDictionaryTest {

    @Test
    @DisplayName("the canonical name is usable as an alias without repeating it")
    void canonicalNameIsImplicitlyAnAlias() {
        SkillDictionary dictionary = new SkillDictionary(Map.of("Kafka", List.of()));
        assertThat(dictionary.canonicalForPlainAlias("kafka")).isEqualTo("Kafka");
    }

    @Test
    @DisplayName("aliases are matched lowercase and whitespace-collapsed")
    void normalisesAliases() {
        SkillDictionary dictionary = new SkillDictionary(
                Map.of("Spring Boot", List.of("  Spring    BOOT ")));
        assertThat(dictionary.canonicalForPlainAlias("spring boot")).isEqualTo("Spring Boot");
    }

    @Test
    @DisplayName("punctuation-bearing aliases go to the special bucket, not the n-gram bucket")
    void routesSpecialAliasesSeparately() {
        SkillDictionary dictionary = new SkillDictionary(Map.of("C++", List.of("c++", "cpp")));

        // "cpp" is plain, so the n-gram scanner handles it.
        assertThat(dictionary.canonicalForPlainAlias("cpp")).isEqualTo("C++");
        // "c++" and the canonical "C++" are special.
        assertThat(dictionary.specialAliases())
                .extracting(SkillDictionary.SpecialAlias::alias)
                .contains("c++");
    }

    @Test
    @DisplayName("special aliases are ordered longest first so the specific one wins")
    void ordersSpecialAliasesLongestFirst() {
        SkillDictionary dictionary = new SkillDictionary(Map.of(
                ".NET", List.of(".net"),
                ".NET Core", List.of(".net core")));

        List<String> aliases = dictionary.specialAliases().stream()
                .map(SkillDictionary.SpecialAlias::alias)
                .toList();

        assertThat(aliases.indexOf(".net core")).isLessThan(aliases.indexOf(".net"));
    }

    @Test
    @DisplayName("an alias longer than MAX_NGRAM is rejected loudly instead of silently never matching")
    void rejectsAliasLongerThanMaxNgram() {
        assertThatThrownBy(() -> new SkillDictionary(
                Map.of("AWS", List.of("amazon web services platform engineer"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("could never match");
    }

    @Test
    @DisplayName("a three-word alias is accepted, since that is exactly the window limit")
    void acceptsAliasAtTheLimit() {
        SkillDictionary dictionary = new SkillDictionary(
                Map.of("AWS", List.of("amazon web services")));
        assertThat(dictionary.canonicalForPlainAlias("amazon web services")).isEqualTo("AWS");
    }

    @Test
    @DisplayName("the shipped skills.json loads and is non-trivial")
    void shippedDictionaryLoads() {
        SkillDictionary dictionary = SkillDictionary.loadFromClasspath("/skills.json");
        assertThat(dictionary.canonicalNames()).hasSizeGreaterThan(40);
        assertThat(dictionary.canonicalForPlainAlias("spring boot")).isEqualTo("Spring Boot");
    }

    @Test
    @DisplayName("a missing dictionary file fails fast with a clear message")
    void missingResourceFailsFast() {
        assertThatThrownBy(() -> SkillDictionary.loadFromClasspath("/nope.json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not found on classpath");
    }
}
