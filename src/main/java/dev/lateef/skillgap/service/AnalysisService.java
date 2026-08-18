package dev.lateef.skillgap.service;

import dev.lateef.skillgap.core.MatchResult;
import dev.lateef.skillgap.core.MatchScorer;
import dev.lateef.skillgap.core.SkillDictionary;
import dev.lateef.skillgap.core.SkillExtractor;
import dev.lateef.skillgap.dto.AnalyzeRequest;
import dev.lateef.skillgap.dto.AnalyzeResponse;
import dev.lateef.skillgap.parse.PdfTextExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Orchestrates one analysis. This is the only class that knows the <em>sequence</em> of
 * steps; the controller knows only HTTP, and the core classes know only their own job.
 *
 * <p>Notice how thin it is. That is the point of a service layer: it coordinates, it does
 * not calculate. All the logic worth testing lives in the framework-free core, where it
 * can be tested without booting Spring.
 */
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private final SkillExtractor skillExtractor;
    private final MatchScorer matchScorer;
    private final SkillDictionary skillDictionary;
    private final PdfTextExtractor pdfTextExtractor;

    /*
     * Constructor injection, not @Autowired on fields. Three concrete reasons:
     *   1. The fields can be final, so the object is immutable once built.
     *   2. It is impossible to construct a half-initialised instance; a missing
     *      dependency fails at startup, not on the first request.
     *   3. A unit test can call `new AnalysisService(extractor, scorer, dictionary)`
     *      directly, with no Spring and no reflection.
     * Since Spring 4.3 a single-constructor class needs no @Autowired annotation at all.
     */
    public AnalysisService(SkillExtractor skillExtractor,
                           MatchScorer matchScorer,
                           SkillDictionary skillDictionary,
                           PdfTextExtractor pdfTextExtractor) {
        this.skillExtractor = skillExtractor;
        this.matchScorer = matchScorer;
        this.skillDictionary = skillDictionary;
        this.pdfTextExtractor = pdfTextExtractor;
    }

    /**
     * Runs a full analysis. Nothing is persisted.
     *
     * @throws NoSkillsExtractedException if the job description yielded no known skills
     */
    public AnalyzeResponse analyze(AnalyzeRequest request) {
        // Both sides go through the same dictionary, so "springboot" on a CV and
        // "Spring Boot" in an advert become the same canonical token. Skipping this
        // would produce false gaps, which is the worst possible bug for this tool:
        // it would confidently tell a candidate to learn something they already know.
        Set<String> candidateSkills = skillExtractor.canonicaliseSkillList(request.candidateSkills());
        Set<String> requiredSkills = skillExtractor.extract(request.jobDescriptionText());

        if (requiredSkills.isEmpty()) {
            throw new NoSkillsExtractedException(
                    "No known skills were found in the job description text. "
                            + "Either the text does not describe a technical role, or the "
                            + "required skills are absent from the dictionary.");
        }

        MatchResult result = matchScorer.score(candidateSkills, requiredSkills);

        log.info("Analysed {} candidate skills against {} required skills, score {}%",
                candidateSkills.size(), requiredSkills.size(), result.matchScore());

        return AnalyzeResponse.from(result, candidateSkills.size());
    }

    /**
     * Analyses an uploaded PDF resume against a job description. Nothing is persisted; the
     * bytes are parsed in memory and discarded when this method returns.
     *
     * <p>Note how little new logic this needs. A resume is prose, and a job advert is
     * prose, so <b>both sides run through the same {@link SkillExtractor#extract}</b>.
     * That symmetry is a payoff from keeping the extractor free of any assumption about
     * where its text came from.
     *
     * <h3>One difference from the typed-skills path, worth knowing</h3>
     * {@link SkillExtractor#canonicaliseSkillList} keeps skills it does not recognise, so a
     * typed "Photoshop" still shows up as an extra skill. Extraction from prose cannot do
     * that: in a wall of resume text there is no way to tell an unlisted skill from an
     * ordinary noun. So an uploaded resume yields <em>only</em> dictionary-known skills, and
     * the "extra" list will be shorter than for the same person typing their skills in by
     * hand. That is a limitation of reading prose, not a bug.
     *
     * @throws dev.lateef.skillgap.parse.UnsupportedFileTypeException if the upload is not a PDF
     * @throws dev.lateef.skillgap.parse.TextExtractionException      if the PDF's text cannot be read
     * @throws NoSkillsExtractedException                            if the job description yielded no known skills
     */
    public AnalyzeResponse analyzeResume(byte[] resumeBytes, String filename, String jobDescriptionText) {
        String resumeText = pdfTextExtractor.extractText(resumeBytes, filename);

        Set<String> candidateSkills = skillExtractor.extract(resumeText);
        Set<String> requiredSkills = skillExtractor.extract(jobDescriptionText);

        if (requiredSkills.isEmpty()) {
            throw new NoSkillsExtractedException(
                    "No known skills were found in the job description text. "
                            + "Either the text does not describe a technical role, or the "
                            + "required skills are absent from the dictionary.");
        }

        MatchResult result = matchScorer.score(candidateSkills, requiredSkills);

        log.info("Analysed resume '{}' ({} chars, {} skills found) against {} required skills, score {}%",
                filename, resumeText.length(), candidateSkills.size(),
                requiredSkills.size(), result.matchScore());

        return AnalyzeResponse.from(result, candidateSkills.size());
    }

    /** Every canonical skill this service can recognise, sorted. Useful for exploring the API. */
    public List<String> knownSkills() {
        return skillDictionary.canonicalNames().stream().sorted().toList();
    }
}
