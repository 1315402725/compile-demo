package com.demo.products.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created by dd on 2018/1/2.
 */
public class ErrorResponse extends Response {

  private String url;
  @JsonProperty("http_status")
  private int httpStatus;

  public ErrorResponse(String url, String msg, int status) {
    super(2, msg, null);
    this.url = url;
    this.httpStatus = status;
  }

  public int getHttpStatus() {
    return httpStatus;
  }

  public String getUrl() {
    return url;
  }
}

