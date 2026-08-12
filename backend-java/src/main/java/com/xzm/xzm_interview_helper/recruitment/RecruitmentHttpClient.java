package com.xzm.xzm_interview_helper.recruitment;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class RecruitmentHttpClient {
    private static final int MAX_BODY_LENGTH = 4_000_000;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public String get(String url) throws IOException, InterruptedException {
        String safeUrl = RecruitmentText.safeHttpUrl(url);
        if (safeUrl.isEmpty()) {
            throw new IOException("Unsupported recruitment source URL");
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(safeUrl))
                .timeout(Duration.ofSeconds(18))
                .header("User-Agent", "Mozilla/5.0 (compatible; XZMRecruitmentBot/2.0; +https://github.com/139425/xzm-interview-helper)")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.6")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Recruitment source returned HTTP " + response.statusCode());
        }
        String body = response.body() == null ? "" : response.body();
        if (body.length() > MAX_BODY_LENGTH) {
            throw new IOException("Recruitment source response is too large");
        }
        return body;
    }
}
