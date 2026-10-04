package com.healthcare.hms.authz;

import com.healthcare.hms.authz.api.PermissionResponse;
import com.healthcare.hms.common.api.PageParams;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Read side of the permission catalog: {@code GET /api/v1/permissions}.
 *
 * <p>Served from {@link PermissionCatalog} rather than the {@code permissions} table. The two are
 * the same 53 rows in both directions &mdash; {@code PermissionCatalogTest} proves it on every
 * build &mdash; so a round-trip would buy nothing and could only ever disagree. The endpoint is
 * gated by {@code ROLE_VIEW} (decision D5: the frozen catalog has no {@code PERMISSION} module, and
 * extending the seed is exactly what D6 defers).
 */
@Service
public class PermissionService {

  /** One page of the catalog, code-ordered, with the platform-only flag spelled out. */
  public Page<PermissionResponse> list(PageParams params) {
    List<PermissionResponse> all =
        java.util.Arrays.stream(PermissionCatalog.values())
            .map(
                permission ->
                    new PermissionResponse(
                        permission.code(),
                        permission.module(),
                        permission.action(),
                        permission.description(),
                        permission.isPlatformOnly()))
            .toList();

    Pageable pageable = params.toPageRequest(org.springframework.data.domain.Sort.unsorted());
    int from = (int) Math.min(pageable.getOffset(), all.size());
    int to = (int) Math.min(pageable.getOffset() + pageable.getPageSize(), all.size());
    return new PageImpl<>(all.subList(from, to), pageable, all.size());
  }
}
