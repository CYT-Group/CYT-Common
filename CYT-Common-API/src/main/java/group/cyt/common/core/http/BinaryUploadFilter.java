package group.cyt.common.core.http;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Stop Spring's form/multipart parsers from consuming a raw request before authorization. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class BinaryUploadFilter extends OncePerRequestFilter {
  public static final String ORIGINAL_TYPE = BinaryUploadFilter.class.getName() + ".contentType";
  private static final org.springframework.web.util.pattern.PathPattern UPLOAD_PATH =
      org.springframework.web.util.pattern.PathPatternParser.defaultInstance.parse(
          "/integrations/nas/objects/upload");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!request.getMethod().equals("PUT")
        || !UPLOAD_PATH.matches(
            org.springframework.http.server.RequestPath.parse(
                    request.getRequestURI(), request.getContextPath())
                .pathWithinApplication())) {
      chain.doFilter(request, response);
      return;
    }
    request.setAttribute(
        ORIGINAL_TYPE, request.getContentType() == null ? "" : request.getContentType());
    chain.doFilter(
        new HttpServletRequestWrapper(request) {
          @Override
          public String getContentType() {
            return "application/octet-stream";
          }

          @Override
          public String getHeader(String name) {
            return name.equalsIgnoreCase("Content-Type") ? getContentType() : super.getHeader(name);
          }

          @Override
          public java.util.Enumeration<String> getHeaders(String name) {
            return name.equalsIgnoreCase("Content-Type")
                ? java.util.Collections.enumeration(java.util.List.of(getContentType()))
                : super.getHeaders(name);
          }
        },
        response);
  }
}
