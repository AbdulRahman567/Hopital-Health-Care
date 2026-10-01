package com.healthcare.hms.common.api;

import org.springframework.data.domain.Page;

/** Maps Spring Data pages to {@link PageMeta} per API.md section 3. */
public final class PaginationMapper {

  private PaginationMapper() {}

  /** Derives pagination metadata from a Spring Data {@link Page}. */
  public static PageMeta from(Page<?> page) {
    return new PageMeta(
        page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
  }
}
