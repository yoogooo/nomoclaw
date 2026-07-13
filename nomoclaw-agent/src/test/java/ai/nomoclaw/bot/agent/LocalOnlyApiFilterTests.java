package ai.nomoclaw.bot.agent;

import ai.nomoclaw.bot.config.AgentProperties;
import ai.nomoclaw.bot.api.filter.LocalOnlyApiFilter;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalOnlyApiFilterTests {

    @Test
    void shouldAllowLoopbackApiRequest() throws ServletException, IOException {
        LocalOnlyApiFilter filter = new LocalOnlyApiFilter(agentProperties(true));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/conversations");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertTrue(chain.getRequest() != null);
    }

    @Test
    void shouldRejectRemoteApiRequest() throws ServletException, IOException {
        LocalOnlyApiFilter filter = new LocalOnlyApiFilter(agentProperties(true));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/conversations");
        request.setRemoteAddr("8.8.8.8");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(403, response.getStatus());
        assertEquals("{\"message\":\"API is restricted to local machine access\"}", response.getContentAsString());
    }

    @Test
    void shouldBypassRestrictionWhenDisabled() throws ServletException, IOException {
        LocalOnlyApiFilter filter = new LocalOnlyApiFilter(agentProperties(false));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/conversations");
        request.setRemoteAddr("8.8.8.8");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertTrue(chain.getRequest() != null);
    }

    private AgentProperties agentProperties(boolean localOnlyEnabled) {
        AgentProperties properties = new AgentProperties();
        properties.getApi().setLocalOnlyEnabled(localOnlyEnabled);
        return properties;
    }
}
