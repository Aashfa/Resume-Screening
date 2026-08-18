package dev.lateef.skillgap.core;

import java.util.List;

/**
 * The outcome of comparing a candidate's skills against a job description's skills.
 *
 * <p>A {@code record} because this type is nothing but data: immutable, value-based
 * equality, no behaviour. Records give us the constructor, accessors, {@code equals},
 * {@code hashCode} and {@code toString} for free, which is why the tests can assert on
 * whole results rather than field by field.
 *
 * @param matchScore      percentage of the job's required skills the candidate has,
 *                        0.0 to 100.0, one decimal place. <b>Coverage only.</b> Extra
 *                        skills deliberately do not appear in this number; see
 *                        {@link MatchScorer} for why.
 * @param matched         skills required by the job that the candidate has
 * @param missing         skills required by the job that the candidate lacks: the gap
 * @param extra           skills the candidate has that the job never asked for
 * @param extraSkillCount size of {@code extra}, surfaced as its own field so extra
 *                        skills are visible as a positive signal without contaminating
 *                        {@code matchScore}
 */
public record MatchResult(
        double matchScore,
        List<String> matched,
        List<String> missing,
        List<String> extra,
        int extraSkillCount) {

    /**
     * Canonical constructor. Defensively copies the lists, so a caller cannot mutate a
     * result after the fact and so the record is genuinely immutable rather than
     * immutable-looking.
     */
    public MatchResult {
        matched = List.copyOf(matched);
        missing = List.copyOf(missing);
        extra = List.copyOf(extra);
    }

    /**
     * Preferred constructor: derives {@code extraSkillCount} from {@code extra} so the
     * two can never disagree. Storing a count that a caller could set independently of
     * the list it counts is an invitation for them to drift apart.
     */
    public MatchResult(double matchScore, List<String> matched, List<String> missing, List<String> extra) {
        this(matchScore, matched, missing, extra, extra.size());
    }
}
