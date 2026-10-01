package com.healthcare.hms.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Pagination metadata per API.md section 3: {@code {page, size, totalElements, totalPages}}.
 *
 * @param page zero-based page index
 * @param size page size
 * @param totalElements total number of elements across all pages
 * @param totalPages total number of pages
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PageMeta(int page, int size, long totalElements, int totalPages) {}
