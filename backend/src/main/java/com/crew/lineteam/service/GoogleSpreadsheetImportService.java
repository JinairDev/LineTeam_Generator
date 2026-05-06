package com.crew.lineteam.service;

import com.crew.lineteam.dto.CrewUploadResponse;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GoogleSpreadsheetImportService {

    private static final Logger log = LoggerFactory.getLogger(GoogleSpreadsheetImportService.class);

    private final ExcelService excelService;
    private final boolean trustAllCertificates;

    public GoogleSpreadsheetImportService(
            ExcelService excelService,
            @Value("${google.sheets.trust-all-certificates:false}") boolean trustAllCertificates) {
        this.excelService = excelService;
        this.trustAllCertificates = trustAllCertificates;
    }

    @PostConstruct
    void logTlsMode() {
        if (trustAllCertificates) {
            log.warn(
                    "google.sheets.trust-all-certificates=true: Google CSV 요청 시 서버 인증서를 검증하지 않습니다. "
                            + "신뢰할 수 있는 네트워크에서만 사용하세요.");
        }
    }

    private static final Pattern SPREADSHEET_ID_PATTERN =
            Pattern.compile("/spreadsheets/d/([a-zA-Z0-9-_]+)");
    private static final Pattern GID_PATTERN = Pattern.compile("[?&#]gid=(\\d+)");

    /**
     * 공개 CSV 내보내기 URL로 스프레드시트를 가져와 엑셀과 동일 규칙으로 파싱합니다.
     * 스프레드시트는 "링크가 있는 모든 사용자"에게 보기 권한이 있어야 합니다.
     */
    public CrewUploadResponse importFromUrlOrId(String urlOrId) throws Exception {
        String trimmed = urlOrId.trim();
        String id = extractSpreadsheetId(trimmed);
        String gid = extractGid(trimmed);

        StringBuilder export = new StringBuilder();
        export.append("https://docs.google.com/spreadsheets/d/").append(id).append("/export?format=csv");
        if (gid != null && !gid.isEmpty()) {
            export.append("&gid=").append(gid);
        }

        HttpClient client = buildHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(export.toString()))
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "text/csv,*/*")
                .GET()
                .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        int code = response.statusCode();
        if (code == 403) {
            throw new IllegalArgumentException(
                    "스프레드시트에 접근할 수 없습니다. Google 스프레드시트에서 "
                            + "'공유' → 링크가 있는 사용자에게 '보기' 권한을 주거나, 공개 후 다시 시도해 주세요.");
        }
        if (code == 404) {
            throw new IllegalArgumentException("스프레드시트를 찾을 수 없습니다. 링크 주소를 확인해 주세요.");
        }
        if (code != 200) {
            throw new IllegalArgumentException("Google 스프레드시트를 불러오지 못했습니다. (HTTP " + code + ")");
        }

        try (InputStream in = response.body();
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            CrewUploadResponse result = excelService.parseCrewCsv(reader);
            if (result.getCrew() == null || result.getCrew().isEmpty()) {
                throw new IllegalArgumentException(
                        "사번·이름이 있는 데이터 행이 없습니다. 첫 행에 헤더(사번, 이름, BASE 등)가 있는지 확인해 주세요.");
            }
            return result;
        }
    }

    private HttpClient buildHttpClient() throws Exception {
        // NORMAL은 301/302/303만 따라가며, Google export가 307을 줄 수 있어 ALWAYS 사용
        HttpClient.Builder b = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.ALWAYS);
        if (trustAllCertificates) {
            b.sslContext(insecureSslContext());
        }
        return b.build();
    }

    private static SSLContext insecureSslContext() throws Exception {
        TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
        };
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(null, trustAll, new SecureRandom());
        return ssl;
    }

    private String extractSpreadsheetId(String input) {
        Matcher m = SPREADSHEET_ID_PATTERN.matcher(input);
        if (m.find()) {
            return m.group(1);
        }
        if (input.matches("[a-zA-Z0-9-_]{10,}")) {
            return input;
        }
        throw new IllegalArgumentException(
                "Google 스프레드시트 URL 또는 스프레드시트 ID를 입력해 주세요. "
                        + "(예: https://docs.google.com/spreadsheets/d/문서ID/edit)");
    }

    private String extractGid(String input) {
        Matcher m = GID_PATTERN.matcher(input);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }
}
