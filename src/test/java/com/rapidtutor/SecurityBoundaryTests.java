package com.rapidtutor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "rapidtutor.demo-users.enabled=false")
public class SecurityBoundaryTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "student1", roles = {"STUDENT"})
    public void testStudentCannotAccessTutorBoard() throws Exception {
        mockMvc.perform(get("/tutor/board"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "student1", roles = {"STUDENT"})
    public void testStudentCannotAccessAdminConsole() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "tutor1", roles = {"TUTOR"})
    public void testTutorCannotSubmitStudentRequests() throws Exception {
        mockMvc.perform(post("/student/requests").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testUnauthenticatedUserRedirectedToLogin() throws Exception {
        MvcResult result = mockMvc.perform(get("/student/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertTrue(result.getResponse().getRedirectedUrl().contains("/login"));
    }
}
