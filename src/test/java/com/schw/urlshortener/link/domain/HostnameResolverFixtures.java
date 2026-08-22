package com.schw.urlshortener.link.domain;

/**
 * Shared test doubles for {@link HostnameResolver}, used by any domain test that needs a TargetUrl
 * but isn't itself testing DNS-resolution behavior.
 */
final class HostnameResolverFixtures {

  private HostnameResolverFixtures() {}

  static HostnameResolver refusingResolver() {
    return host -> {
      throw new AssertionError("resolver should not be consulted for an IP literal host: " + host);
    };
  }
}
