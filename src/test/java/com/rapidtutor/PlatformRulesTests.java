package com.rapidtutor;

import com.rapidtutor.model.TutoringRequest;
import com.rapidtutor.model.User;
import com.rapidtutor.repository.ReviewRepository;
import com.rapidtutor.repository.TutoringRequestRepository;
import com.rapidtutor.repository.UserRepository;
import com.rapidtutor.service.EconomyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:rapidtutor-rules;DB_CLOSE_DELAY=-1")
class PlatformRulesTests {

    private static final Set<String> DEMO_USERS = Set.of("student1", "tutor1", "admin");

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TutoringRequestRepository requestRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private EconomyService economyService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void resetRows() {
        reviewRepository.deleteAll();
        requestRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(user -> !DEMO_USERS.contains(user.getUsername()))
                .forEach(userRepository::delete);
        setBalance("student1", 100);
        setBalance("tutor1", 100);
    }

    @Test
    void seededAndRegisteredPasswordsAreBcrypt() throws Exception {
        User student = userRepository.findByUsername("student1");
        assertTrue(student.getPassword().startsWith("{bcrypt}"));
        assertTrue(passwordEncoder.matches("password", student.getPassword()));
        assertFalse(student.getPassword().contains("{noop}"));

        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("username", "newstudent")
                        .param("password", "secret-pass"))
                .andExpect(redirectedUrl("/login?registered=true"));

