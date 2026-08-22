package com.schw.urlshortener.link.domain;

import static com.schw.urlshortener.link.domain.HostnameResolverFixtures.refusingResolver;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class TargetUrlTest {

  private static final String OWN_HOST = "sho.rt";

  @Test
  void acceptsAValidHttpsUrlWithAPubliclyResolvableHost() {
    HostnameResolver resolver = resolverReturning("93.184.216.34");

    TargetUrl targetUrl = TargetUrl.of("https://example.com/path", OWN_HOST, resolver);

    assertThat(targetUrl.value()).isEqualTo("https://example.com/path");
  }

  @Test
  void rejectsSchemesOtherThanHttpAndHttps() {
    HostnameResolver resolver = resolverReturning("93.184.216.34");

    assertThatExceptionOfType(InvalidTargetUrlException.class)
        .isThrownBy(() -> TargetUrl.of("javascript:alert(1)", OWN_HOST, resolver));
  }

  @Test
  void rejectsAUrlWhoseHostDoesNotParse() {
    HostnameResolver resolver = resolverReturning("93.184.216.34");

    assertThatExceptionOfType(InvalidTargetUrlException.class)
        .isThrownBy(() -> TargetUrl.of("https:///no-host", OWN_HOST, resolver));
  }

  @Test
  void rejectsLocalhostRegardlessOfCase() {
    HostnameResolver resolver = refusingResolver();

    assertThatExceptionOfType(InvalidTargetUrlException.class)
        .isThrownBy(() -> TargetUrl.of("http://localhost/x", OWN_HOST, resolver));
    assertThatExceptionOfType(InvalidTargetUrlException.class)
        .isThrownBy(() -> TargetUrl.of("http://LocalHost/x", OWN_HOST, resolver));
  }

  @Test
  void rejectsLoopbackIpLiteralWithoutConsultingTheResolver() {
    HostnameResolver resolver = refusingResolver();

    assertThatExceptionOfType(InvalidTargetUrlException.class)
        .isThrownBy(() -> TargetUrl.of("http://127.0.0.1/x", OWN_HOST, resolver));
  }

  @Test
  void rejectsPrivateRangeIpLiterals() {
    HostnameResolver resolver = refusingResolver();

    for (String ip : new String[] {"10.0.0.5", "172.16.0.5", "192.168.1.5"}) {
      assertThatExceptionOfType(InvalidTargetUrlException.class)
          .describedAs("private IP literal '%s' should be rejected", ip)
          .isThrownBy(() -> TargetUrl.of("http://" + ip + "/x", OWN_HOST, resolver));
    }
  }

  @Test
  void rejectsLinkLocalIpLiteralIncludingCloudMetadataAddress() {
    HostnameResolver resolver = refusingResolver();

    assertThatExceptionOfType(InvalidTargetUrlException.class)
        .isThrownBy(() -> TargetUrl.of("http://169.254.169.254/x", OWN_HOST, resolver));
  }

  @Test
  void acceptsAPublicIpLiteralWithoutConsultingTheResolver() {
    HostnameResolver resolver = refusingResolver();

    TargetUrl targetUrl = TargetUrl.of("http://93.184.216.34/x", OWN_HOST, resolver);

    assertThat(targetUrl.value()).isEqualTo("http://93.184.216.34/x");
  }

  @Test
  void rejectsAHostnameThatResolvesToAPrivateAddress() {
    HostnameResolver resolver = resolverReturning("10.0.0.5");

    assertThatExceptionOfType(InvalidTargetUrlException.class)
        .isThrownBy(() -> TargetUrl.of("https://internal.example.com/x", OWN_HOST, resolver));
  }

  @Test
  void rejectsAHostnameWhenAnyResolvedAddressIsBlocked() {
    HostnameResolver resolver = resolverReturning("93.184.216.34", "169.254.169.254");

    assertThatExceptionOfType(InvalidTargetUrlException.class)
        .isThrownBy(() -> TargetUrl.of("https://multi.example.com/x", OWN_HOST, resolver));
  }

  @Test
  void acceptsAHostnameWhereAllResolvedAddressesArePublic() {
    HostnameResolver resolver = resolverReturning("93.184.216.34", "203.0.113.7");

    TargetUrl targetUrl = TargetUrl.of("https://multi-public.example.com/x", OWN_HOST, resolver);

    assertThat(targetUrl.value()).isEqualTo("https://multi-public.example.com/x");
  }

  @Test
  void rejectsAHostnameTheResolverCannotResolve() {
    HostnameResolver resolver =
        host -> {
          throw new UnknownHostException(host);
        };

    assertThatExceptionOfType(InvalidTargetUrlException.class)
        .isThrownBy(() -> TargetUrl.of("https://nowhere.example.com/x", OWN_HOST, resolver));
  }

  @Test
  void rejectsTheServicesOwnHostRegardlessOfCase() {
    // own-host is checked last, after DNS resolution (per spec order), so the
    // resolver must still answer for it even though the host will be rejected.
    HostnameResolver resolver = resolverReturning("93.184.216.34");

    assertThatExceptionOfType(InvalidTargetUrlException.class)
        .isThrownBy(() -> TargetUrl.of("https://sho.rt/loop", OWN_HOST, resolver));
    assertThatExceptionOfType(InvalidTargetUrlException.class)
        .isThrownBy(() -> TargetUrl.of("https://Sho.Rt/loop", OWN_HOST, resolver));
  }

  @Test
  void reconstituteProducesTargetUrlWithTheStoredValueWithoutValidation() {
    // A persisted value was already SSRF-validated once, at creation. Reconstitution
    // must not repeat DNS resolution or own-host checks on every read — those are
    // step-4 (API) concerns, not the persistence layer's.
    TargetUrl targetUrl = TargetUrl.reconstitute("https://example.com/path");

    assertThat(targetUrl.value()).isEqualTo("https://example.com/path");
  }

  private static HostnameResolver resolverReturning(String... literalIps) {
    List<InetAddress> addresses =
        Arrays.stream(literalIps).map(TargetUrlTest::literalAddress).toList();
    return host -> addresses;
  }

  private static InetAddress literalAddress(String ip) {
    try {
      return InetAddress.getByName(ip);
    } catch (UnknownHostException e) {
      throw new AssertionError(e);
    }
  }
}
