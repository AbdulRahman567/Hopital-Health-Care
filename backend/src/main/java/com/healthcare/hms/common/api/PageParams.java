package com.healthcare.hms.common.api;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Request pagination parameters with API.md section 4 rules applied: default size 20, maximum size
 * 100, page index never below 0.
 */
public record PageParams(int page, int size) {

  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 100;
  public static final int DEFAULT_PAGE = 0;

  /** Builds clamped page parameters; {@code null} values fall back to the defaults. */
  public static PageParams of(Integer page, Integer size) {
    int resolvedPage = page == null || page < DEFAULT_PAGE ? DEFAULT_PAGE : page;
    int resolvedSize = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    return new PageParams(resolvedPage, resolvedSize);
  }

  /** Converts to a Spring Data {@link PageRequest} with the given sort. */
  public PageRequest toPageRequest(Sort sort) {
    return PageRequest.of(page, size, sort);
  }
}