        User created = userRepository.findByUsername("newstudent");
        assertTrue(created.getPassword().startsWith("{bcrypt}"));
        assertTrue(passwordEncoder.matches("secret-pass", created.getPassword()));
        assertFalse(created.getPassword().contains("secret-pass"));
    }

    @Test
    void loginRoutesEachRole() throws Exception {
        mockMvc.perform(formLogin("/login").user("student1").password("password"))
                .andExpect(redirectedUrl("/student/dashboard"));
        mockMvc.perform(formLogin("/login").user("tutor1").password("password"))
                .andExpect(redirectedUrl("/tutor/board"));
        mockMvc.perform(formLogin("/login").user("admin").password("password"))
                .andExpect(redirectedUrl("/admin"));
    }

    @Test
    void rejectsBountiesOutsideZeroToFifty() {
        TutoringRequest request = newRequest(userRepository.findByUsername("student1"), "HCDD 412", "Bounds");

        assertThrows(IllegalArgumentException.class,
                () -> economyService.createRequestWithBounty("student1", request, -5));
        assertThrows(IllegalArgumentException.class,
                () -> economyService.createRequestWithBounty("student1", request, 51));
        assertEquals(100, userRepository.findByUsername("student1").getTokenBalance());
    }

    @Test
    void httpRejectsANegativeBounty() throws Exception {
        mockMvc.perform(post("/student/requests")
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf())
                        .param("course", "HCDD 412")
                        .param("topic", "Loops")
                        .param("shortDescription", "I am stuck on the loop condition.")
                        .param("bountyValue", "-5")
                        .param("preferredMeetingMethod", "Zoom")
                        .param("desiredResponseTime", "Today")
                        .param("expectedEffort", "Quick explanation (10-15 mins)"))
                .andExpect(redirectedUrl("/student/requests/new"))
                .andExpect(flash().attribute("errorMessage", "Bounty must be between 0 and 50 tokens."));

        assertEquals(100, userRepository.findByUsername("student1").getTokenBalance());
        assertEquals(0, requestRepository.count());
    }

    @Test
    void studentCannotCancelOrViewAnotherStudentsRequest() throws Exception {
        User owner = saveStudent("student2");
        TutoringRequest request = requestRepository.save(newRequest(owner, "HCDD 412", "Owner only"));

        mockMvc.perform(post("/student/requests/" + request.getId() + "/cancel")
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf()))
                .andExpect(redirectedUrl("/student/dashboard"))
                .andExpect(flash().attribute("errorMessage", "You can only manage your own requests."));

        mockMvc.perform(get("/student/requests/" + request.getId())
                        .with(user("student1").roles("STUDENT")))
                .andExpect(redirectedUrl("/student/dashboard"))
                .andExpect(flash().attribute("errorMessage", "You can only manage your own requests."));

        TutoringRequest stored = requestRepository.findById(request.getId()).orElseThrow();
        assertEquals("OPEN", stored.getStatus());
        assertEquals(100, userRepository.findByUsername("student1").getTokenBalance());
    }

    @Test
    void ownerCanCancelAnOpenRequestAndCannotCancelAClaimedOne() throws Exception {
        User student = userRepository.findByUsername("student1");
        TutoringRequest open = requestRepository.save(newRequest(student, "HCDD 412", "Cancel me"));
        open.setBountyValue(15);
        open = requestRepository.save(open);
        student.setTokenBalance(85);
        userRepository.save(student);

        mockMvc.perform(post("/student/requests/" + open.getId() + "/cancel")
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf()))
                .andExpect(redirectedUrl("/student/dashboard"))
                .andExpect(flash().attributeExists("successMessage"));

        assertEquals("CANCELED", requestRepository.findById(open.getId()).orElseThrow().getStatus());
        assertEquals(100, userRepository.findByUsername("student1").getTokenBalance());

        TutoringRequest claimed = requestRepository.save(newRequest(student, "HCDD 412", "Keep claimed"));
        claimed.setStatus("CLAIMED");
        claimed.setAssignedTutorId(userRepository.findByUsername("tutor1").getId());
        claimed = requestRepository.save(claimed);

        mockMvc.perform(post("/student/requests/" + claimed.getId() + "/cancel")
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf()))
                .andExpect(redirectedUrl("/student/dashboard"))
                .andExpect(flash().attribute("errorMessage", "Only open requests can be canceled."));

        assertEquals("CLAIMED", requestRepository.findById(claimed.getId()).orElseThrow().getStatus());
    }

    @Test
    void onlyOneReviewIsStoredForAResolvedRequest() throws Exception {
        User student = userRepository.findByUsername("student1");
        User tutor = userRepository.findByUsername("tutor1");
        TutoringRequest request = requestRepository.save(newRequest(student, "HCDD 412", "Reviewed once"));
        request.setStatus("RESOLVED");
        request.setAssignedTutorId(tutor.getId());
        request = requestRepository.save(request);

        mockMvc.perform(post("/student/reviews")
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf())
                        .param("requestId", request.getId().toString())
                        .param("score", "4")
                        .param("feedback", "Clear explanation")
                        .param("tutorId", "999")
                        .param("studentId", "999"))
                .andExpect(redirectedUrl("/student/dashboard"));

        mockMvc.perform(post("/student/reviews")
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf())
                        .param("requestId", request.getId().toString())
                        .param("score", "1"))
                .andExpect(flash().attribute("errorMessage", "You already reviewed this session."));

        assertEquals(1, reviewRepository.count());
        assertEquals(tutor.getId(), reviewRepository.findByRequestId(request.getId()).orElseThrow().getTutorId());
        assertEquals(student.getId(), reviewRepository.findByRequestId(request.getId()).orElseThrow().getStudentId());
        assertEquals(4, reviewRepository.findByRequestId(request.getId()).orElseThrow().getScore());
    }

    @Test
    void tutorBoardHidesOtherSubjectsAndClaimIsRejected() throws Exception {
        User student = userRepository.findByUsername("student1");
        requestRepository.save(newRequest(student, "HCDD 412", "Visible topic beta"));
        TutoringRequest hidden = requestRepository.save(newRequest(student, "IST 311", "Hidden topic alpha"));

        mockMvc.perform(get("/tutor/board").with(user("tutor1").roles("TUTOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Visible topic beta")))
                .andExpect(content().string(not(containsString("Hidden topic alpha"))))
                .andExpect(content().string(containsString("HCDD 412, IST 440W")));

        mockMvc.perform(post("/tutor/claim/" + hidden.getId())
                        .with(user("tutor1").roles("TUTOR"))
                        .with(csrf()))
                .andExpect(redirectedUrl("/tutor/board"))
                .andExpect(flash().attribute("errorMessage", "This request is outside your subjects."));

        TutoringRequest stored = requestRepository.findById(hidden.getId()).orElseThrow();
        assertEquals("OPEN", stored.getStatus());
        assertNull(stored.getAssignedTutorId());
    }

    @Test
    void adminCanOpenOverviewAndInsights() throws Exception {
        mockMvc.perform(get("/admin").with(user("student1").roles("STUDENT")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("student1")))
                .andExpect(content().string(not(containsString("{bcrypt}"))));

        mockMvc.perform(get("/admin/insights").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Learning insights")));
    }

    private void setBalance(String username, int balance) {
        User user = userRepository.findByUsername(username);
        user.setTokenBalance(balance);
        userRepository.save(user);
    }

    private User saveStudent(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("password"));
        user.setRole("STUDENT");
        user.setTokenBalance(100);
        return userRepository.save(user);
    }

    private TutoringRequest newRequest(User student, String course, String topic) {
        TutoringRequest request = new TutoringRequest();
        request.setStudentId(student.getId());
        request.setCourse(course);
        request.setTopic(topic);
        request.setShortDescription("Need help with " + topic);
        request.setBountyValue(0);
        request.setPreferredMeetingMethod("Zoom");
        request.setExpectedEffort("Quick explanation (10-15 mins)");
        request.setDesiredResponseTime("Today");
        return request;
    }
}
