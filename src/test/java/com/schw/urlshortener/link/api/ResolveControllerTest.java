package com.schw.urlshortener.link.api;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.schw.urlshortener.link.domain.ShortCode;
import com.schw.urlshortener.link.domain.ShortLink;
import com.schw.urlshortener.link.domain.TargetUrl;
import com.schw.urlshortener.link.persistence.ShortLinkRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ResolveController.class)
@Import(ResolveControllerTest.TestSupport.class)
class ResolveControllerTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  private static final TargetUrl TARGET_URL = TargetUrl.reconstitute("https://example.com/x");

  @Autowired private MockMvc mockMvc;
  @MockitoBean private ShortLinkRepository repository;

  @Test
  void resolvableCodeRedirectsWithNoStoreCacheControl() throws Exception {
    ShortLink shortLink =
        new ShortLink(
            ShortCode.reconstitute("aB3xK9z"), TARGET_URL, "dev-key", NOW, Optional.empty());
    given(repository.findByCode(ShortCode.reconstitute("aB3xK9z")))
        .willReturn(Optional.of(shortLink));

    mockMvc
        .perform(get("/aB3xK9z"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://example.com/x"))
        .andExpect(header().string("Cache-Control", "no-store"));
  }

  @Test
  void expiredCodeReturns410() throws Exception {
    ShortLink shortLink =
        new ShortLink(
            ShortCode.reconstitute("aB3xK9z"),
            TARGET_URL,
            "dev-key",
            NOW.minusSeconds(7200),
            Optional.of(NOW.minusSeconds(3600)));
    given(repository.findByCode(ShortCode.reconstitute("aB3xK9z")))
        .willReturn(Optional.of(shortLink));

    mockMvc
        .perform(get("/aB3xK9z"))
        .andExpect(status().isGone())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  void deactivatedCodeReturns410() throws Exception {
    ShortLink shortLink =
        new ShortLink(
            ShortCode.reconstitute("aB3xK9z"), TARGET_URL, "dev-key", NOW, Optional.empty());
    shortLink.deactivate();
    given(repository.findByCode(ShortCode.reconstitute("aB3xK9z")))
        .willReturn(Optional.of(shortLink));

    mockMvc.perform(get("/aB3xK9z")).andExpect(status().isGone());
  }

  @Test
  void codeThatWasNeverIssuedReturns404() throws Exception {
    given(repository.findByCode(ShortCode.reconstitute("nvrissud"))).willReturn(Optional.empty());

    mockMvc
        .perform(get("/nvrissud"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  void resolveRequiresNoApiKey() throws Exception {
    ShortLink shortLink =
        new ShortLink(
            ShortCode.reconstitute("aB3xK9z"), TARGET_URL, "dev-key", NOW, Optional.empty());
    given(repository.findByCode(ShortCode.reconstitute("aB3xK9z")))
        .willReturn(Optional.of(shortLink));

    mockMvc.perform(get("/aB3xK9z")).andExpect(status().isFound());
  }

  static class TestSupport {

    @Bean
    Clock clock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }
}
