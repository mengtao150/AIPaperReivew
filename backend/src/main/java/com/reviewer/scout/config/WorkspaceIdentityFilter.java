package com.reviewer.scout.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.HexFormat;

@Component
public class WorkspaceIdentityFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String workspace = null;
        if (request.getCookies() != null) for (Cookie cookie : request.getCookies()) if (cookie.getName().equals("reviewer_workspace") && cookie.getValue().matches("[a-f0-9]{64}")) workspace = cookie.getValue();
        if (workspace == null) { byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes); workspace = HexFormat.of().formatHex(bytes); }
        response.addHeader("Set-Cookie", ResponseCookie.from("reviewer_workspace", workspace).httpOnly(true).secure(request.isSecure()).sameSite("Strict").path("/api").maxAge(365L * 24 * 3600).build().toString());
        request.setAttribute("workspace", workspace); response.setHeader("Cache-Control", "no-store"); chain.doFilter(request, response);
    }
}
