package dev.lateef.skillgap.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * An immutable lookup table of canonical skill names and the aliases people
 * actually write in job adverts.
 *
 * <p>There is no Spring in this file, and there is no Spring in this package.
 * That is deliberate: the whole matching engine can be unit tested by calling
 * {@code new SkillDictionary(map)} directly, with no application context to
 * start up. A test that boots Spring takes seconds; these take milliseconds.
 *
 * <p>Aliases are split into two buckets at construction time, because they need
 * two completely different matching strategies:
 * <ul>
 *   <li><b>plain</b> &mdash; letters, digits and spaces only ("spring boot").
 *       These survive word-tokenisation, so the n-gram scanner handles them.</li>
 *   <li><b>special</b> &mdash; contains punctuation that a tokeniser destroys
 *       ("c++", "c#", ".net core", "node.js"). These need a literal substring
 *       scan <em>before</em> the text is normalised.</li>
 * </ul>
 */
public final class SkillDictionary {

    /**
     * Longest phrase, in words, that the n-gram scanner will consider.
     * See {@link SkillExtractor} for why this is 3.
     */
    public static final int MAX_NGRAM = 3;

    /** An alias whose punctuation means it cannot be found by word-tokenising. */
    public record SpecialAlias(String alias, String canonical) {
    }

    /** normalised plain alias -> canonical skill name. e.g. "spring boot" -> "Spring Boot" */
    private final Map<String, String> plainAliases;

    /** Special aliases, pre-sorted longest-first. Order matters; see below. */
    private final List<SpecialAlias> specialAliases;

    /** Every canonical name in the dictionary, for validation and reporting. */
    private final Set<String> canonicalNames;

    /**
     * @param source canonical skill name -> list of aliases. The canonical name is
     *               always treated as an alias of itself, so you never have to repeat it.
     * @throws IllegalArgumentException if a plain alias is longer than {@link #MAX_NGRAM}
     *                                  words, because such an alias could never match and
     *                                  failing loudly beats failing silently.
     */
    public SkillDictionary(Map<String, List<String>> source) {
        Objects.requireNonNull(source, "source");

        Map<String, String> plain = new HashMap<>();
        List<SpecialAlias> special = new ArrayList<>();
        Set<String> canonicals = new LinkedHashSet<>();

        source.forEach((canonical, aliases) -> {
            if (canonical == null || canonical.isBlank()) {
                throw new IllegalArgumentException("Canonical skill name must not be blank");
            }
            canonicals.add(canonical);

            // The canonical name is implicitly an alias of itself.
            List<String> all = new ArrayList<>();
            all.add(canonical);
            if (aliases != null) {
                all.addAll(aliases);
            }

            for (String alias : all) {
                if (alias == null || alias.isBlank()) {
                    continue;
                }
                String lower = alias.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");

                if (containsSpecialCharacter(lower)) {
                    special.add(new SpecialAlias(lower, canonical));
                } else {
                    int wordCount = lower.split(" ").length;
                    if (wordCount > MAX_NGRAM) {
                        throw new IllegalArgumentException(
                                "Alias '" + alias + "' for skill '" + canonical + "' has " + wordCount
                                        + " words, but the n-gram scanner only looks at windows of up to "
                                        + MAX_NGRAM + ". It could never match. Shorten it, or raise MAX_NGRAM.");
                    }
                    // First writer wins. If two skills claim the same alias that is a
                    // dictionary bug, but we must not blow up at runtime over it.
                    plain.putIfAbsent(lower, canonical);
                }
            }
        });

        // Longest first. This is what makes ".net core" win over ".net", and "c++" win
        // over a hypothetical "c". Without this ordering, the shorter alias would consume
        // the text first and the longer, more specific skill would be lost.
        special.sort(Comparator.comparingInt((SpecialAlias s) -> s.alias().length()).reversed());

        this.plainAliases = Map.copyOf(plain);
        this.specialAliases = List.copyOf(special);
        this.canonicalNames = Set.copyOf(canonicals);
    }

    /**
     * Loads a dictionary from a JSON resource on the classpath, in the shape
     * {@code { "Canonical Name": ["alias", "alias"], ... }}.
     *
     * <p>Uses Jackson, which is a JSON library, not a Spring class. This method still
     * runs perfectly well in a plain JUnit test with no application context.
     */
    public static SkillDictionary loadFromClasspath(String resourcePath) {
        try (InputStream in = SkillDictionary.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Skill dictionary not found on classpath: " + resourcePath);
            }
            Map<String, List<String>> raw =
                    new ObjectMapper().readValue(in, new TypeReference<Map<String, List<String>>>() {
                    });
            return new SkillDictionary(raw);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read skill dictionary: " + resourcePath, e);
        }
    }

    /**
     * True if the alias contains anything other than a lowercase letter, digit or space,
     * which is exactly the set of characters that {@link SkillExtractor}'s normaliser
     * would replace with a space, and therefore destroy.
     */
    private static boolean containsSpecialCharacter(String lowerAlias) {
        for (int i = 0; i < lowerAlias.length(); i++) {
            char c = lowerAlias.charAt(i);
            boolean safe = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == ' ';
            if (!safe) {
                return true;
            }
        }
        return false;
    }

    /** Resolves a normalised n-gram to a canonical skill name, or null if unknown. */
    public String canonicalForPlainAlias(String normalisedNgram) {
        return plainAliases.get(normalisedNgram);
    }

    /** Special aliases, longest first. */
    public List<SpecialAlias> specialAliases() {
        return specialAliases;
    }

    public Set<String> canonicalNames() {
        return canonicalNames;
    }

    public int plainAliasCount() {
        return plainAliases.size();
    }
}
