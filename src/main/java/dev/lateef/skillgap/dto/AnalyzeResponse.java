package dev.lateef.skillgap.dto;

import dev.lateef.skillgap.core.MatchResult;

import java.util.List;

/**
 * The API's response shape.
 *
 * <p>Deliberately a separate type from {@link MatchResult}, even though the two overlap
 * heavily. {@code MatchResult} is the core algorithm's output; this is the public HTTP
 * contract. Splitting them means you can add a field to the API, or rename one for
 * consumers, without touching tested algorithm code, and vice versa.
 *
 * @param matchScore          percentage of the job's requirements the candidate covers,
 *                            0.0 to 100.0. Coverage only: extra skills never inflate it.
 * @param matchedSkills       required skills the candidate has
 * @param missingSkills       required skills the candidate lacks. This is the gap, and
 *                            the actual point of the tool.
 * @param extraSkills         candidate skills the job never asked for
 * @param extraSkillCount     how many extras, surfaced separately so they read as a
 *                            positive signal without contaminating {@code matchScore}
 * @param requiredSkillCount  how many skills were extracted from the job description,
 *                            i.e. the denominator of {@code matchScore}
 * @param candidateSkillCount how many distinct skills the candidate had after aliases
 *                            were resolved to canonical names
 */
public record AnalyzeResponse(
        double matchScore,
        List<String> matchedSkills,
        List<String> missingSkills,
        List<String> extraSkills,
        int extraSkillCount,
        int requiredSkillCount,
        int candidateSkillCount) {

    /** Maps the core result onto the API shape. */
    public static AnalyzeResponse from(MatchResult result, int candidateSkillCount) {
        return new AnalyzeResponse(
                result.matchScore(),
                result.matched(),
                result.missing(),
                result.extra(),
                result.extraSkillCount(),
                result.matched().size() + result.missing().size(),
                candidateSkillCount);
    }
}
