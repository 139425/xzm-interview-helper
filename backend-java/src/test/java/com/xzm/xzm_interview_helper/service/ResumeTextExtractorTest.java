package com.xzm.xzm_interview_helper.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumeTextExtractorTest {

    private final ResumeTextExtractor extractor = new ResumeTextExtractor();

    @Test
    void extractsMarkdownResumeAsStrictUtf8() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "candidate.md",
                "text/markdown",
                "# Candidate\nJava and RAG experience".getBytes(StandardCharsets.UTF_8)
        );

        ResumeTextExtractor.ExtractedResume resume = extractor.extract(file);

        assertEquals("candidate.md", resume.filename());
        assertTrue(resume.text().contains("Java and RAG experience"));
    }

    @Test
    void extractsPlainUtf8DocsResume() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "candidate.docs",
                "text/plain",
                "Backend engineer\nBuilt resilient services".getBytes(StandardCharsets.UTF_8)
        );

        ResumeTextExtractor.ExtractedResume resume = extractor.extract(file);

        assertEquals("candidate.docs", resume.filename());
        assertTrue(resume.text().contains("Built resilient services"));
    }

    @Test
    void extractsDocxParagraphsAndTableCells() throws Exception {
        byte[] docx;
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph()
                    .createRun()
                    .setText("Java platform engineer");
            XWPFTable table = document.createTable(1, 1);
            table.getRow(0).getCell(0).setText("Reduced p95 latency by 60 percent");
            document.write(output);
            docx = output.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "candidate.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docx
        );
        ResumeTextExtractor.ExtractedResume resume = extractor.extract(file);

        assertEquals("candidate.docx", resume.filename());
        assertTrue(resume.text().contains("Java platform engineer"));
        assertTrue(resume.text().contains("Reduced p95 latency by 60 percent"));
    }

    @Test
    void extractsPdfResume() throws Exception {
        byte[] pdf;
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText("Platform engineer with distributed systems experience");
                content.endText();
            }
            document.save(output);
            pdf = output.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile("file", "candidate.pdf", "application/pdf", pdf);
        ResumeTextExtractor.ExtractedResume resume = extractor.extract(file);

        assertTrue(resume.text().contains("distributed systems experience"));
    }
}
