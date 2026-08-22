package com.schw.urlshortener.link.cache;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class CacheConfig {

  @Bean
  LinkCache linkCache(Clock clock) {
    return new CaffeineLinkCache(clock);
  }
}
