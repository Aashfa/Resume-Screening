package dev.lateef.skillgap.parse;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * No Spring, and no mocking of PDFBox either. These tests build genuine PDF files in
 * memory and read them back.
 *
 * <p>Mocking the PDF library would only prove that our code calls the methods we told it
 * to call. It would not prove we can read a real PDF, which is the entire job of this
 * class. Mock the things you own; use the real thing when the integration <em>is</em> the
 * behaviour under test.
 */
class PdfTextExtractorTest {

    private final PdfTextExtractor extractor = new PdfTextExtractor();

    /** Builds a real single-page PDF containing the given lines. */
    private static byte[] pdfWithText(List<String> lines) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                // PDFBox 3.x: fonts are constructed, not accessed as static constants
                // the way PDType1Font.HELVETICA worked in 2.x.
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

    @Test
    @DisplayName("extracts text from a real PDF")
    void extractsText() throws IOException {
        byte[] pdf = pdfWithText(List.of(
                "Aashfa Lateef",
                "Skills: Java, Spring Boot, SQL, Git"));

        String text = extractor.extractText(pdf, "cv.pdf");

        assertThat(text).contains("Aashfa Lateef");
        assertThat(text).contains("Java");
        assertThat(text).contains("Spring Boot");
    }

    @Test
    @DisplayName("extracted text feeds the skill extractor unchanged")
    void integratesWithSkillExtractor() throws IOException {
        byte[] pdf = pdfWithText(List.of(
                "Technical Skills",
                "Languages: Java, C++, C#",
                "Frameworks: Spring Boot, Node.js",
                "Tools: Git, Maven, Docker"));

        String text = extractor.extractText(pdf, "cv.pdf");

        var skillExtractor = new dev.lateef.skillgap.core.SkillExtractor(
                dev.lateef.skillgap.core.SkillDictionary.loadFromClasspath("/skills.json"));

        // The same extractor used on job adverts works on resume prose. Nothing about it
        // assumed where the text came from.
        assertThat(skillExtractor.extract(text))
                .contains("Java", "C++", "C#", "Spring Boot", "Node.js", "Git", "Maven", "Docker");
    }

    @Test
    @DisplayName("a non-PDF file is rejected by magic bytes, not by its filename")
    void rejectsNonPdf() {
        byte[] notAPdf = "This is a plain text file pretending to be a PDF."
                .getBytes(StandardCharsets.UTF_8);

        // Note the filename says .pdf. We must not believe it.
        assertThatThrownBy(() -> extractor.extractText(notAPdf, "resume.pdf"))
                .isInstanceOf(UnsupportedFileTypeException.class)
                .hasMessageContaining("is not a PDF");
    }

    @Test
    @DisplayName("an empty upload is rejected")
    void rejectsEmpty() {
        assertThatThrownBy(() -> extractor.extractText(new byte[0], "cv.pdf"))
                .isInstanceOf(TextExtractionException.class)
                .hasMessageContaining("is empty");
    }

    @Test
    @DisplayName("null bytes are rejected without a NullPointerException")
    void rejectsNull() {
        assertThatThrownBy(() -> extractor.extractText(null, "cv.pdf"))
                .isInstanceOf(TextExtractionException.class);
    }

    @Test
    @DisplayName("a PDF with no text layer reports the scanned-image problem clearly")
    void rejectsPdfWithoutTextLayer() throws IOException {
        // A valid PDF with a blank page: exactly what a scanned resume looks like to a
        // text extractor. The error must explain OCR rather than say "0 skills found".
        byte[] blank;
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(out);
            blank = out.toByteArray();
        }

        assertThatThrownBy(() -> extractor.extractText(blank, "scanned-cv.pdf"))
                .isInstanceOf(TextExtractionException.class)
                .hasMessageContaining("scanned image")
                .hasMessageContaining("OCR");
    }

    @Test
    @DisplayName("a truncated PDF is reported as corrupt rather than crashing")
    void rejectsCorruptPdf() throws IOException {
        byte[] full = pdfWithText(List.of("Java developer with Spring Boot experience"));
        byte[] truncated = new byte[full.length / 3];
        System.arraycopy(full, 0, truncated, 0, truncated.length);

        assertThatThrownBy(() -> extractor.extractText(truncated, "broken.pdf"))
                .isInstanceOf(TextExtractionException.class);
    }

    @Test
    @DisplayName("a missing filename still produces a readable message")
    void handlesMissingFilename() {
        assertThatThrownBy(() -> extractor.extractText(new byte[0], null))
                .isInstanceOf(TextExtractionException.class)
                .hasMessageContaining("the uploaded file");
    }
}
