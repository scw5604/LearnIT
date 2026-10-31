package com.rapidtutor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "rapidtutor.demo-users.enabled=false")
public class UiEdgeCaseTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "student1", roles = {"STUDENT"})
    public void testFormSubmissionWithInvalidCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/student/requests").with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
    }
}
