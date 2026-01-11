package workflow.core.engine.infrastructure.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Tenant Context Filter (v2) Extracts tenant ID from HTTP headers and sets it in TenantContext */
@Slf4j
@Component
public class TenantContextFilter extends OncePerRequestFilter {

  private static final String TENANT_HEADER = "X-Tenant-Id";
  private static final String DEFAULT_TENANT = "default";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      String tenantId = extractTenantId(request);
      TenantContext.setTenantId(tenantId);
      log.debug("Request processing with tenant: {}", tenantId);
      filterChain.doFilter(request, response);
    } finally {
      TenantContext.clear();
    }
  }

  private String extractTenantId(HttpServletRequest request) {
    String tenantId = request.getHeader(TENANT_HEADER);
    if (tenantId == null || tenantId.trim().isEmpty()) {
      tenantId = DEFAULT_TENANT;
    }
    return tenantId;
  }
}
