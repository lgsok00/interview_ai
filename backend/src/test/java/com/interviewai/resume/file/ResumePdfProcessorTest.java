package com.interviewai.resume.file;

import com.interviewai.resume.exception.InvalidResumePdfException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
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


    @Test
    @DisplayName("빈 페이지 PDF는 해시를 유지하며 텍스트 없음 실패로 처리한다")
    void rejectsEmptyPageText() throws Exception {
        assertEmptyExtraction(createPdf(null, false));
    }

    @Test
    @DisplayName("공백만 있는 PDF도 완료가 아닌 텍스트 없음 실패로 처리한다")
    void rejectsWhitespaceOnlyText() throws Exception {
        assertEmptyExtraction(createPdf("   ", false));
    }

    @Test
    @DisplayName("이미지로만 구성된 PDF는 OCR 없이 텍스트 없음 실패로 처리한다")
    void rejectsImageOnlyText() throws Exception {
        assertEmptyExtraction(createPdf(null, true));
    }

    private void assertEmptyExtraction(byte[] pdf) {
        ResumePdfAnalysis analysis = processor.analyze(pdf);

        assertThat(analysis.extractionSucceeded()).isFalse();
        assertThat(analysis.extractionFailureCode()).isEqualTo("TEXT_EXTRACTION_EMPTY");
        assertThat(analysis.extractedText()).isNull();
        assertThat(analysis.sha256()).hasSize(64);
    }

    private byte[] createPdf() throws Exception {
        return createPdf("Backend Resume", false);
    }

    private byte[] createPdf(String text, boolean imageOnly) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                if (text != null) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(50, 700);
                    content.showText(text);
                    content.endText();
                }
                if (imageOnly) {
                    BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
                    content.drawImage(LosslessFactory.createFromImage(document, image), 50, 600, 100, 100);
                }
            }

            document.save(output);
            return output.toByteArray();
        }
    }
}
