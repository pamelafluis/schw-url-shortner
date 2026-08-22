package com.schw.urlshortener.link.api;

import com.schw.urlshortener.link.domain.HostnameResolver;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Real adapter for the domain's {@link HostnameResolver} port, backed by the JVM's DNS resolver.
 */
@Component
class DnsHostnameResolver implements HostnameResolver {

  @Override
  public List<InetAddress> resolve(String host) throws UnknownHostException {
    return List.of(InetAddress.getAllByName(host));
  }
}
