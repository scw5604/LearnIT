package com.rapidtutor;

import com.rapidtutor.model.TutoringRequest;
import com.rapidtutor.model.User;
import com.rapidtutor.repository.MeetingOfferRepository;
import com.rapidtutor.repository.NotificationRepository;
import com.rapidtutor.repository.RequestEventRepository;
import com.rapidtutor.repository.ReviewRepository;
import com.rapidtutor.repository.TutoringRequestRepository;
import com.rapidtutor.repository.UserRepository;
import com.rapidtutor.service.EconomyService;
import com.rapidtutor.service.RequestExpiration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
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

import org.springframework.test.web.servlet.MvcResult;

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
    private MeetingOfferRepository offerRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private RequestEventRepository eventRepository;

    @Autowired
    private RequestExpiration requestExpiration;

    @Autowired
    private EconomyService economyService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void resetRows() {
        reviewRepository.deleteAll();
        notificationRepository.deleteAll();
        eventRepository.deleteAll();
        offerRepository.deleteAll();
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
                        .param("displayName", "New Student")
                        .param("email", "newstudent@example.com")
                        .param("username", "newstudent")
                        .param("password", "secret-pass")
                        .param("confirmPassword", "secret-pass")
                        .param("role", "ADMIN")
                        .param("tokenBalance", "9999")
                        .param("subjects", "HCDD 412"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"recovery-code\"")));

        User created = userRepository.findByUsername("newstudent");
        assertTrue(created.getPassword().startsWith("{bcrypt}"));
        assertTrue(passwordEncoder.matches("secret-pass", created.getPassword()));
        assertFalse(created.getPassword().contains("secret-pass"));
        assertEquals("STUDENT", created.getRole());
        assertEquals(100, created.getTokenBalance());
        assertNull(created.getSubjects());
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
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bounty must be between 0 and 50 tokens.")))
                .andExpect(content().string(containsString("Loops")));

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
                .andExpect(flash().attributeExists("successMessage"));

        assertEquals("CANCELED", requestRepository.findById(claimed.getId()).orElseThrow().getStatus());
        assertEquals(100, userRepository.findByUsername("student1").getTokenBalance());
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
    void tutorBoardHidesOtherSubjectsAndOfferIsRejected() throws Exception {
        User student = userRepository.findByUsername("student1");
        requestRepository.save(newRequest(student, "HCDD 412", "Visible topic beta"));
        TutoringRequest hidden = requestRepository.save(newRequest(student, "HIST 100", "Hidden topic alpha"));

        mockMvc.perform(get("/tutor/board").with(user("tutor1").roles("TUTOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Visible topic beta")))
                .andExpect(content().string(not(containsString("Hidden topic alpha"))))
                .andExpect(content().string(containsString("HCDD 412, IST 440W, IST 311, HCDD 411, HCDD 331, IST 230")));

        mockMvc.perform(post("/tutor/requests/" + hidden.getId() + "/offers")
                        .with(user("tutor1").roles("TUTOR"))
                        .with(csrf())
                        .param("from", "board")
                        .param("offeredTime", "2026-10-15T16:00"))
                .andExpect(redirectedUrl("/tutor/board"))
                .andExpect(flash().attribute("errorMessage", "This request is outside your subjects."));

        TutoringRequest stored = requestRepository.findById(hidden.getId()).orElseThrow();
        assertEquals("OPEN", stored.getStatus());
        assertNull(stored.getAssignedTutorId());
        assertEquals(0, offerRepository.count());
    }

    @Test
    void offerStaysOpenUntilTheStudentAcceptsIt() throws Exception {
        User student = userRepository.findByUsername("student1");
        saveStudent("student2");
        TutoringRequest request = requestRepository.save(newRequest(student, "HCDD 412", "Offer me"));

        mockMvc.perform(post("/tutor/requests/" + request.getId() + "/offers")
                        .with(user("tutor1").roles("TUTOR"))
                        .with(csrf())
                        .param("offeredTime", "2027-06-01T09:00")
                        .param("from", "board"))
                .andExpect(redirectedUrl("/tutor/board"))
                .andExpect(flash().attributeExists("successMessage"));

        mockMvc.perform(post("/tutor/requests/" + request.getId() + "/offers")
                        .with(user("tutor1").roles("TUTOR"))
                        .with(csrf())
                        .param("offeredTime", "2027-06-02T15:30"))
                .andExpect(redirectedUrl("/tutor/requests/" + request.getId()));

        TutoringRequest stillOpen = requestRepository.findById(request.getId()).orElseThrow();
        assertEquals("OPEN", stillOpen.getStatus());
        assertNull(stillOpen.getAssignedTutorId());
        assertEquals(1, offerRepository.count());
        var offer = offerRepository.findAll().get(0);
        assertEquals(LocalDateTime.of(2027, 6, 2, 15, 30), offer.getOfferedTime());

        mockMvc.perform(get("/tutor/board").with(user("tutor1").roles("TUTOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Offer me")))
                .andExpect(content().string(containsString("Jun 2, 3:30 PM")));

        mockMvc.perform(post("/student/requests/" + request.getId() + "/offers/" + offer.getId() + "/accept")
                        .with(user("student2").roles("STUDENT"))
                        .with(csrf()))
                .andExpect(flash().attribute("errorMessage", "You can only manage your own requests."));
        assertEquals("OPEN", requestRepository.findById(request.getId()).orElseThrow().getStatus());

        mockMvc.perform(post("/student/requests/" + request.getId() + "/offers/" + offer.getId() + "/accept")
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf()))
                .andExpect(redirectedUrl("/student/requests/" + request.getId()))
                .andExpect(flash().attributeExists("successMessage"));

        TutoringRequest claimed = requestRepository.findById(request.getId()).orElseThrow();
        assertEquals("CLAIMED", claimed.getStatus());
        assertEquals(userRepository.findByUsername("tutor1").getId(), claimed.getAssignedTutorId());
        assertEquals(LocalDateTime.of(2027, 6, 2, 15, 30), claimed.getOfferedMeetingTime());
        assertEquals(0, offerRepository.count());

        mockMvc.perform(get("/student/requests/" + request.getId()).with(user("student1").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Claimed by")))
                .andExpect(content().string(containsString("tutor1")))
                .andExpect(content().string(containsString("Zoom")))
                .andExpect(content().string(containsString("Jun 2, 3:30 PM")));
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

    @Test
    void duplicateEmailIsRejectedAndRecoveryCodeResetsPassword() throws Exception {
        MvcResult created = mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("displayName", "Ada Student")
                        .param("email", "ada@example.com")
                        .param("username", "ada")
                        .param("password", "first-password")
                        .param("confirmPassword", "first-password"))
                .andExpect(status().isOk())
                .andReturn();
        String body = created.getResponse().getContentAsString();
        int start = body.indexOf("id=\"recovery-code\">") + "id=\"recovery-code\">".length();
        String code = body.substring(start, body.indexOf('<', start));

        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("displayName", "Ada Two")
                        .param("email", "ada@example.com")
                        .param("username", "ada2")
                        .param("password", "second-password")
                        .param("confirmPassword", "second-password"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("That email is already registered.")))
                .andExpect(content().string(containsString("ada2")));

        mockMvc.perform(post("/forgot-password")
                        .with(csrf())
                        .param("email", "ada@example.com")
                        .param("recoveryCode", code)
                        .param("password", "reset-password")
                        .param("confirmPassword", "reset-password"))
                .andExpect(redirectedUrl("/login?reset=true"));

        assertTrue(passwordEncoder.matches("reset-password", userRepository.findByUsername("ada").getPassword()));
    }

    @Test
    void applicantCannotOpenTheTutorBoardUntilApproved() throws Exception {
        mockMvc.perform(post("/apply/tutor")
                        .with(csrf())
                        .param("displayName", "New Tutor")
                        .param("email", "tutorapplicant@example.com")
                        .param("username", "applicant1")
                        .param("password", "tutor-password")
                        .param("confirmPassword", "tutor-password")
                        .param("courses", "HCDD 412")
                        .param("courses", "IST 311"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"recovery-code\"")));

        User applicant = userRepository.findByUsername("applicant1");
        assertEquals("APPLICANT", applicant.getRole());
        assertEquals(0, applicant.getTokenBalance());

        mockMvc.perform(get("/tutor/board").with(user("applicant1").roles("APPLICANT")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/applicants/" + applicant.getId() + "/approve")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(redirectedUrl("/admin"));

        User approved = userRepository.findByUsername("applicant1");
        assertEquals("TUTOR", approved.getRole());
        assertEquals("HCDD 412, IST 311", approved.getSubjects());
    }

    @Test
    void postedRequestIgnoresStatusAndForeignIds() throws Exception {
        mockMvc.perform(post("/student/requests")
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf())
                        .param("course", "HCDD 412")
                        .param("topic", "Binding check")
                        .param("shortDescription", "I am stuck on the concept.")
                        .param("preferredMeetingMethod", "Zoom")
                        .param("desiredResponseTime", "Not urgent")
                        .param("expectedEffort", "Quick explanation (10-15 mins)")
                        .param("bountyValue", "0")
                        .param("status", "CLAIMED")
                        .param("studentId", "999")
                        .param("assignedTutorId", "999"))
                .andExpect(redirectedUrl("/student/dashboard"));

        TutoringRequest stored = requestRepository.findAll().get(0);
        assertEquals("OPEN", stored.getStatus());
        assertEquals(userRepository.findByUsername("student1").getId(), stored.getStudentId());
        assertNull(stored.getAssignedTutorId());
    }

    @Test
    void pastMeetingTimeIsRejectedOnTheOfferForm() throws Exception {
        User student = userRepository.findByUsername("student1");
        TutoringRequest request = requestRepository.save(newRequest(student, "HCDD 412", "Future only"));

        mockMvc.perform(post("/tutor/requests/" + request.getId() + "/offers")
                        .with(user("tutor1").roles("TUTOR"))
                        .with(csrf())
                        .param("from", "details")
                        .param("offeredTime", "2020-01-01T09:00"))
                .andExpect(redirectedUrl("/tutor/requests/" + request.getId()))
                .andExpect(flash().attribute("timeError", true))
                .andExpect(flash().attribute("errorMessage", "Choose a meeting time in the future."));

        assertEquals(0, offerRepository.count());
    }

    @Test
    void withdrawingAnOfferNotifiesTheStudent() throws Exception {
        User student = userRepository.findByUsername("student1");
        TutoringRequest request = requestRepository.save(newRequest(student, "HCDD 412", "Withdraw me"));

        mockMvc.perform(post("/tutor/requests/" + request.getId() + "/offers")
                        .with(user("tutor1").roles("TUTOR"))
                        .with(csrf())
                        .param("from", "board")
                        .param("offeredTime", "2027-06-03T10:00"))
                .andExpect(flash().attributeExists("successMessage"));

        var offer = offerRepository.findAll().get(0);
        mockMvc.perform(post("/tutor/requests/" + request.getId() + "/offers/" + offer.getId() + "/withdraw")
                        .with(user("tutor1").roles("TUTOR"))
                        .with(csrf()))
                .andExpect(redirectedUrl("/tutor/board"));

        assertEquals(0, offerRepository.count());
        assertTrue(notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(student.getId()).stream()
                .anyMatch(note -> note.getMessage().contains("withdrew")));
    }

    @Test
    void cancelingAClaimedSessionRefundsAndNotifiesTheTutor() throws Exception {
        User student = userRepository.findByUsername("student1");
        User tutor = userRepository.findByUsername("tutor1");
        TutoringRequest request = requestRepository.save(newRequest(student, "HCDD 412", "Cancel claimed"));
        request.setStatus("CLAIMED");
        request.setAssignedTutorId(tutor.getId());
        request.setBountyValue(20);
        requestRepository.save(request);
        setBalance("student1", 80);

        mockMvc.perform(post("/student/requests/" + request.getId() + "/cancel")
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf()))
                .andExpect(redirectedUrl("/student/dashboard"))
                .andExpect(flash().attributeExists("successMessage"));

        assertEquals("CANCELED", requestRepository.findById(request.getId()).orElseThrow().getStatus());
        assertEquals(100, userRepository.findByUsername("student1").getTokenBalance());
        assertTrue(notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(tutor.getId()).stream()
                .anyMatch(note -> note.getMessage().contains("canceled")));
    }

    @Test
    void acceptingAnOfferNotifiesTheTutor() throws Exception {
        User student = userRepository.findByUsername("student1");
        User tutor = userRepository.findByUsername("tutor1");
        TutoringRequest request = requestRepository.save(newRequest(student, "HCDD 412", "Notify me"));

        mockMvc.perform(post("/tutor/requests/" + request.getId() + "/offers")
                        .with(user("tutor1").roles("TUTOR"))
                        .with(csrf())
                        .param("from", "board")
                        .param("offeredTime", "2027-06-04T11:00"));
        var offer = offerRepository.findAll().get(0);

        mockMvc.perform(post("/student/requests/" + request.getId() + "/offers/" + offer.getId() + "/accept")
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf()))
                .andExpect(flash().attributeExists("successMessage"));

        assertTrue(notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(tutor.getId()).stream()
                .anyMatch(note -> note.getMessage().contains("accepted")));
    }

    @Test
    void expiredRequestRefundsTheBounty() {
        User student = userRepository.findByUsername("student1");
        TutoringRequest request = requestRepository.save(newRequest(student, "HCDD 412", "Too late"));
        request.setDesiredResponseTime("ASAP");
        request.setCreationTimestamp(LocalDateTime.now().minusHours(2));
        request.setBountyValue(10);
        requestRepository.save(request);
        setBalance("student1", 90);

        requestExpiration.expireDue();

        TutoringRequest stored = requestRepository.findById(request.getId()).orElseThrow();
        assertEquals("EXPIRED", stored.getStatus());
        assertNull(stored.getCanceledTimestamp());
        assertEquals(100, userRepository.findByUsername("student1").getTokenBalance());
        assertTrue(eventRepository.findByRequestIdOrderByAtAsc(request.getId()).stream()
                .anyMatch(event -> "EXPIRED".equals(event.getToStatus())));
    }

    @Test
    void insightsListWrittenFeedback() throws Exception {
        User student = userRepository.findByUsername("student1");
        User tutor = userRepository.findByUsername("tutor1");
        TutoringRequest request = requestRepository.save(newRequest(student, "HCDD 412", "Insight review"));
        request.setStatus("RESOLVED");
        request.setAssignedTutorId(tutor.getId());
        request = requestRepository.save(request);

        mockMvc.perform(post("/student/reviews")
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf())
                        .param("requestId", request.getId().toString())
                        .param("score", "5")
                        .param("feedback", "Patient with the concept"))
                .andExpect(redirectedUrl("/student/dashboard"));

        mockMvc.perform(get("/admin/insights").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Patient with the concept")))
                .andExpect(content().string(containsString("Score counts")));

        mockMvc.perform(get("/tutor/board").with(user("tutor1").roles("TUTOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Patient with the concept")));
    }

    @Test
    void h2ConsoleIsLimitedToAdmins() throws Exception {
        mockMvc.perform(get("/h2-console").with(user("student1").roles("STUDENT")))
                .andExpect(status().isForbidden());
        MvcResult adminConsole = mockMvc.perform(get("/h2-console").with(user("admin").roles("ADMIN")))
                .andReturn();
        assertTrue(adminConsole.getResponse().getStatus() != 403);
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
