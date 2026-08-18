package dev.lateef.skillgap.web;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for the multipart upload path, driven with {@link MockMultipartFile}
 * over real generated PDFs.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ResumeUploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String JD = "We are looking for a Java developer with Spring Boot, "
            + "SQL, Git and Docker experience to join our backend team.";

    private static byte[] pdf(List<String> lines) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.setLeading(16f);
                content.newLineAtOffset(50, 700);
                for (String line : lines) {
                    content.showText(line);
                    content.newLine();
                }
                content.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private static MockMultipartFile resume(byte[] bytes) {
        return new MockMultipartFile("file", "cv.pdf", "application/pdf", bytes);
    }

    @Test
    @DisplayName("uploading a PDF resume returns a full analysis")
    void uploadReturnsAnalysis() throws Exception {
        byte[] cv = pdf(List.of(
                "Aashfa Lateef - Software Developer",
                "Skills: Java, Spring Boot, SQL, Git",
                "Also familiar with C++ and C#"));

        mockMvc.perform(multipart("/api/analyze/resume")
                        .file(resume(cv))
                        .param("jobDescriptionText", JD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchScore").exists())
                .andExpect(jsonPath("$.matchedSkills").isArray())
                .andExpect(jsonPath("$.missingSkills").isArray())
                .andExpect(jsonPath("$.extraSkills").isArray());
    }

    @Test
    @DisplayName("skills written in the resume are matched against the advert")
    void matchesSkillsFromResumeText() throws Exception {
        byte[] cv = pdf(List.of("Skills: Java, Spring Boot, SQL, Git"));

        mockMvc.perform(multipart("/api/analyze/resume")
                        .file(resume(cv))
                        .param("jobDescriptionText", JD))
                .andExpect(status().isOk())
                // JD wants Java, Spring Boot, SQL, Git, Docker. The CV has four of the five.
                .andExpect(jsonPath("$.matchScore").value(80.0))
                .andExpect(jsonPath("$.missingSkills[0]").value("Docker"));
    }

    @Test
    @DisplayName("resume aliases are canonicalised the same way as typed skills")
    void resumeAliasesAreCanonicalised() throws Exception {
        byte[] cv = pdf(List.of("Worked with springboot, core java and my sql for two years"));

        mockMvc.perform(multipart("/api/analyze/resume")
                        .file(resume(cv))
                        .param("jobDescriptionText",
                                "Looking for Java, Spring Boot and MySQL skills here."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchScore").value(100.0))
                .andExpect(jsonPath("$.missingSkills").isEmpty());
    }

    @Test
    @DisplayName("a non-PDF upload returns 415 Unsupported Media Type")
    void nonPdfReturns415() throws Exception {
        MockMultipartFile fake = new MockMultipartFile(
                "file", "cv.pdf", "application/pdf",
                "just some text, definitely not a pdf".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/analyze/resume")
                        .file(fake)
                        .param("jobDescriptionText", JD))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("is not a PDF")));
    }

    @Test
    @DisplayName("a scanned PDF with no text layer returns 422 explaining OCR")
    void scannedPdfReturns422() throws Exception {
        byte[] blank;
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(out);
            blank = out.toByteArray();
        }

        mockMvc.perform(multipart("/api/analyze/resume")
                        .file(resume(blank))
                        .param("jobDescriptionText", JD))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("scanned image")));
    }

    @Test
    @DisplayName("an empty file returns 422")
    void emptyFileReturns422() throws Exception {
        mockMvc.perform(multipart("/api/analyze/resume")
                        .file(resume(new byte[0]))
                        .param("jobDescriptionText", JD))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("a blank job description returns 400")
    void blankJobDescriptionReturns400() throws Exception {
        byte[] cv = pdf(List.of("Skills: Java, Spring Boot"));

        mockMvc.perform(multipart("/api/analyze/resume")
                        .file(resume(cv))
                        .param("jobDescriptionText", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("a missing file part returns 400 naming the absent part")
    void missingFilePartReturns400() throws Exception {
        mockMvc.perform(multipart("/api/analyze/resume")
                        .param("jobDescriptionText", JD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("file")));
    }

    @Test
    @DisplayName("a job description with no known skills returns 422")
    void unrecognisableJobDescriptionReturns422() throws Exception {
        byte[] cv = pdf(List.of("Skills: Java, Spring Boot"));

        mockMvc.perform(multipart("/api/analyze/resume")
                        .file(resume(cv))
                        .param("jobDescriptionText",
                                "We are a fast paced team that values curiosity and empathy."))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("No known skills were found")));
    }
}
