package com.nexaai.auth.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Locks in the Phase 0 boundary contract: internal endpoints are not anonymous, and the
 * service reports its own identity, port and owned data rather than another service's.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthServiceBoundaryTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsAnonymousAccessToInternalEndpoints() throws Exception {
        mockMvc.perform(get("/internal/v1/auth/info"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reportsOwnBoundary() throws Exception {
        mockMvc.perform(get("/internal/v1/auth/info").with(user("phase0").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("auth-service"))
                .andExpect(jsonPath("$.port").value(8081))
                .andExpect(jsonPath("$.ownedDatabase").value("nexa_auth"));
    }

    @Test
    void keepsHealthEndpointPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
