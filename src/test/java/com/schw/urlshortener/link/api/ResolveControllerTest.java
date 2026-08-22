package com.schw.urlshortener.link.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.schw.urlshortener.link.cache.LinkCache;
import com.schw.urlshortener.link.click.ClickRegistry;
import com.schw.urlshortener.link.domain.ShortCode;
import com.schw.urlshortener.link.domain.ShortLink;
import com.schw.urlshortener.link.domain.TargetUrl;
import com.schw.urlshortener.link.persistence.ShortLinkRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
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
  @Autowired private ClickRegistry clickRegistry;
  @Autowired private MeterRegistry meterRegistry;
  @MockitoBean private ShortLinkRepository repository;
  @MockitoBean private LinkCache cache;

  @BeforeEach
  void resetClickRegistry() {
    // The registry is a singleton reused across test methods in this Spring context; drain
    // discards any increment left over from a previous test so state doesn't leak between them.
    clickRegistry.drain();
  }

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
  void resolvableCodeIncrementsTheClickRegistry() throws Exception {
    ShortLink shortLink =
        new ShortLink(
            ShortCode.reconstitute("aB3xK9z"), TARGET_URL, "dev-key", NOW, Optional.empty());
    given(repository.findByCode(ShortCode.reconstitute("aB3xK9z")))
        .willReturn(Optional.of(shortLink));

    mockMvc.perform(get("/aB3xK9z")).andExpect(status().isFound());

    assertThat(clickRegistry.drain()).containsEntry(ShortCode.reconstitute("aB3xK9z"), 1L);
  }

  @Test
  void expiredCodeDoesNotIncrementTheClickRegistry() throws Exception {
    ShortLink shortLink =
        new ShortLink(
            ShortCode.reconstitute("aB3xK9z"),
            TARGET_URL,
            "dev-key",
            NOW.minusSeconds(7200),
            Optional.of(NOW.minusSeconds(3600)));
    given(repository.findByCode(ShortCode.reconstitute("aB3xK9z")))
        .willReturn(Optional.of(shortLink));

    mockMvc.perform(get("/aB3xK9z")).andExpect(status().isGone());

    assertThat(clickRegistry.drain()).isEmpty();
  }

  @Test
  void deactivatedCodeDoesNotIncrementTheClickRegistry() throws Exception {
    ShortLink shortLink =
        new ShortLink(
            ShortCode.reconstitute("aB3xK9z"), TARGET_URL, "dev-key", NOW, Optional.empty());
    shortLink.deactivate();
    given(repository.findByCode(ShortCode.reconstitute("aB3xK9z")))
        .willReturn(Optional.of(shortLink));

    mockMvc.perform(get("/aB3xK9z")).andExpect(status().isGone());

    assertThat(clickRegistry.drain()).isEmpty();
  }

  @Test
  void codeThatWasNeverIssuedDoesNotIncrementTheClickRegistry() throws Exception {
    given(repository.findByCode(ShortCode.reconstitute("nvrissud"))).willReturn(Optional.empty());

    mockMvc.perform(get("/nvrissud")).andExpect(status().isNotFound());

    assertThat(clickRegistry.drain()).isEmpty();
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

  // --- Request ID (issue #13): RequestIdFilter runs ahead of every request, including this one ---

  @Test
  void responseCarriesAGeneratedRequestIdWhenCallerSuppliesNone() throws Exception {
    given(repository.findByCode(ShortCode.reconstitute("nvrissud"))).willReturn(Optional.empty());

    mockMvc.perform(get("/nvrissud")).andExpect(header().exists("X-Request-Id"));
  }

  @Test
  void twoRequestsWithoutASuppliedRequestIdGetDifferentGeneratedIds() throws Exception {
    given(repository.findByCode(ShortCode.reconstitute("nvrissud"))).willReturn(Optional.empty());

    String first =
        mockMvc.perform(get("/nvrissud")).andReturn().getResponse().getHeader("X-Request-Id");
    String second =
        mockMvc.perform(get("/nvrissud")).andReturn().getResponse().getHeader("X-Request-Id");

    assertThat(first).isNotBlank();
    assertThat(second).isNotBlank();
    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void aCallerSuppliedRequestIdIsEchoedBackUnchanged() throws Exception {
    given(repository.findByCode(ShortCode.reconstitute("nvrissud"))).willReturn(Optional.empty());

    mockMvc
        .perform(get("/nvrissud").header("X-Request-Id", "caller-supplied-id"))
        .andExpect(header().string("X-Request-Id", "caller-supplied-id"));
  }

  // --- Metrics (issue #13): outcome counter tagged per ResolutionOutcome, including not-found ---

  @Test
  void resolvableCodeIncrementsTheResolutionsCounterTaggedResolvable() throws Exception {
    ShortLink shortLink =
        new ShortLink(
            ShortCode.reconstitute("aB3xK9z"), TARGET_URL, "dev-key", NOW, Optional.empty());
    given(repository.findByCode(ShortCode.reconstitute("aB3xK9z")))
        .willReturn(Optional.of(shortLink));
    double before = meterRegistry.counter("link.resolutions", "outcome", "resolvable").count();

    mockMvc.perform(get("/aB3xK9z")).andExpect(status().isFound());

    assertThat(meterRegistry.counter("link.resolutions", "outcome", "resolvable").count())
        .isEqualTo(before + 1.0);
  }

  @Test
  void unissuedCodeIncrementsTheResolutionsCounterTaggedNotFound() throws Exception {
    given(repository.findByCode(ShortCode.reconstitute("nvrissud"))).willReturn(Optional.empty());
    double before = meterRegistry.counter("link.resolutions", "outcome", "not-found").count();

    mockMvc.perform(get("/nvrissud")).andExpect(status().isNotFound());

    assertThat(meterRegistry.counter("link.resolutions", "outcome", "not-found").count())
        .isEqualTo(before + 1.0);
  }

  @Test
  void cachedHitRedirectsWithoutConsultingPostgres() throws Exception {
    ShortLink shortLink =
        new ShortLink(
            ShortCode.reconstitute("aB3xK9z"), TARGET_URL, "dev-key", NOW, Optional.empty());
    given(cache.get(ShortCode.reconstitute("aB3xK9z")))
        .willReturn(Optional.of(Optional.of(shortLink)));

    mockMvc
        .perform(get("/aB3xK9z"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://example.com/x"));

    verify(repository, never()).findByCode(ShortCode.reconstitute("aB3xK9z"));
  }

  @Test
  void cachedHitThatIsNowExpiredReturns410() throws Exception {
    ShortLink shortLink =
        new ShortLink(
            ShortCode.reconstitute("aB3xK9z"),
            TARGET_URL,
            "dev-key",
            NOW.minusSeconds(7200),
            Optional.of(NOW.minusSeconds(3600)));
    given(cache.get(ShortCode.reconstitute("aB3xK9z")))
        .willReturn(Optional.of(Optional.of(shortLink)));

    mockMvc
        .perform(get("/aB3xK9z"))
        .andExpect(status().isGone())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

    verify(repository, never()).findByCode(ShortCode.reconstitute("aB3xK9z"));
  }

  @Test
  void cachedHitThatIsDeactivatedReturns410() throws Exception {
    ShortLink shortLink =
        new ShortLink(
            ShortCode.reconstitute("aB3xK9z"), TARGET_URL, "dev-key", NOW, Optional.empty());
    shortLink.deactivate();
    given(cache.get(ShortCode.reconstitute("aB3xK9z")))
        .willReturn(Optional.of(Optional.of(shortLink)));

    mockMvc.perform(get("/aB3xK9z")).andExpect(status().isGone());

    verify(repository, never()).findByCode(ShortCode.reconstitute("aB3xK9z"));
  }

  @Test
  void cachedNegativeEntryReturns404WithoutConsultingPostgres() throws Exception {
    given(cache.get(ShortCode.reconstitute("nvrissud"))).willReturn(Optional.of(Optional.empty()));

    mockMvc.perform(get("/nvrissud")).andExpect(status().isNotFound());

    verify(repository, never()).findByCode(ShortCode.reconstitute("nvrissud"));
  }

  @Test
  void cacheMissPopulatesTheCacheFromPostgres() throws Exception {
    ShortLink shortLink =
        new ShortLink(
            ShortCode.reconstitute("aB3xK9z"), TARGET_URL, "dev-key", NOW, Optional.empty());
    given(cache.get(ShortCode.reconstitute("aB3xK9z"))).willReturn(Optional.empty());
    given(repository.findByCode(ShortCode.reconstitute("aB3xK9z")))
        .willReturn(Optional.of(shortLink));

    mockMvc.perform(get("/aB3xK9z")).andExpect(status().isFound());

    verify(cache, times(1)).put(eq(ShortCode.reconstitute("aB3xK9z")), eq(Optional.of(shortLink)));
  }

  @Test
  void unissuedCacheMissPopulatesANegativeCacheEntry() throws Exception {
    given(cache.get(ShortCode.reconstitute("nvrissud"))).willReturn(Optional.empty());
    given(repository.findByCode(ShortCode.reconstitute("nvrissud"))).willReturn(Optional.empty());

    mockMvc.perform(get("/nvrissud")).andExpect(status().isNotFound());

    verify(cache, times(1)).put(ShortCode.reconstitute("nvrissud"), Optional.empty());
  }

  static class TestSupport {

    @Bean
    Clock clock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    @Bean
    ClickRegistry clickRegistry() {
      return new ClickRegistry();
    }

    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }
}
