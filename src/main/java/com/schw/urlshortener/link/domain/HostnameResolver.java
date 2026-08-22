package com.schw.urlshortener.link.domain;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Port for DNS resolution, so TargetUrl validation stays deterministic and
 * network-free in domain tests. The real adapter is wired at the API layer (step 4).
 */
public interface HostnameResolver {

	List<InetAddress> resolve(String host) throws UnknownHostException;

}
