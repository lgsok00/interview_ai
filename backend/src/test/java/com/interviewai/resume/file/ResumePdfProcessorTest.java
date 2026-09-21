package com.interviewai.resume.file;

import com.interviewai.resume.exception.InvalidResumePdfException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResumePdfProcessorTest {

    private final ResumePdfProcessor processor = new ResumePdfProcessor();


    @Test
    @DisplayName("유효한 PDF에서 텍스트와 SHA-256 해시를 추출한다")
    void extractsTextAndHash() throws Exception {
        byte[] pdf = createPdf();

        ResumePdfAnalysis analysis = processor.analyze(pdf);

        assertThat(analysis.extractionSucceeded()).isTrue();
        assertThat(analysis.extractedText()).contains("Backend Resume");
        assertThat(analysis.sha256()).hasSize(64);
    }


    @Test
    @DisplayName("PDF 시그니처만 위조한 손상 파일을 거부한다")
    void rejectsCorruptedPdf() {
        byte[] corrupted = "%PDF-not-a-document".getBytes();

        assertThatThrownBy(() -> processor.analyze(corrupted))
                .isInstanceOf(InvalidResumePdfException.class);
    }


    private byte[] createPdf() throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText("Backend Resume");
                content.endText();
            }

            document.save(output);
            return output.toByteArray();
        }
    }
}
