package dev.lateef.skillgap.parse;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;

/**
 * Pulls the text layer out of a PDF resume.
 *
 * <p>Separate package from {@code core} on purpose. {@code core} is the skill-matching
 * algorithm; this is file parsing. They are different concerns with different reasons to
 * change, and mixing them would mean a PDF library upgrade touching the same package as
 * the scoring logic.
 *
 * <p>Like {@code core}, there is no Spring here, so it unit tests without an application
 * context. It does depend on PDFBox, which is a parsing library rather than a framework:
 * the rule being followed is "no framework in the logic", not "no libraries anywhere".
 *
 * <h2>What this cannot do</h2>
 * It reads the <em>text layer</em> of a PDF. A resume that was scanned or exported as an
 * image has no text layer, so extraction returns nothing. Recovering text from pixels
 * needs OCR (Tesseract or similar), which is a substantially bigger undertaking and is
 * deliberately out of scope. Such a file produces a clear error rather than a silent
 * "0 skills found", because those two situations mean very different things to a user.
 */
public final class PdfTextExtractor {

    /** Every PDF file begins with these bytes. */
    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};

    /**
     * Below this many characters we assume the PDF carried no real text layer.
     * Image-only PDFs commonly still yield a handful of stray characters, so testing for
     * exactly zero is not reliable enough.
     */
    private static final int MINIMUM_PLAUSIBLE_TEXT_LENGTH = 20;

    /**
     * @param pdfBytes the raw uploaded file
     * @param filename original filename, used only to make error messages readable
     * @return the extracted text
     * @throws UnsupportedFileTypeException if the bytes are not a PDF at all
     * @throws TextExtractionException      if the PDF is encrypted, corrupt, or has no
     *                                      usable text layer
     */
    public String extractText(byte[] pdfBytes, String filename) {
        String name = (filename == null || filename.isBlank()) ? "the uploaded file" : filename;

        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new TextExtractionException(name + " is empty.");
        }

        /*
         * Check the magic bytes rather than trusting the filename or the browser-supplied
         * Content-Type. Both are attacker-controlled and both are routinely wrong even
         * without an attacker: users rename files, and browsers guess MIME types.
         */
        if (!startsWithPdfMagic(pdfBytes)) {
            throw new UnsupportedFileTypeException(
                    name + " is not a PDF. Only PDF resumes are supported. "
                            + "If yours is a Word document, export or print it to PDF first.");
        }

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            /*
             * A password-protected PDF may load but refuse text extraction. Checking up
             * front lets us return a message the user can act on, instead of an empty
             * result that looks like "we found no skills in your resume".
             */
            if (document.isEncrypted()) {
                throw new TextExtractionException(
                        name + " is password protected, so its text cannot be read. "
                                + "Please upload an unprotected copy.");
            }

            String text = new PDFTextStripper().getText(document);

            if (text == null || text.strip().length() < MINIMUM_PLAUSIBLE_TEXT_LENGTH) {
                throw new TextExtractionException(
                        "No readable text was found in " + name + ". This usually means the "
                                + "resume is a scanned image rather than a text PDF. Reading "
                                + "text from an image would require OCR, which this service "
                                + "does not do. Please upload a text-based PDF.");
            }

            return text;

        } catch (IOException e) {
            // Corrupt or truncated file. The underlying message can expose library
            // internals, so it goes to the cause for the logs and not to the user.
            throw new TextExtractionException(
                    name + " could not be read as a PDF. It may be corrupt or incomplete.", e);
        }
    }

    private static boolean startsWithPdfMagic(byte[] bytes) {
        if (bytes.length < PDF_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (bytes[i] != PDF_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }
}
