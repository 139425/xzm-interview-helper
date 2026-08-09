package com.xzm.xzm_interview_helper.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.util.Locale;
import java.util.Set;

/**
 * Extracts only text from a resume upload. Files are not retained on the web server; the normalized
 * text is stored with the interview session instead.
 */
@Service
public class ResumeTextExtractor {

    public static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;
    public static final int MAX_RESUME_CHARACTERS = 60_000;
    private static final int MAX_PDF_PAGES = 50;
    private static final long MAX_OFFICE_ZIP_ENTRY_BYTES = 20L * 1024 * 1024;
    private static final long MAX_OFFICE_EXTRACTED_TEXT_BYTES = 2L * 1024 * 1024;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("pdf", "md", "txt", "doc", "docx", "docs");

    public ExtractedResume extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择非空的简历文件");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "简历文件不能超过 10MB");
        }

        String filename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "resume" : file.getOriginalFilename());
        String extension = extensionOf(filename);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 PDF、Markdown、DOC、DOCX 或 DOCS 简历文件");
        }

        try {
            String text = switch (extension) {
                case "pdf" -> readPdf(file.getBytes());
                case "docx" -> readDocx(file.getBytes());
                case "doc" -> readDoc(file.getBytes());
                case "docs" -> readDocs(file.getBytes());
                default -> readUtf8(file.getBytes());
            };
            if (text.length() > MAX_RESUME_CHARACTERS * 2) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "简历可提取文本过大");
            }
            text = normalize(text);
            if (!StringUtils.hasText(text)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未能从简历文件中提取可用文本");
            }
            if (text.length() > MAX_RESUME_CHARACTERS) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "简历文本不能超过 60000 个字符");
            }
            return new ExtractedResume(text, filename);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法读取该简历文件，请确认文件未损坏或受密码保护", exception);
        }
    }

    private String readPdf(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.getNumberOfPages() > MAX_PDF_PAGES) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "PDF 简历不能超过 " + MAX_PDF_PAGES + " 页");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String readDocx(byte[] bytes) throws IOException {
        // Apache POI applies these limits while opening the archive, before XML is expanded.
        ZipSecureFile.setMinInflateRatio(0.01d);
        ZipSecureFile.setMaxEntrySize(MAX_OFFICE_ZIP_ENTRY_BYTES);
        ZipSecureFile.setMaxTextSize(MAX_OFFICE_EXTRACTED_TEXT_BYTES);
        StringBuilder text = new StringBuilder();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            document.getParagraphs().forEach(paragraph -> appendLine(text, paragraph.getText()));
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        appendLine(text, cell.getText());
                    }
                }
            }
        }
        return text.toString();
    }

    private String readDoc(byte[] bytes) throws IOException {
        try (HWPFDocument document = new HWPFDocument(new ByteArrayInputStream(bytes))) {
            return document.getRange().text();
        }
    }

    private String readDocs(byte[] bytes) throws IOException {
        if (startsWith(bytes, 0x50, 0x4B)) {
            return readDocx(bytes);
        }
        if (startsWith(bytes, 0xD0, 0xCF, 0x11, 0xE0)) {
            return readDoc(bytes);
        }
        return readUtf8(bytes);
    }

    private String readUtf8(byte[] bytes) throws IOException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    private boolean startsWith(byte[] bytes, int... signature) {
        if (bytes == null || bytes.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if ((bytes[index] & 0xFF) != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private void appendLine(StringBuilder text, String value) {
        if (StringUtils.hasText(value)) {
            if (text.length() + value.length() > MAX_RESUME_CHARACTERS * 2) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "简历可提取文本过大");
            }
            text.append(value).append('\n');
        }
    }

    private String normalize(String source) {
        if (source == null) {
            return "";
        }
        return source
                .replace('\u0000', ' ')
                .replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\r\\n?", "\\n")
                .trim();
    }

    private String extensionOf(String filename) {
        int index = filename.lastIndexOf('.');
        return index >= 0 && index < filename.length() - 1
                ? filename.substring(index + 1).toLowerCase(Locale.ROOT)
                : "";
    }

    public record ExtractedResume(String text, String filename) {
    }
}
