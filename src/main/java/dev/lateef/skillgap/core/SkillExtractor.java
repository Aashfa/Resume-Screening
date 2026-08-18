package dev.lateef.skillgap.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Finds known skills inside free-form job-description text.
 *
 * <p>No Spring. Construct it with {@code new SkillExtractor(dictionary)} and test it
 * directly.
 *
 * <h2>The algorithm, in order</h2>
 * <ol>
 *   <li><b>Pass 1 &mdash; special characters.</b> Scan the lowercased raw text for
 *       punctuation-bearing aliases ("c++", "c#", ".net core", "node.js"), longest
 *       first. Every hit is recorded <em>and then blanked out of the working text</em>.</li>
 *   <li><b>Pass 2 &mdash; n-gram windows.</b> Replace every remaining non-alphanumeric
 *       character with a space, split on whitespace, then slide windows of 1, 2 and 3
 *       words over the tokens and look each window up in the dictionary.</li>
 * </ol>
 *
 * <h2>Why pass 1 must run first, and must blank out what it finds</h2>
 * This is the part worth understanding. Normalising strips punctuation, so:
 * <pre>
 *   "C++"  --normalise--&gt;  "c"      collides with the letter C
 *   "C#"   --normalise--&gt;  "c"      collides with the letter C
 *   ".NET" --normalise--&gt;  "net"    a different word entirely
 * </pre>
 * If we normalised first we would not merely fail to find C++, we would actively
 * report the <em>wrong</em> skill. Blanking the matched span is what stops the leftover
 * "c" from "c++" being re-read as a separate skill in pass 2.
 *
 * <h2>Why the n-gram window stops at 3</h2>
 * A window of n words costs one hash lookup, and there are roughly {@code tokens}
 * windows for each n, so the scan is {@code O(tokens * MAX_NGRAM)} &mdash; linear in the
 * length of the job description. Raising the cap to 6 would double the work for
 * essentially no gain, because almost no real skill name is longer than three words:
 * "spring data jpa", "amazon web services", "continuous integration" are already at the
 * limit. Longer phrases are marketing prose, not skills. 3 is the point where the curve
 * of "skills you can still catch" goes flat.
 *
 * <h2>Why no clever algorithm is needed here</h2>
 * A natural instinct is to reach for something sophisticated &mdash; a trie, edit
 * distance, an optimal-segmentation search that picks the "best" non-overlapping cover of
 * the text. That instinct is wrong here, and being able to say why is the point.
 * <ul>
 *   <li>Optimal-segmentation problems like the Travelling Salesman Problem are hard
 *       because every choice constrains every later choice, so you cannot commit to a
 *       local decision. Here, choices are independent: finding "Spring Boot" at word 40
 *       tells you nothing about word 900, and we want <em>all</em> matches, not one
 *       best-scoring tour. There is no combinatorial explosion to tame.</li>
 *   <li>The output is a {@link Set}. Overlapping and duplicate hits collapse on their own,
 *       so there is nothing to disambiguate.</li>
 *   <li>A job description is a few hundred words against a dictionary of a few hundred
 *       aliases. A {@link java.util.HashMap} lookup is O(1); the whole scan finishes in
 *       well under a millisecond. Optimising it further would be solving a problem
 *       nobody has.</li>
 * </ul>
 * Choosing the simple solution when the simple solution is sufficient is an engineering
 * judgement, not a shortcut. The complexity that <em>is</em> warranted went into pass 1,
 * because that is where real inputs actually break.
 */
public final class SkillExtractor {

    private final SkillDictionary dictionary;

    public SkillExtractor(SkillDictionary dictionary) {
        this.dictionary = Objects.requireNonNull(dictionary, "dictionary");
    }

    /**
     * @param rawText free-form job description text; null and blank are tolerated
     * @return canonical skill names found, in discovery order, never null
     */
    public Set<String> extract(String rawText) {
        Set<String> found = new LinkedHashSet<>();
        if (rawText == null || rawText.isBlank()) {
            return found;
        }

        StringBuilder work = new StringBuilder(rawText.toLowerCase(Locale.ROOT));

        extractSpecialCharacterSkills(work, found);
        extractNgramSkills(work, found);

        return found;
    }

