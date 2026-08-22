package com.schw.urlshortener.link.click;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ClickRegistryConfig {

  @Bean
  ClickRegistry clickRegistry() {
    return new ClickRegistry();
  }
}
