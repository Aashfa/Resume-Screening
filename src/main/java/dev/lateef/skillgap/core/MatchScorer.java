package dev.lateef.skillgap.core;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Compares two sets of canonical skill names and produces a {@link MatchResult}.
 *
 * <p>No Spring, and no state. Every method is a pure function of its arguments, which is
 * why this class is trivial to test and safe to share between threads.
 *
 * <h2>Three set operations</h2>
 * <pre>
 *   matched = candidate &cap; required     the overlap
 *   missing = required  &minus; candidate     the gap, which is the product
 *   extra   = candidate &minus; required     what the job never asked for
 * </pre>
 * That is the entire algorithm. It is deliberately unremarkable: the difficulty in this
 * project lives in {@link SkillExtractor}, in turning messy prose into a clean set. Once
 * both sides are clean sets, the comparison is three lines of set arithmetic. Recognising
 * which part of a problem is actually hard is most of engineering.
 *
 * <h2>Why extra skills are excluded from the score</h2>
 * {@code matchScore} answers exactly one question: <em>how much of what this job asked
 * for does this candidate have?</em> That is coverage. Extra skills are by definition
 * things the job did not ask for, so they cannot change coverage without changing what
 * the number means.
 *
 * <p>Concretely, suppose extras added a point each. A job needs five skills.
 * Candidate A has all five: {@code 5/5 * 100 = 100}. Candidate B has three, plus
 * forty-five unrelated ones: {@code 3/5 * 100 + 45 = 105}. Candidate B now outranks a
 * perfect match while missing 40% of the requirements. Any uncapped bonus eventually
 * does this; a cap only moves where it breaks.
 *
 * <p>There is a worse version of the problem. This is a resume screener, and the most
 * common way candidates game real screeners is padding a CV with keywords. A rule that
 * rewards extra skills pays out for precisely that behaviour, building the exploit into
 * the metric.
 *
 * <p>So extras are reported in full, and counted in
 * {@link MatchResult#extraSkillCount()}, but kept out of the score. One number, one
 * question.
 */
public final class MatchScorer {

    /**
     * @param candidateSkills canonical skills the candidate has; null is treated as empty
     * @param requiredSkills  canonical skills the job requires; null is treated as empty
     * @return the comparison, with all three lists sorted alphabetically
     */
    public MatchResult score(Set<String> candidateSkills, Set<String> requiredSkills) {
        Set<String> candidate = nullSafe(candidateSkills);
        Set<String> required = nullSafe(requiredSkills);

        // Sorted so the API response and the test assertions are both deterministic.
        // An endpoint that returns the same data in a different order on every call is
        // needlessly hard to test and to diff.
        List<String> matched = candidate.stream().filter(required::contains).sorted().toList();
        List<String> missing = required.stream().filter(s -> !candidate.contains(s)).sorted().toList();
        List<String> extra = candidate.stream().filter(s -> !required.contains(s)).sorted().toList();

        return new MatchResult(percentage(matched.size(), required.size()), matched, missing, extra);
    }

    /**
     * Coverage as a percentage, rounded to one decimal place.
     *
     * <p>When the job requires nothing we return 0.0 rather than 100.0. Strictly, a
     * candidate vacuously satisfies an empty requirement list, so 100 is the
     * mathematically defensible answer. It is the wrong answer for a human reading a
     * report: "100% match" against a job description we failed to extract anything from
     * reads as a perfect candidate, when what actually happened is that we could not
     * score at all. The empty {@code missing} list is what distinguishes the two cases.
     */
    private static double percentage(int matchedCount, int requiredCount) {
        if (requiredCount == 0) {
            return 0.0;
        }
        double raw = (matchedCount * 100.0) / requiredCount;
        return Math.round(raw * 10.0) / 10.0;
    }

    private static Set<String> nullSafe(Set<String> input) {
        return input == null ? Set.of() : new LinkedHashSet<>(input);
    }
}