    /**
     * Canonicalises a candidate's own list of skills, so that both sides of the
     * comparison speak the same vocabulary.
     *
     * <p>This matters more than it looks. A candidate types "springboot" and the job
     * advert says "Spring Boot". Comparing those two raw strings finds no match, and the
     * whole tool silently reports a gap that does not exist. Running both sides through
     * the same dictionary is what makes the set comparison in
     * {@link MatchScorer} meaningful.
     *
     * <p><b>Unrecognised skills are kept, not dropped.</b> If a candidate lists
     * "Photoshop" and the dictionary has never heard of it, it passes through trimmed and
     * unchanged so it can still be reported as an extra skill. Dropping it would quietly
     * delete something the user told us about, and "the tool ignored half my CV" is a
     * worse failure than "the tool listed something odd".
     *
     * @param rawSkills the candidate's skills as typed; null entries are skipped
     * @return canonical names where recognised, trimmed originals where not
     */
    public Set<String> canonicaliseSkillList(Collection<String> rawSkills) {
        Set<String> result = new LinkedHashSet<>();
        if (rawSkills == null) {
            return result;
        }
        for (String raw : rawSkills) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            Set<String> recognised = extract(raw);
            if (recognised.isEmpty()) {
                result.add(raw.trim());
            } else {
                result.addAll(recognised);
            }
        }
        return result;
    }

    /**
     * Pass 1. Literal substring search for punctuation-bearing aliases, longest first,
     * blanking each hit so pass 2 cannot misread the remains.
     */
    private void extractSpecialCharacterSkills(StringBuilder work, Set<String> found) {
        for (SkillDictionary.SpecialAlias special : dictionary.specialAliases()) {
            String alias = special.alias();
            int from = 0;
            while (from <= work.length() - alias.length()) {
                int index = work.indexOf(alias, from);
                if (index < 0) {
                    break;
                }
                int end = index + alias.length();

                // Boundary check. Without it, ".net core" would match inside
                // "asp.net core", stealing a hit from the more specific ASP.NET skill.
                if (isFreeBoundaryBefore(work, index) && isFreeBoundaryAfter(work, end)) {
                    found.add(special.canonical());
                    for (int i = index; i < end; i++) {
                        work.setCharAt(i, ' ');
                    }
                }
                from = index + 1;
            }
        }
    }

    /**
     * Pass 2. Normalise what pass 1 left behind, then slide 1-, 2- and 3-word windows,
     * <b>longest window first, consuming whatever matches</b>.
     *
     * <h3>Why longest-first-and-consume, rather than recording every window that matches</h3>
     * An earlier version tried all window sizes at every position and recorded every hit.
     * That produced a false gap, which for this tool is the worst possible bug: it tells a
     * candidate to go and learn something they already know.
     *
     * <p>The dictionary maps the 1-gram "spring" to Spring Framework and the 2-gram
     * "spring boot" to Spring Boot. So the text "Spring Boot" yielded <em>both</em> skills,
     * while the equivalent alias "springboot" has no "spring" token and yielded only Spring
     * Boot. Two spellings of one skill therefore canonicalised to different sets:
     * <pre>
     *   candidate types "Spring Boot" -&gt; {Spring Boot, Spring Framework} -&gt; 100% match
     *   candidate types "springboot"  -&gt; {Spring Boot}                   -&gt;  50%, "learn Spring Framework"
     * </pre>
     * Taking the longest match at each position and skipping past it restores the property
     * that matters: <b>every alias of a skill canonicalises to the same set.</b> It also
     * fixes "Java Script", which previously reported both Java and JavaScript.
     *
     * <p>Note that this is precisely the rule pass 1 already used. Pass 1 sorts aliases
     * longest-first and blanks each hit; pass 2 now takes the longest window and advances
     * past it. Same principle, applied consistently.
     *
     * <h3>The cost of being greedy</h3>
     * Greedy left-to-right matching can, in principle, miss an overlapping alternative: in
     * "a b c", if both "a b" and "b c" are skills, we take "a b" and never see "b c".
     * Resolving that optimally would need the segmentation search this design explicitly
     * avoids. It stays avoided, because for real skill names the case does not arise, and
     * paying for a general solution to a problem the data does not contain is how simple
     * code becomes unmaintainable. If a future dictionary does create such an overlap, the
     * fix is to add an explicit alias for the phrase, not to make the scanner cleverer.
     */
    private void extractNgramSkills(StringBuilder work, Set<String> found) {
        List<String> tokens = tokenise(work);

        int position = 0;
        while (position < tokens.size()) {
            int maxWindow = Math.min(SkillDictionary.MAX_NGRAM, tokens.size() - position);
            int consumed = 1; // no match here: step forward one token

            for (int n = maxWindow; n >= 1; n--) {
                String window = String.join(" ", tokens.subList(position, position + n));
                String canonical = dictionary.canonicalForPlainAlias(window);
                if (canonical != null) {
                    found.add(canonical);
                    consumed = n;
                    break;
                }
            }
            position += consumed;
        }
    }

    /**
     * Replaces every character that is not a lowercase letter or digit with a space,
     * then splits on whitespace. Digits are kept so "html5" and "es6" survive intact.
     */
    private static List<String> tokenise(StringBuilder work) {
        StringBuilder normalised = new StringBuilder(work.length());
        for (int i = 0; i < work.length(); i++) {
            char c = work.charAt(i);
            normalised.append(isWordCharacter(c) ? c : ' ');
        }

        List<String> tokens = new ArrayList<>();
        for (String token : normalised.toString().split(" ")) {
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static boolean isWordCharacter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }

    /** True if nothing alphanumeric immediately precedes the match. */
    private static boolean isFreeBoundaryBefore(CharSequence text, int index) {
        return index == 0 || !isWordCharacter(text.charAt(index - 1));
    }

    /** True if nothing alphanumeric immediately follows the match. */
    private static boolean isFreeBoundaryAfter(CharSequence text, int end) {
        return end >= text.length() || !isWordCharacter(text.charAt(end));
    }
}
