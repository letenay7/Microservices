package org.example.auth;

import org.example.auth.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SecurityIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Test
    public void testUnauthorizedAccess() throws Exception {
        mockMvc.perform(get("/api/test/secure"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void testAuthenticatedAccess() throws Exception {
        String token = jwtService.generateToken("AuthorizedUser");
        mockMvc.perform(get("/api/test/secure")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

    }
}
