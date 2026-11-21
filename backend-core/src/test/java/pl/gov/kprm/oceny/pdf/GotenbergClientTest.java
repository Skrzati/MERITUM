package pl.gov.kprm.oceny.pdf;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GotenbergClientTest {
    private static final String URL = "http://gotenberg:3000/forms/chromium/convert/html";

    @Test
    void postsHtmlAsIndexFileAndRequestsPdfA() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("filename=\"index.html\""),
                        org.hamcrest.Matchers.containsString(GotenbergClient.PDF_A_FORMAT),
                        org.hamcrest.Matchers.containsString("<h1>Arkusz</h1>"))))
                .andRespond(withSuccess(new byte[]{'%', 'P', 'D', 'F'}, MediaType.APPLICATION_PDF));

        byte[] pdf = new GotenbergClient(builder, URL).htmlToPdf("<h1>Arkusz</h1>");

        assertThat(new String(pdf)).isEqualTo("%PDF");
        server.verify();
    }

    @Test
    void mapsGotenbergFailureTo503() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> new GotenbergClient(builder, URL).htmlToPdf("<p/>"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("503");
    }
}
