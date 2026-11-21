package pl.gov.kprm.oceny.pdf;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

/** Konwersja HTML -> PDF/A przez Gotenberg (Chromium). */
@Component
public class GotenbergClient {
    static final String PDF_A_FORMAT = "PDF/A-2b";
    private static final String A4_WIDTH_IN = "8.27";
    private static final String A4_HEIGHT_IN = "11.7";
    private static final String NO_MARGIN = "0";

    private final RestClient http;
    private final String convertUrl;

    public GotenbergClient(RestClient.Builder builder, @Value("${app.gotenberg-url}") String convertUrl) {
        this.http = builder.build();
        this.convertUrl = convertUrl;
    }

    public byte[] htmlToPdf(String html) {
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8));
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("files", new HttpEntity<>(new NamedResource(html.getBytes(StandardCharsets.UTF_8), "index.html"),
                fileHeaders));
        body.add("pdfa", PDF_A_FORMAT);
        body.add("pdfua", "true");
        body.add("preferCssPageSize", "true");
        body.add("printBackground", "true");
        body.add("paperWidth", A4_WIDTH_IN);
        body.add("paperHeight", A4_HEIGHT_IN);
        for (String side : new String[]{"marginTop", "marginBottom", "marginLeft", "marginRight"}) {
            body.add(side, NO_MARGIN);
        }
        try {
            return http.post().uri(convertUrl)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(byte[].class);
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Generator PDF (Gotenberg) niedostępny", e);
        }
    }

    private static final class NamedResource extends ByteArrayResource {
        private final String filename;

        NamedResource(byte[] bytes, String filename) {
            super(bytes);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
