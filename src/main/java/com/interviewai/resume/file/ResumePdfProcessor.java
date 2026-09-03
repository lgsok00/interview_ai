package com.interviewai.resume.file;

import com.interviewai.resume.exception.EncryptedResumePdfException;
import com.interviewai.resume.exception.InvalidResumePdfException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class ResumePdfProcessor {

    private static final byte[] PDF_SIGNATURE = {'%', 'P', 'D', 'F', '-'};
    private static final String EXTRACTION_FAILURE_CODE = "TEXT_EXTRACTION_FAILED";


    public ResumePdfAnalysis analyze(byte[] contents) {
        validateSignature(contents);
        String sha256 = calculateSha256(contents);

        try (PDDocument document = loadDocument(contents)) {
            try {
                String extractedText = new PDFTextStripper().getText(document).trim();

                return ResumePdfAnalysis.completed(sha256, extractedText);

            } catch (IOException exception) {
                return ResumePdfAnalysis.failed(sha256, EXTRACTION_FAILURE_CODE);
            }

        } catch (IOException exception) {
            throw new InvalidResumePdfException();
        }
    }


    private void validateSignature(byte[] contents) {
        if (contents.length < PDF_SIGNATURE.length) {
            throw new InvalidResumePdfException();
        }

        for (int index = 0; index < PDF_SIGNATURE.length; index++) {
            if (contents[index] != PDF_SIGNATURE[index]) {
                throw new InvalidResumePdfException();
            }
        }
    }


    private String calculateSha256(byte[] contents) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            return HexFormat.of().formatHex(digest.digest(contents));

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }


    private PDDocument loadDocument(byte[] contents) {
        try {
            return Loader.loadPDF(contents);

        } catch (InvalidPasswordException exception) {
            throw new EncryptedResumePdfException();

        } catch (IOException e) {
            throw new InvalidResumePdfException();
        }
    }
}
