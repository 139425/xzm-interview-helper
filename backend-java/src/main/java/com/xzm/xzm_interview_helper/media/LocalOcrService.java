package com.xzm.xzm_interview_helper.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class LocalOcrService {
    public static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    public static final long MAX_IMAGE_PIXELS = 25_000_000L;
    public static final int MAX_OCR_CHARACTERS = 60_000;
    private static final Set<String> EXTENSIONS = Set.of("png", "jpg", "jpeg", "bmp");

    private final String command;
    private final String languages;

    public LocalOcrService(
            @Value("${app.ocr.tesseract-command:tesseract}") String command,
            @Value("${app.ocr.languages:chi_sim+eng}") String languages
    ) {
        this.command = command;
        this.languages = languages;
    }

    public OcrResult recognize(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择非空图片");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "图片不能超过 10MB");
        }

        String filename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "image.png" : file.getOriginalFilename());
        String extension = extension(filename);
        if (!EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OCR 仅支持 PNG、JPG、JPEG、BMP 图片");
        }

        Path input = null;
        Path outputBase = null;
        Path outputText = null;
        try {
            byte[] bytes = file.getBytes();
            validateImage(bytes);
            input = Files.createTempFile("xzm-ocr-input-", "." + extension);
            outputBase = Files.createTempFile("xzm-ocr-output-", "");
            Files.deleteIfExists(outputBase);
            outputText = Path.of(outputBase.toString() + ".txt");
            Files.write(input, bytes);

            Process process = new ProcessBuilder(List.of(
                    command,
                    input.toAbsolutePath().toString(),
                    outputBase.toAbsolutePath().toString(),
                    "-l",
                    languages,
                    "--psm",
                    "6"
            )).redirectErrorStream(true).start();

            boolean completed = process.waitFor(30, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "图片识别超时，请压缩或裁剪后重试");
            }
            String diagnostics = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0 || !Files.exists(outputText)) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "本地 OCR 暂时不可用" + safeDiagnostic(diagnostics)
                );
            }

            String text = normalize(Files.readString(outputText, StandardCharsets.UTF_8));
            if (text.length() > MAX_OCR_CHARACTERS) {
                text = text.substring(0, MAX_OCR_CHARACTERS);
            }
            if (text.isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "没有识别到清晰文字，请换一张更清楚的图片");
            }
            return new OcrResult(text, filename, "tesseract:" + languages);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "本地 OCR 服务尚未就绪", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "图片识别被中断", exception);
        } finally {
            deleteQuietly(input);
            deleteQuietly(outputText);
            deleteQuietly(outputBase);
        }
    }

    private void validateImage(byte[] bytes) throws IOException {
        BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片内容无效或已损坏");
        }
        if ((long) image.getWidth() * image.getHeight() > MAX_IMAGE_PIXELS) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "图片像素不能超过 2500 万");
        }
    }

    private static String normalize(String text) {
        return text
                .replace('\u0000', ' ')
                .replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\r\\n?", "\\n")
                .replaceAll("\\n{3,}", "\\n\\n")
                .strip();
    }

    private static String extension(String filename) {
        int index = filename.lastIndexOf('.');
        return index >= 0 && index < filename.length() - 1
                ? filename.substring(index + 1).toLowerCase(Locale.ROOT)
                : "";
    }

    private static String safeDiagnostic(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.replaceAll("[\\r\\n]+", " ").strip();
        return "：" + normalized.substring(0, Math.min(normalized.length(), 160));
    }

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Temporary OCR inputs are best-effort cleaned after every request.
        }
    }

    public record OcrResult(String text, String filename, String engine) {
    }
}
