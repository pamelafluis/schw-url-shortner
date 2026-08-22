package com.schw.urlshortener.link.domain;

public class InvalidTargetUrlException extends RuntimeException {

  public InvalidTargetUrlException(String message) {
    super(message);
  }
}
