package com.schw.urlshortener.link.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.schw.urlshortener.link.cache.LinkCache;
import com.schw.urlshortener.link.domain.HostnameResolver;
import com.schw.urlshortener.link.domain.ShortCode;
import com.schw.urlshortener.link.domain.ShortLink;
import com.schw.urlshortener.link.domain.TargetUrl;
import com.schw.urlshortener.link.persistence.AliasConflictException;
import com.schw.urlshortener.link.persistence.ShortLinkRepository;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = LinksController.class)
@Import(LinksControllerTest.TestSupport.class)
class LinksControllerTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  private static final TargetUrl TARGET_URL = TargetUrl.reconstitute("https://example.com/x");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @MockitoBean private ShortLinkRepository repository;
  @MockitoBean private LinkCache cache;

  // --- POST /api/v1/links ---

  @Test
  void createWithGeneratedCodeReturns201AndTheShortLinkBody() throws Exception {
    ShortCode generated = ShortCode.reconstitute("aB3xK9z");
    ShortLink saved = new ShortLink(generated, TARGET_URL, "dev-key", NOW, Optional.empty());
    given(repository.saveGenerated(any(), eq("dev-key"), eq(NOW), any())).willReturn(saved);

    mockMvc
        .perform(
            post("/api/v1/links")
                .header("X-API-Key", "dev-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new CreateShortLinkRequest("https://example.com/x", null, null))))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/links/aB3xK9z"))
        .andExpect(jsonPath("$.code").value("aB3xK9z"))
        .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/aB3xK9z"))
        .andExpect(jsonPath("$.active").value(true));
  }

  @Test
  void createWithAliasUsesTheAliasAsTheCode() throws Exception {
    ShortCode alias = ShortCode.fromAlias("launch");
    ShortLink saved = new ShortLink(alias, TARGET_URL, "dev-key", NOW, Optional.empty());
    given(repository.saveWithAlias(eq(alias), any(), eq("dev-key"), eq(NOW), any()))
        .willReturn(saved);

    mockMvc
        .perform(
            post("/api/v1/links")
                .header("X-API-Key", "dev-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new CreateShortLinkRequest("https://example.com/x", "launch", null))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value("launch"));
  }

  @Test
  void createWithoutApiKeyReturns401ProblemJson() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new CreateShortLinkRequest("https://example.com/x", null, null))))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  void createWithInvalidTargetUrlReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/links")
                .header("X-API-Key", "dev-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new CreateShortLinkRequest("javascript:alert(1)", null, null))))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("https://example.com/probs/invalid-target-url"));
  }

  @Test
  void createWithMalformedAliasReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/links")
                .header("X-API-Key", "dev-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new CreateShortLinkRequest("https://example.com/x", "a", null))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("https://example.com/probs/malformed-alias"));
  }

  @Test
  void createWithReservedAliasReturns409() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/links")
                .header("X-API-Key", "dev-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new CreateShortLinkRequest("https://example.com/x", "health", null))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("https://example.com/probs/reserved-alias"));
  }

  @Test
  void createWithTakenAliasReturns409() throws Exception {
    ShortCode alias = ShortCode.fromAlias("launch");
    given(repository.saveWithAlias(eq(alias), any(), eq("dev-key"), eq(NOW), any()))
        .willThrow(new AliasConflictException("launch"));

    mockMvc
        .perform(
            post("/api/v1/links")
                .header("X-API-Key", "dev-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new CreateShortLinkRequest("https://example.com/x", "launch", null))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("https://example.com/probs/alias-taken"));
  }

  // --- GET /api/v1/links/{code} ---

  @Test
  void getMetadataReturns200WithClickCount() throws Exception {
    ShortLink shortLink =
        new ShortLink(
            ShortCode.reconstitute("aB3xK9z"), TARGET_URL, "dev-key", NOW, Optional.empty());
    given(repository.findByCode(ShortCode.reconstitute("aB3xK9z")))
        .willReturn(Optional.of(shortLink));

    mockMvc
        .perform(get("/api/v1/links/aB3xK9z").header("X-API-Key", "dev-key"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.clickCount").value(0));
  }

  @Test
  void getMetadataReportsANonZeroPersistedClickCount() throws Exception {
    ShortLink shortLink =
        new ShortLink(
            ShortCode.reconstitute("aB3xK9z"), TARGET_URL, "dev-key", NOW, Optional.empty(), 7L);
    given(repository.findByCode(ShortCode.reconstitute("aB3xK9z")))
        .willReturn(Optional.of(shortLink));

    mockMvc
        .perform(get("/api/v1/links/aB3xK9z").header("X-API-Key", "dev-key"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.clickCount").value(7));
  }

  @Test
  void getMetadataForUnissuedCodeReturns404() throws Exception {
    given(repository.findByCode(ShortCode.reconstitute("nvrissud"))).willReturn(Optional.empty());

    mockMvc
        .perform(get("/api/v1/links/nvrissud").header("X-API-Key", "dev-key"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  void getMetadataWithoutApiKeyReturns401() throws Exception {
    mockMvc.perform(get("/api/v1/links/aB3xK9z")).andExpect(status().isUnauthorized());
  }

  // --- DELETE /api/v1/links/{code} ---

  @Test
  void deactivateByOwnerReturns204() throws Exception {
    ShortLink shortLink =
        new ShortLink(
            ShortCode.reconstitute("aB3xK9z"), TARGET_URL, "dev-key", NOW, Optional.empty());
    given(repository.findByCode(ShortCode.reconstitute("aB3xK9z")))
        .willReturn(Optional.of(shortLink));

    mockMvc
        .perform(delete("/api/v1/links/aB3xK9z").header("X-API-Key", "dev-key"))
        .andExpect(status().isNoContent());

    verify(cache).invalidate(ShortCode.reconstitute("aB3xK9z"));
  }

  @Test
  void deactivateByNonOwnerReturns403() throws Exception {
    ShortLink shortLink =
        new ShortLink(
            ShortCode.reconstitute("aB3xK9z"), TARGET_URL, "someone-else", NOW, Optional.empty());
    given(repository.findByCode(ShortCode.reconstitute("aB3xK9z")))
        .willReturn(Optional.of(shortLink));

    mockMvc
        .perform(delete("/api/v1/links/aB3xK9z").header("X-API-Key", "dev-key"))
        .andExpect(status().isForbidden())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

    verify(cache, never()).invalidate(any());
  }

  @Test
  void deactivateOfUnissuedCodeReturns404() throws Exception {
    given(repository.findByCode(ShortCode.reconstitute("nvrissud"))).willReturn(Optional.empty());

    mockMvc
        .perform(delete("/api/v1/links/nvrissud").header("X-API-Key", "dev-key"))
        .andExpect(status().isNotFound());
  }

  @Test
  void deactivateWithoutApiKeyReturns401() throws Exception {
    mockMvc.perform(delete("/api/v1/links/aB3xK9z")).andExpect(status().isUnauthorized());
  }

  private String json(CreateShortLinkRequest request) {
    return objectMapper.writeValueAsString(request);
  }

  static class TestSupport {

    @Bean
    HostnameResolver hostnameResolver() {
      return host -> resolvePublicAddress();
    }

    @Bean
    Clock clock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private static List<InetAddress> resolvePublicAddress() throws UnknownHostException {
      return List.of(InetAddress.getByAddress(new byte[] {93, (byte) 184, (byte) 216, 34}));
    }
  }
}
