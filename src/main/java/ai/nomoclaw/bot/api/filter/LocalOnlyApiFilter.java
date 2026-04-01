package ai.nomoclaw.bot.api.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import ai.nomoclaw.bot.config.AgentProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class LocalOnlyApiFilter extends OncePerRequestFilter {

    private final AgentProperties agentProperties;

    public LocalOnlyApiFilter(AgentProperties agentProperties) {
        this.agentProperties = agentProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!agentProperties.getApi().isLocalOnlyEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        String remoteAddr = request.getRemoteAddr();
        if (isLocalAddress(remoteAddr)) {
            filterChain.doFilter(request, response);
            return;
        }
        log.warn("[HTTP][REJECT] method={} uri={} remote={} reason=local access only",
                request.getMethod(), request.getRequestURI(), remoteAddr);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"message\":\"API is restricted to local machine access\"}");
    }

    boolean isLocalAddress(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(remoteAddr);
            if (address.isAnyLocalAddress() || address.isLoopbackAddress()) {
                return true;
            }
            return NetworkInterface.getByInetAddress(address) != null;
        } catch (Exception ex) {
            log.debug("[HTTP] failed to resolve remote address: {}", remoteAddr, ex);
            return false;
        }
    }
}
