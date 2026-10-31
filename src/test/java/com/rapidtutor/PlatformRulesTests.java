package com.rapidtutor;

import com.rapidtutor.model.MeetingOffer;
import com.rapidtutor.model.TutoringRequest;
import com.rapidtutor.model.User;
import com.rapidtutor.repository.DuckDraftRepository;
import com.rapidtutor.repository.KnowledgeArticleRepository;
import com.rapidtutor.repository.MeetingOfferRepository;
import com.rapidtutor.repository.NotificationRepository;
import com.rapidtutor.repository.RequestEventRepository;
import com.rapidtutor.repository.ReviewRepository;
import com.rapidtutor.repository.TutoringRequestRepository;
import com.rapidtutor.repository.UserRepository;
import com.rapidtutor.config.H2AlreadyRunningAnalyzer;
import com.rapidtutor.config.LegacyColumnWiden;
import com.rapidtutor.model.DuckDraft;
import com.rapidtutor.model.Notification;
import com.rapidtutor.model.Review;
import com.rapidtutor.service.AiDiagnosisService;
import com.rapidtutor.service.EconomyService;
import com.rapidtutor.service.MeetingOfferService;
import com.rapidtutor.service.RequestExpiration;
import com.rapidtutor.service.SocraticNotes;
import jakarta.persistence.EntityManager;
import org.h2.jdbc.JdbcSQLNonTransientConnectionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:rapidtutor-rules;DB_CLOSE_DELAY=-1",
        "groq.api.key=mock-key",
        "rapidtutor.demo-users.enabled=false"
})
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
    private MeetingOfferService meetingOfferService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private KnowledgeArticleRepository knowledgeArticleRepository;

    @Autowired
    private DuckDraftRepository duckDraftRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LegacyColumnWiden legacyColumnWiden;

    @Autowired
    private AiDiagnosisService aiDiagnosisService;

    @BeforeEach
    void resetRows() {
        duckDraftRepository.deleteAll();
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
        knowledgeArticleRepository.deleteByKind("triage");
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
    void logoutEndsTheSession() throws Exception {
        MvcResult login = mockMvc.perform(formLogin("/login").user("student1").password("password"))
                .andExpect(redirectedUrl("/student/dashboard"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        mockMvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(redirectedUrl("/login?logout=true"));
        mockMvc.perform(get("/login").param("logout", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("You have been signed out.")));

        MvcResult dashboard = mockMvc.perform(get("/student/dashboard").session(session))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertTrue(dashboard.getResponse().getRedirectedUrl().contains("/login"));
    }

    @Test
    void anInvalidEmailIsRejected() throws Exception {
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("displayName", "New Student")
                        .param("email", "not-an-email")
                        .param("username", "validuser")
                        .param("password", "secret-pass")
                        .param("confirmPassword", "secret-pass"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Enter a valid email address.")));
        assertNull(userRepository.findByUsername("validuser"));
    }

    @Test
    void aUsernameOfTheWrongShapeIsRejected() throws Exception {
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("displayName", "New Student")
                        .param("email", "shortname@example.com")
                        .param("username", "ab")
                        .param("password", "secret-pass")
                        .param("confirmPassword", "secret-pass"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Username must be 3 to 40 letters, numbers, dots, hyphens, or underscores.")));
        assertNull(userRepository.findByUsername("ab"));
    }

    @Test
    void aBlankNameIsRejected() throws Exception {
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("displayName", "   ")
                        .param("email", "blankname@example.com")
                        .param("username", "blankname")
                        .param("password", "secret-pass")
                        .param("confirmPassword", "secret-pass"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Enter your name.")));
        assertNull(userRepository.findByUsername("blankname"));
    }

    @Test
    void aTutorApplicationWithNoCourseIsRejected() throws Exception {
        mockMvc.perform(post("/apply/tutor")
                        .with(csrf())
                        .param("displayName", "New Tutor")
                        .param("email", "newtutor@example.com")
                        .param("username", "newtutor")
                        .param("password", "secret-pass")
                        .param("confirmPassword", "secret-pass"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Choose at least one course.")));
        assertNull(userRepository.findByUsername("newtutor"));
    }

    @Test
    void aTutorApplicationForAnUnlistedCourseIsRejected() throws Exception {
        mockMvc.perform(post("/apply/tutor")
                        .with(csrf())
                        .param("displayName", "New Tutor")
                        .param("email", "unlisted@example.com")
                        .param("username", "unlistedtutor")
                        .param("password", "secret-pass")
                        .param("confirmPassword", "secret-pass")
                        .param("courses", "HIST 100"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Choose at least one course.")));
        assertNull(userRepository.findByUsername("unlistedtutor"));
    }

    @Test
    void aNameLongerThanEightyCharactersIsRejected() throws Exception {
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("displayName", "n".repeat(81))
                        .param("email", "longname@example.com")
                        .param("username", "longname")
                        .param("password", "secret-pass")
                        .param("confirmPassword", "secret-pass"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Keep that entry under 80 characters.")));
        assertNull(userRepository.findByUsername("longname"));
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
    void aBountyOfZeroOrFiftyIsAccepted() {
        User student = userRepository.findByUsername("student1");
        TutoringRequest free = economyService.createRequestWithBounty(
                "student1", newRequest(student, "HCDD 412", "No bounty"), 0);
        assertEquals("OPEN", free.getStatus());
        assertEquals(0, free.getBountyValue());
        assertEquals(100, userRepository.findByUsername("student1").getTokenBalance());

        TutoringRequest full = economyService.createRequestWithBounty(
                "student1", newRequest(student, "HCDD 412", "Full bounty"), 50);
        assertEquals("OPEN", full.getStatus());
        assertEquals(50, full.getBountyValue());
        assertEquals(50, userRepository.findByUsername("student1").getTokenBalance());
    }

    @Test
    void httpRejectsANegativeBounty() throws Exception {
        MockHttpSession session = duckSession("Loops", "I am stuck on the loop condition.");

        mockMvc.perform(post("/student/requests")
                        .session(session)
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf())
                        .param("studentExplanation", "I tried the condition.")
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
        assertEquals(110, userRepository.findByUsername("student1").getTokenBalance());
    }

    @Test
    void descriptionOver2000CharactersIsRejected() throws Exception {
        mockMvc.perform(post("/student/requests/duck")
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf())
                        .param("course", "HCDD 412")
                        .param("topic", "Loops")
                        .param("shortDescription", "a".repeat(2001))
                        .param("preferredMeetingMethod", "Zoom")
                        .param("desiredResponseTime", "Not urgent")
                        .param("expectedEffort", "Quick explanation (10-15 mins)")
                        .param("bountyValue", "0"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Keep that entry under 2000 characters.")))
                .andExpect(content().string(containsString("maxlength=\"2000\"")));
        assertEquals(0, requestRepository.count());
    }

    @Test
    void feedbackOver1000CharactersIsRejected() throws Exception {
        User student = userRepository.findByUsername("student1");
        User tutor = userRepository.findByUsername("tutor1");
        TutoringRequest request = requestRepository.save(newRequest(student, "HCDD 412", "Long review"));
        request.setStatus("RESOLVED");
        request.setAssignedTutorId(tutor.getId());
        request = requestRepository.save(request);

        mockMvc.perform(post("/student/reviews")
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf())
                        .param("requestId", request.getId().toString())
                        .param("score", "4")
                        .param("feedback", "b".repeat(1001)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Keep feedback under 1000 characters.")))
                .andExpect(content().string(containsString("maxlength=\"1000\"")));
        assertEquals(0, reviewRepository.count());
        assertEquals(100, userRepository.findByUsername("student1").getTokenBalance());
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
    void theTutorBoardShowsTheAverageScore() throws Exception {
        User student = userRepository.findByUsername("student1");
        User tutor = userRepository.findByUsername("tutor1");
        TutoringRequest first = requestRepository.save(newRequest(student, "HCDD 412", "First score"));
        TutoringRequest second = requestRepository.save(newRequest(student, "HCDD 412", "Second score"));
        reviewRepository.save(review(first.getId(), tutor.getId(), student.getId(), 4));
        reviewRepository.save(review(second.getId(), tutor.getId(), student.getId(), 2));

        mockMvc.perform(get("/tutor/board").with(user("tutor1").roles("TUTOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Your average rating is")))
                .andExpect(content().string(containsString("3.0")));
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
                .andExpect(content().string(containsString("Time offered")))
                .andExpect(content().string(containsString("Offer me")))
                .andExpect(content().string(containsString("Jun 2, 3:30 PM")));

        mockMvc.perform(get("/student/requests").with(user("student1").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Time offers")))
                .andExpect(content().string(containsString("Offer me")));

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
    void duplicateUsernameIsRejected() throws Exception {
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("displayName", "Ada Student")
                        .param("email", "ada@example.com")
                        .param("username", "ada")
                        .param("password", "first-password")
                        .param("confirmPassword", "first-password"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"recovery-code\"")));

        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("displayName", "Ada Two")
                        .param("email", "ada-two@example.com")
                        .param("username", "ada")
                        .param("password", "second-password")
                        .param("confirmPassword", "second-password"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("That username is already taken.")));

        assertEquals(1, userRepository.findAll().stream().filter(user -> "ada".equals(user.getUsername())).count());
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
    void approvedApplicantMustSignInAgainForTheBoard() throws Exception {
        mockMvc.perform(post("/apply/tutor")
                        .with(csrf())
                        .param("displayName", "Later Tutor")
                        .param("email", "latertutor@example.com")
                        .param("username", "applicant2")
                        .param("password", "tutor-password")
                        .param("confirmPassword", "tutor-password")
                        .param("courses", "HCDD 412"))
                .andExpect(status().isOk());

        MvcResult login = mockMvc.perform(post("/login")
                        .param("username", "applicant2")
                        .param("password", "tutor-password")
                        .with(csrf()))
                .andExpect(redirectedUrl("/apply/status"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        User applicant = userRepository.findByUsername("applicant2");
        mockMvc.perform(post("/admin/applicants/" + applicant.getId() + "/approve")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(redirectedUrl("/admin"));

        mockMvc.perform(get("/apply/status").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sign in again to open the board.")))
                .andExpect(content().string(not(containsString("Open the tutor board"))));

        mockMvc.perform(get("/tutor/board").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void postingWithoutTheDuckDoesNotCreateARequest() throws Exception {
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
                        .param("studentExplanation", "I tried the first example."))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Talk it through before posting.")));

        assertEquals(0, requestRepository.count());
        assertEquals(100, userRepository.findByUsername("student1").getTokenBalance());
    }

    @Test
    void duckPassStoresTheNoteAndIgnoresForeignIds() throws Exception {
        MockHttpSession session = duckSession("Binding check", "I am stuck on the concept.");
        assertEquals(0, requestRepository.count());

        mockMvc.perform(post("/student/requests")
                        .session(session)
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf())
                        .param("studentExplanation", "   "))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Explain what you have already tried.")));
        assertEquals(0, requestRepository.count());

        mockMvc.perform(post("/student/requests")
                        .session(session)
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf())
                        .param("studentExplanation", "I traced the condition and it never changes.")
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
        assertEquals("I traced the condition and it never changes.", stored.getStudentExplanation());
        assertEquals(SocraticNotes.offline("Binding check"), stored.getAiDiagnosticNote());
        assertEquals(100, userRepository.findByUsername("student1").getTokenBalance());
        var draft = duckDraftRepository.findByStudentIdOrderByCreatedAtDesc(stored.getStudentId()).get(0);
        assertEquals("ESCALATED", draft.getOutcome());
        assertEquals(stored.getId(), draft.getRequestId());
    }

    @Test
    void figuringItOutSavesATriageNoteWithoutARequest() throws Exception {
        MockHttpSession session = duckSession("Solved alone", "I am stuck on the concept.");

        mockMvc.perform(post("/student/requests/duck/solved")
                        .session(session)
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf())
                        .param("studentExplanation", "The loop variable was never updated."))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Write what you understand now.")));
        assertEquals(0, knowledgeArticleRepository
                .findByCourseIgnoreCaseAndTopicContainingIgnoreCase("HCDD 412", "Solved alone").size());

        mockMvc.perform(post("/student/requests/duck/solved")
                        .session(session)
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf())
                        .param("studentExplanation", "The loop variable was never updated.")
                        .param("takeaway", "Setters can refuse bad values."))
                .andExpect(redirectedUrl("/student/dashboard"))
                .andExpect(flash().attributeExists("successMessage"));

        assertEquals(0, requestRepository.count());
        var drafts = duckDraftRepository.findByStudentIdOrderByCreatedAtDesc(
                userRepository.findByUsername("student1").getId());
        assertEquals(1, drafts.size());
        assertEquals("SOLVED", drafts.get(0).getOutcome());
        assertEquals("Setters can refuse bad values.", drafts.get(0).getTakeaway());
        assertEquals(100, userRepository.findByUsername("student1").getTokenBalance());
        var articles = knowledgeArticleRepository
                .findByCourseIgnoreCaseAndTopicContainingIgnoreCase("HCDD 412", "Solved alone");
        assertEquals(1, articles.size());
        assertEquals("triage", articles.get(0).getKind());
        assertEquals("Solved alone", articles.get(0).getProblemDescription());
        assertEquals(SocraticNotes.offline("Solved alone").trim(), articles.get(0).getVerifiedSolution());
        assertFalse(articles.get(0).getProblemDescription().contains("The loop variable was never updated."));
        assertFalse(articles.get(0).getVerifiedSolution().contains("The loop variable was never updated."));
        assertFalse(articles.get(0).getVerifiedSolution().contains("Setters can refuse bad values."));
        assertFalse(articles.get(0).getProblemDescription().contains("Setters can refuse bad values."));

        mockMvc.perform(get("/student/dashboard").with(user("student1").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("When I was stuck")))
                .andExpect(content().string(containsString("What changed")))
                .andExpect(content().string(containsString("Setters can refuse bad values.")))
                .andExpect(content().string(containsString("I am stuck on the concept.")));

        mockMvc.perform(get("/student/knowledge")
                        .param("topic", "Solved alone")
                        .with(user("student1").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Soquacktic")))
                .andExpect(content().string(containsString("Solved alone is the idea to clarify")))
                .andExpect(content().string(not(containsString("The loop variable was never updated."))))
                .andExpect(content().string(not(containsString("I am stuck on the concept."))));
    }

    @Test
    void walkingItThroughShowsAFollowUpWithoutARequest() throws Exception {
        MockHttpSession session = duckSession("Loop counters", "The loop never stops.");

        mockMvc.perform(post("/student/requests/duck/reply")
                        .session(session)
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf())
                        .param("studentExplanation", "I forgot to increment the counter."))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Follow-up")))
                .andExpect(content().string(containsString("Answer the question above, then walk it through.")))
                .andExpect(content().string(containsString("I forgot to increment the counter.")))
                .andExpect(content().string(containsString("Loop counters is the idea to clarify")))
                .andExpect(content().string(containsString("I'm still stuck")))
                .andExpect(content().string(containsString("I figured it out")));
        assertEquals(0, requestRepository.count());

        mockMvc.perform(post("/student/requests/duck/reply")
                        .session(session)
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf())
                        .param("studentExplanation", "The condition checks the wrong variable."))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Soquacktic already replied once. Start over for a new note.")))
                .andExpect(content().string(containsString("I forgot to increment the counter.")));
        assertEquals(0, requestRepository.count());

        mockMvc.perform(post("/student/requests/duck/solved")
                        .session(session)
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf())
                        .param("studentExplanation", "The condition checks the wrong variable.")
                        .param("takeaway", "The counter has to change."))
                .andExpect(redirectedUrl("/student/dashboard"));

        assertEquals(0, requestRepository.count());
        var articles = knowledgeArticleRepository
                .findByCourseIgnoreCaseAndTopicContainingIgnoreCase("HCDD 412", "Loop counters");
        assertEquals(1, articles.size());
        assertTrue(articles.get(0).getVerifiedSolution().contains("I forgot to increment the counter."));
        assertFalse(articles.get(0).getVerifiedSolution().contains("The condition checks the wrong variable."));
        assertFalse(articles.get(0).getVerifiedSolution().contains("is the idea to clarify"));
    }

    @Test
    void stillStuckAfterAReplyStoresTheFollowUp() throws Exception {
        MockHttpSession session = duckSession("Loop counters", "The loop never stops.");

        mockMvc.perform(post("/student/requests/duck/reply")
                        .session(session)
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf())
                        .param("studentExplanation", "I forgot to increment the counter."))
                .andExpect(status().isOk());

        mockMvc.perform(post("/student/requests")
                        .session(session)
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf())
                        .param("studentExplanation", "I forgot to increment the counter.")
                        .param("preferredMeetingMethod", "Zoom")
                        .param("desiredResponseTime", "Not urgent")
                        .param("expectedEffort", "Quick explanation (10-15 mins)")
                        .param("bountyValue", "0"))
                .andExpect(redirectedUrl("/student/dashboard"));

        TutoringRequest stored = requestRepository.findAll().get(0);
        assertEquals("I forgot to increment the counter.", stored.getStudentExplanation());
        assertTrue(stored.getAiDiagnosticNote().contains("I forgot to increment the counter."));
        assertFalse(stored.getAiDiagnosticNote().contains("is the idea to clarify"));
    }

    @Test
    void closingFromTheNoteRefundsAndSavesTheArticle() throws Exception {
        User student = userRepository.findByUsername("student1");
        TutoringRequest request = newRequest(student, "HCDD 412", "Triage topic");
        request.setBountyValue(10);
        request = economyService.createRequestWithBounty("student1", request, 10);
        request.setAiDiagnosticNote(SocraticNotes.offline("Triage topic"));
        request = requestRepository.save(request);
        assertEquals(90, userRepository.findByUsername("student1").getTokenBalance());

        mockMvc.perform(post("/tutor/requests/" + request.getId() + "/offers")
                        .with(user("tutor1").roles("TUTOR"))
                        .with(csrf())
                        .param("from", "board")
                        .param("offeredTime", "2027-06-05T11:00"))
                .andExpect(flash().attributeExists("successMessage"));
        assertEquals(1, offerRepository.count());

        mockMvc.perform(post("/student/requests/" + request.getId() + "/triage")
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf()))
                .andExpect(redirectedUrl("/student/requests/" + request.getId()))
                .andExpect(flash().attributeExists("successMessage"));

        TutoringRequest stored = requestRepository.findById(request.getId()).orElseThrow();
        assertEquals("RESOLVED", stored.getStatus());
        assertNull(stored.getAssignedTutorId());
        assertEquals(100, userRepository.findByUsername("student1").getTokenBalance());
        assertEquals(100, userRepository.findByUsername("tutor1").getTokenBalance());
        assertEquals(0, offerRepository.count());
        var articles = knowledgeArticleRepository
                .findByCourseIgnoreCaseAndTopicContainingIgnoreCase("HCDD 412", "Triage topic");
        assertEquals(1, articles.size());
        assertEquals("triage", articles.get(0).getKind());

        mockMvc.perform(get("/student/requests/" + request.getId()).with(user("student1").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Soquacktic")))
                .andExpect(content().string(not(containsString("Leave a review"))));

        mockMvc.perform(post("/student/requests/" + request.getId() + "/triage")
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf()))
                .andExpect(flash().attribute("errorMessage", "Only an open request can be closed from the note."));
        assertEquals(1, knowledgeArticleRepository
                .findByCourseIgnoreCaseAndTopicContainingIgnoreCase("HCDD 412", "Triage topic").size());
    }

    @Test
    void lessonScaffoldShowsTheBodyAfterTheLabel() throws Exception {
        User student = userRepository.findByUsername("student1");
        TutoringRequest request = requestRepository.save(newRequest(student, "HCDD 412", "Scaffold topic"));
        request.setAiDiagnosticNote("""
                Core concept:
                Addition combines two quantities.
                Diagnostic question:
                What does the plus sign tell you to do?
                Starting step:
                Rewrite the expression in words.
                """);
        request.setStudentExplanation("I added the numbers in the wrong order.");
        requestRepository.save(request);

        mockMvc.perform(get("/tutor/requests/" + request.getId()).with(user("tutor1").roles("TUTOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("AI lesson scaffold")))
                .andExpect(content().string(containsString("Addition combines two quantities.")))
                .andExpect(content().string(containsString("I added the numbers in the wrong order.")));

        mockMvc.perform(get("/student/requests/" + request.getId()).with(user("student1").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Soquacktic")))
                .andExpect(content().string(containsString("Addition combines two quantities.")));
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
        assertTrue(notificationRepository.findTop50ByRecipientUserIdOrderByCreatedAtDesc(student.getId()).stream()
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
        assertTrue(notificationRepository.findTop50ByRecipientUserIdOrderByCreatedAtDesc(tutor.getId()).stream()
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

        assertTrue(notificationRepository.findTop50ByRecipientUserIdOrderByCreatedAtDesc(tutor.getId()).stream()
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
    void aRequestDueWithinTwoHoursWaitsTwoHours() {
        setBalance("student1", 90);
        TutoringRequest request = openExpiringRequest("Within 2 hours", LocalDateTime.now().minusMinutes(90), 10);

        requestExpiration.expireDue();
        assertEquals("OPEN", requestRepository.findById(request.getId()).orElseThrow().getStatus());
        assertEquals(90, userRepository.findByUsername("student1").getTokenBalance());

        request.setCreationTimestamp(LocalDateTime.now().minusHours(3));
        requestRepository.save(request);
        requestExpiration.expireDue();
        assertEquals("EXPIRED", requestRepository.findById(request.getId()).orElseThrow().getStatus());
        assertEquals(100, userRepository.findByUsername("student1").getTokenBalance());
    }

    @Test
    void aRequestDueTodayExpiresAtTheNextMidnight() {
        setBalance("student1", 92);
        TutoringRequest request = openExpiringRequest("Today", LocalDateTime.now(), 8);

        requestExpiration.expireDue();
        assertEquals("OPEN", requestRepository.findById(request.getId()).orElseThrow().getStatus());
        assertEquals(92, userRepository.findByUsername("student1").getTokenBalance());

        request.setCreationTimestamp(LocalDateTime.now().minusDays(1));
        requestRepository.save(request);
        requestExpiration.expireDue();
        assertEquals("EXPIRED", requestRepository.findById(request.getId()).orElseThrow().getStatus());
        assertEquals(100, userRepository.findByUsername("student1").getTokenBalance());
    }

    @Test
    void aClaimedRequestPastItsDeadlineStaysClaimed() {
        User tutor = userRepository.findByUsername("tutor1");
        TutoringRequest request = openExpiringRequest("ASAP", LocalDateTime.now().minusHours(3), 10);
        request.setStatus("CLAIMED");
        request.setAssignedTutorId(tutor.getId());
        requestRepository.save(request);
        setBalance("student1", 90);

        requestExpiration.expireDue();

        TutoringRequest stored = requestRepository.findById(request.getId()).orElseThrow();
        assertEquals("CLAIMED", stored.getStatus());
        assertEquals(tutor.getId(), stored.getAssignedTutorId());
        assertEquals(90, userRepository.findByUsername("student1").getTokenBalance());
        assertEquals(0, eventRepository.findByRequestIdOrderByAtAsc(request.getId()).stream()
                .filter(event -> "EXPIRED".equals(event.getToStatus()))
                .count());
    }

    @Test
    void aResolvedRequestPastItsDeadlineStaysResolved() {
        User tutor = userRepository.findByUsername("tutor1");
        TutoringRequest request = openExpiringRequest("ASAP", LocalDateTime.now().minusHours(3), 10);
        request.setStatus("RESOLVED");
        request.setAssignedTutorId(tutor.getId());
        requestRepository.save(request);
        setBalance("student1", 90);

        requestExpiration.expireDue();

        assertEquals("RESOLVED", requestRepository.findById(request.getId()).orElseThrow().getStatus());
        assertEquals(90, userRepository.findByUsername("student1").getTokenBalance());
    }

    @Test
    void aRequestOutsideTheNamedWindowsStaysOpen() {
        TutoringRequest request = openExpiringRequest("This week", LocalDateTime.now().minusDays(2), 10);
        setBalance("student1", 90);

        requestExpiration.expireDue();

        assertEquals("OPEN", requestRepository.findById(request.getId()).orElseThrow().getStatus());
        assertEquals(90, userRepository.findByUsername("student1").getTokenBalance());
    }

    @Test
    void resolvingAClaimedRequestPaysTheTutorOnce() throws Exception {
        User student = userRepository.findByUsername("student1");
        User tutor = userRepository.findByUsername("tutor1");
        TutoringRequest request = economyService.createRequestWithBounty(
                "student1", newRequest(student, "HCDD 412", "Pay the tutor"), 15);
        request.setStatus("CLAIMED");
        request.setAssignedTutorId(tutor.getId());
        request = requestRepository.save(request);

        mockMvc.perform(post("/student/requests/" + request.getId() + "/resolve")
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf()))
                .andExpect(redirectedUrl("/student/requests/" + request.getId() + "/review"));

        assertEquals("RESOLVED", requestRepository.findById(request.getId()).orElseThrow().getStatus());
        assertEquals(85, userRepository.findByUsername("student1").getTokenBalance());
        assertEquals(115, userRepository.findByUsername("tutor1").getTokenBalance());

        mockMvc.perform(post("/student/requests/" + request.getId() + "/resolve")
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf()))
                .andExpect(redirectedUrl("/student/dashboard"))
                .andExpect(flash().attribute("errorMessage", "Only claimed requests can be resolved."));
        assertEquals(85, userRepository.findByUsername("student1").getTokenBalance());
        assertEquals(115, userRepository.findByUsername("tutor1").getTokenBalance());
    }

    @Test
    void cancelingAResolvedRequestDoesNotRefund() throws Exception {
        User student = userRepository.findByUsername("student1");
        User tutor = userRepository.findByUsername("tutor1");
        TutoringRequest request = requestRepository.save(newRequest(student, "HCDD 412", "Already resolved"));
        request.setStatus("RESOLVED");
        request.setAssignedTutorId(tutor.getId());
        request.setBountyValue(10);
        request = requestRepository.save(request);
        setBalance("student1", 90);

        mockMvc.perform(post("/student/requests/" + request.getId() + "/cancel")
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf()))
                .andExpect(redirectedUrl("/student/dashboard"))
                .andExpect(flash().attribute("errorMessage", "Only an open or claimed session can be canceled."));
        assertEquals("RESOLVED", requestRepository.findById(request.getId()).orElseThrow().getStatus());
        assertEquals(90, userRepository.findByUsername("student1").getTokenBalance());
    }

    @Test
    void aSecondExpireDoesNotRefundAgain() {
        User student = userRepository.findByUsername("student1");
        TutoringRequest request = requestRepository.save(newRequest(student, "HCDD 412", "Expire twice"));
        request.setDesiredResponseTime("ASAP");
        request.setCreationTimestamp(LocalDateTime.now().minusHours(2));
        request.setBountyValue(10);
        request = requestRepository.save(request);
        setBalance("student1", 90);

        requestExpiration.expireDue();
        assertEquals("EXPIRED", requestRepository.findById(request.getId()).orElseThrow().getStatus());
        assertEquals(100, userRepository.findByUsername("student1").getTokenBalance());

        Long requestId = request.getId();
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> economyService.expireAndRefund(requestId));
        assertEquals("Only open requests can expire.", thrown.getMessage());
        assertEquals("EXPIRED", requestRepository.findById(requestId).orElseThrow().getStatus());
        assertEquals(100, userRepository.findByUsername("student1").getTokenBalance());
    }

    @Test
    void payingATutorWhoIsNotAssignedIsRejected() {
        User student = userRepository.findByUsername("student1");
        User tutor = userRepository.findByUsername("tutor1");
        User other = saveStudent("unassigned-tutor");
        other.setRole("TUTOR");
        other.setTokenBalance(40);
        other = userRepository.save(other);
        TutoringRequest request = requestRepository.save(newRequest(student, "HCDD 412", "Wrong tutor"));
        request.setStatus("CLAIMED");
        request.setAssignedTutorId(tutor.getId());
        request.setBountyValue(7);
        request = requestRepository.save(request);
        Long requestId = request.getId();
        Long otherId = other.getId();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> economyService.resolveAndPayout(requestId, otherId));
        assertEquals("This tutor is not assigned to the request.", thrown.getMessage());
        assertEquals("CLAIMED", requestRepository.findById(requestId).orElseThrow().getStatus());
        assertEquals(100, userRepository.findByUsername("tutor1").getTokenBalance());
        assertEquals(40, userRepository.findByUsername("unassigned-tutor").getTokenBalance());
    }

    @Test
    void aTutorCannotReadAnotherTutorsClaimedSession() throws Exception {
        User student = userRepository.findByUsername("student1");
        User other = saveStudent("assigned-tutor");
        other.setRole("TUTOR");
        other = userRepository.save(other);
        TutoringRequest request = requestRepository.save(newRequest(student, "HCDD 412", "Private claimed blocker"));
        request.setStatus("CLAIMED");
        request.setAssignedTutorId(other.getId());
        request.setShortDescription("Only the assigned tutor should read this.");
        request = requestRepository.save(request);

        MvcResult result = mockMvc.perform(get("/tutor/requests/" + request.getId()).with(user("tutor1").roles("TUTOR")))
                .andExpect(redirectedUrl("/tutor/board"))
                .andExpect(flash().attribute("errorMessage", "This request is outside your subjects."))
                .andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("Only the assigned tutor should read this."));
    }

    @Test
    void theNotificationListShowsOnlyTheRecipient() throws Exception {
        User other = saveStudent("note-owner");
        Notification note = new Notification();
        note.setRecipientUserId(other.getId());
        note.setMessage("Only student two was told about the offer");
        note.setLink("/student/dashboard");
        note.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(note);

        mockMvc.perform(get("/notifications").with(user("student1").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Only student two was told about the offer"))));
        mockMvc.perform(get("/notifications").with(user("note-owner").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Only student two was told about the offer")));
    }

    @Test
    void aMissingNotificationStaysOnTheList() throws Exception {
        mockMvc.perform(post("/notifications/" + Long.MAX_VALUE + "/read")
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf())
                        .param("follow", "true"))
                .andExpect(redirectedUrl("/notifications"));
        assertEquals(0, notificationRepository.count());
    }

    @Test
    void theRequestListShowsOnlyTheOwner() throws Exception {
        User other = saveStudent("list-owner");
        requestRepository.save(newRequest(other, "HCDD 412", "Only student two can see this topic"));

        mockMvc.perform(get("/student/requests").with(user("student1").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Only student two can see this topic"))));
        mockMvc.perform(get("/student/requests").with(user("list-owner").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Only student two can see this topic")));
    }

    @Test
    void theDashboardShowsOnlyThatStudentsDrafts() throws Exception {
        User other = saveStudent("draft-owner");
        TutoringRequest request = requestRepository.save(newRequest(other, "HCDD 412", "Private draft"));
        DuckDraft draft = DuckDraft.from(request, SocraticNotes.offline("Private draft"), other.getId());
        draft.setOutcome(DuckDraft.SOLVED);
        draft.setTakeaway("Only student two learned this");
        duckDraftRepository.save(draft);

        mockMvc.perform(get("/student/dashboard").with(user("student1").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Only student two learned this"))));
        mockMvc.perform(get("/student/dashboard").with(user("draft-owner").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Only student two learned this")));
    }

    @Test
    void anotherStudentCannotUseADuckSession() throws Exception {
        saveStudent("session-thief");
        MockHttpSession session = duckSession("Private duck", "Only the first student explained this.");
        DuckDraft original = duckDraftRepository.findByStudentIdOrderByCreatedAtDesc(
                userRepository.findByUsername("student1").getId()).get(0);

        mockMvc.perform(post("/student/requests")
                        .session(session)
                        .with(user("session-thief").roles("STUDENT"))
                        .with(csrf())
                        .param("course", "HCDD 412")
                        .param("topic", "Stolen topic")
                        .param("shortDescription", "Trying to reuse the session.")
                        .param("studentExplanation", "I tried the condition.")
                        .param("bountyValue", "0")
                        .param("preferredMeetingMethod", "Zoom")
                        .param("desiredResponseTime", "Today")
                        .param("expectedEffort", "Quick explanation (10-15 mins)"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Talk it through before posting.")))
                .andExpect(content().string(not(containsString("Only the first student explained this."))));

        assertEquals(0, requestRepository.count());
        assertEquals(100, userRepository.findByUsername("session-thief").getTokenBalance());
        DuckDraft stored = duckDraftRepository.findById(original.getId()).orElseThrow();
        assertEquals(DuckDraft.TRYING, stored.getOutcome());
        assertNull(stored.getRequestId());
        assertEquals(userRepository.findByUsername("student1").getId(), stored.getStudentId());
    }

    @Test
    void aTutorCannotCancelAnOpenRequest() throws Exception {
        User student = userRepository.findByUsername("student1");
        TutoringRequest request = economyService.createRequestWithBounty(
                "student1", newRequest(student, "HCDD 412", "Still open"), 12);

        mockMvc.perform(post("/tutor/requests/" + request.getId() + "/cancel")
                        .with(user("tutor1").roles("TUTOR"))
                        .with(csrf()))
                .andExpect(redirectedUrl("/tutor/board"))
                .andExpect(flash().attribute("errorMessage", "You can only cancel a session assigned to you."));
        assertEquals("OPEN", requestRepository.findById(request.getId()).orElseThrow().getStatus());
        assertEquals(88, userRepository.findByUsername("student1").getTokenBalance());
    }

    @Test
    void anotherTutorCannotWithdrawTheOffer() throws Exception {
        User student = userRepository.findByUsername("student1");
        User other = saveStudent("withdraw-tutor");
        other.setRole("TUTOR");
        other = userRepository.save(other);
        TutoringRequest request = requestRepository.save(newRequest(student, "HCDD 412", "Keep the offer"));
        MeetingOffer offer = saveOffer(request.getId(), userRepository.findByUsername("tutor1").getId(),
                LocalDateTime.of(2027, 6, 4, 11, 0));

        mockMvc.perform(post("/tutor/requests/" + request.getId() + "/offers/" + offer.getId() + "/withdraw")
                        .with(user("withdraw-tutor").roles("TUTOR"))
                        .with(csrf()))
                .andExpect(redirectedUrl("/tutor/board"))
                .andExpect(flash().attribute("errorMessage", "You can only withdraw your own offer."));
        assertEquals(1, offerRepository.count());
        assertFalse(notificationRepository.findTop50ByRecipientUserIdOrderByCreatedAtDesc(student.getId()).stream()
                .anyMatch(note -> note.getMessage().contains("withdrew")));
    }

    @Test
    void offerAndAcceptAreRejectedAfterTheDeadline() throws Exception {
        User student = userRepository.findByUsername("student1");
        User tutor = userRepository.findByUsername("tutor1");
        TutoringRequest request = requestRepository.save(newRequest(student, "HCDD 412", "Already late"));
        request.setDesiredResponseTime("ASAP");
        request.setCreationTimestamp(LocalDateTime.now().minusHours(2));
        request = requestRepository.save(request);

        mockMvc.perform(post("/tutor/requests/" + request.getId() + "/offers")
                        .with(user("tutor1").roles("TUTOR"))
                        .with(csrf())
                        .param("from", "board")
                        .param("offeredTime", "2027-06-04T11:00"))
                .andExpect(redirectedUrl("/tutor/board"))
                .andExpect(flash().attribute("errorMessage", "This request has expired."));
        assertEquals(0, offerRepository.count());

        MeetingOffer offer = new MeetingOffer();
        offer.setRequestId(request.getId());
        offer.setTutorId(tutor.getId());
        offer.setOfferedTime(LocalDateTime.of(2027, 6, 4, 11, 0));
        offer = offerRepository.save(offer);

        mockMvc.perform(post("/student/requests/" + request.getId() + "/offers/" + offer.getId() + "/accept")
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf()))
                .andExpect(flash().attribute("errorMessage", "This request has expired."));
        assertEquals("OPEN", requestRepository.findById(request.getId()).orElseThrow().getStatus());
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

    @Test
    void h2LockAnalyzerNamesTheOtherCopy() {
        JdbcSQLNonTransientConnectionException cause = new JdbcSQLNonTransientConnectionException(
                "Database may be already in use: ./data/rapidtutor.mv.db", null, "90020", 90020, null, null);
        var analysis = new H2AlreadyRunningAnalyzer().analyze(cause);
        assertEquals(H2AlreadyRunningAnalyzer.ALREADY_RUNNING, analysis.getDescription());
    }

    @Test
    void legacyDescriptionAndFeedbackColumnsWiden() {
        jdbcTemplate.execute("ALTER TABLE TUTORING_REQUEST ALTER COLUMN SHORT_DESCRIPTION VARCHAR(255)");
        jdbcTemplate.execute("ALTER TABLE REVIEW ALTER COLUMN FEEDBACK VARCHAR(255)");
        assertEquals(255, legacyColumnWiden.characterLength("TUTORING_REQUEST", "SHORT_DESCRIPTION"));
        assertEquals(255, legacyColumnWiden.characterLength("REVIEW", "FEEDBACK"));
        legacyColumnWiden.widen("TUTORING_REQUEST", "SHORT_DESCRIPTION", 2000);
        legacyColumnWiden.widen("REVIEW", "FEEDBACK", 1000);
        assertEquals(2000, legacyColumnWiden.characterLength("TUTORING_REQUEST", "SHORT_DESCRIPTION"));
        assertEquals(1000, legacyColumnWiden.characterLength("REVIEW", "FEEDBACK"));
    }

    @Test
    void concurrentBalanceWavesStayConsistent() throws Exception {
        for (int wave = 1; wave <= 20; wave++) {
            assertTrue(balanceWave(wave),
                    "wave " + wave + ": stored bounties, refunds, and review rewards did not match the balances");
        }
    }

    @Test
    void concurrentOpenCapStaysAtThree() throws Exception {
        for (int wave = 1; wave <= 20; wave++) {
            User student = saveStudent("cap-" + wave);
            setBalance(student.getUsername(), 50);
            for (int i = 0; i < 2; i++) {
                economyService.createRequestWithBounty(student.getUsername(), newRequest(student, "HCDD 412", "held-" + i), 0);
            }
            ExecutorService pool = Executors.newFixedThreadPool(16);
            List<Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                int n = i;
                tasks.add(pool.submit(() -> {
                    try {
                        economyService.createRequestWithBounty(student.getUsername(),
                                newRequest(student, "HCDD 412", "race-" + n), 0);
                    } catch (IllegalStateException | IllegalArgumentException | ObjectOptimisticLockingFailureException ignored) {
                        // a refused post or a reloaded balance leaves the committed rows as they are
                    }
                }));
            }
            for (Future<?> task : tasks) {
                task.get(30, TimeUnit.SECONDS);
            }
            pool.shutdown();
            entityManager.clear();
            assertEquals(3, requestRepository.countByStudentIdAndStatus(student.getId(), "OPEN"),
                    "wave " + wave + ": a student who started with 2 open requests did not stay at 3");
        }
    }

    @Test
    void notificationGuessesWhileIdsAreCreatedStayOnSite() throws Exception {
        User owner = userRepository.findByUsername("student1");
        User other = saveStudent("guesser");
        TutoringRequest request = requestRepository.save(newRequest(owner, "HCDD 412", "Private blocker"));
        String victimLink = "/student/requests/" + request.getId();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        AtomicInteger created = new AtomicInteger();
        List<Future<?>> tasks = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            tasks.add(pool.submit(() -> {
                Notification note = new Notification();
                note.setRecipientUserId(owner.getId());
                note.setMessage("Offer " + created.incrementAndGet());
                note.setLink(victimLink);
                note.setCreatedAt(LocalDateTime.now());
                notificationRepository.save(note);
            }));
        }
        for (int id = 1; id <= 40; id++) {
            long guess = id;
            tasks.add(pool.submit(() -> {
                try {
                    MvcResult result = mockMvc.perform(post("/notifications/" + guess + "/read")
                                    .with(user("guesser").roles("STUDENT"))
                                    .with(csrf())
                                    .param("follow", "true"))
                            .andReturn();
                    String location = result.getResponse().getRedirectedUrl();
                    if (location != null && location.contains(victimLink)) {
                        throw new IllegalStateException("guess landed on " + location);
                    }
                } catch (IllegalStateException e) {
                    throw e;
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }));
        }
        for (Future<?> task : tasks) {
            task.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();
        mockMvc.perform(post("/notifications/" + notificationRepository.findAll().get(0).getId() + "/read")
                        .with(user("guesser").roles("STUDENT"))
                        .with(csrf())
                        .param("follow", "true"))
                .andExpect(redirectedUrl("/notifications"));
        assertNull(notificationRepository.findAll().get(0).getReadAt());
    }

    @Test
    void offSiteReadLinkStaysOnSite() throws Exception {
        User owner = userRepository.findByUsername("student1");
        for (String link : List.of("https://example.com/phish", "//example.com/phish", "/\\example.com", "/student/dashboard ")) {
            Notification note = new Notification();
            note.setRecipientUserId(owner.getId());
            note.setMessage("Open this");
            note.setLink(link);
            note.setCreatedAt(LocalDateTime.now());
            note = notificationRepository.save(note);
            MvcResult result = mockMvc.perform(post("/notifications/" + note.getId() + "/read")
                            .with(user("student1").roles("STUDENT"))
                            .with(csrf())
                            .param("follow", "true"))
                    .andExpect(redirectedUrl("/notifications"))
                    .andReturn();
            assertFalse(String.valueOf(result.getResponse().getRedirectedUrl()).contains("example.com"));
        }
    }

    @Test
    void hungDiagnosisCallsLeaveLoginResponsive() throws Exception {
        List<Socket> held = new ArrayList<>();
        Thread acceptorThread = null;
        try (ServerSocket server = new ServerSocket()) {
            server.bind(new InetSocketAddress("127.0.0.1", 0));
            server.setSoTimeout(20000);
            acceptorThread = new Thread(() -> {
                try {
                    while (!server.isClosed()) {
                        Socket socket = server.accept();
                        synchronized (held) {
                            held.add(socket);
                        }
                    }
                } catch (Exception ignored) {
                    // closed when the 200 calls are done
                }
            });
            acceptorThread.setDaemon(true);
            acceptorThread.start();
            Field key = AiDiagnosisService.class.getDeclaredField("apiKey");
            Field url = AiDiagnosisService.class.getDeclaredField("baseUrl");
            key.setAccessible(true);
            url.setAccessible(true);
            String previousKey = (String) key.get(aiDiagnosisService);
            String previousUrl = (String) url.get(aiDiagnosisService);
            key.set(aiDiagnosisService, "not-the-mock-key");
            url.set(aiDiagnosisService, "http://127.0.0.1:" + server.getLocalPort() + "/hang");
            ExecutorService pool = Executors.newFixedThreadPool(16);
            try {
                List<Future<?>> calls = new ArrayList<>();
                for (int i = 0; i < 16; i++) {
                    int n = i;
                    calls.add(pool.submit(() -> duckQuietly("Hang " + n)));
                }
                long finished = waitUntilDone(calls, 8, 4000);
                long stillHung = calls.stream().filter(call -> !call.isDone()).count();
                assertTrue(finished >= 8 && stillHung >= 8, "the cap did not keep hung diagnosis calls from filling the server");
                long started = System.nanoTime();
                Future<Void> login = pool.submit(() -> {
                    mockMvc.perform(get("/login")).andExpect(status().isOk());
                    return null;
                });
                login.get(5, TimeUnit.SECONDS);
                assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(5));
                for (int i = 16; i < 200; i++) {
                    int n = i;
                    calls.add(pool.submit(() -> duckQuietly("Hang " + n)));
                }
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
                for (int i = 16; i < calls.size(); i++) {
                    long remaining = deadline - System.nanoTime();
                    assertTrue(remaining > 0, "turned-away diagnosis calls did not finish");
                    calls.get(i).get(remaining, TimeUnit.NANOSECONDS);
                }
            } finally {
                key.set(aiDiagnosisService, previousKey);
                url.set(aiDiagnosisService, previousUrl);
                pool.shutdownNow();
            }
        } finally {
            synchronized (held) {
                for (Socket socket : held) {
                    socket.close();
                }
            }
            if (acceptorThread != null) {
                acceptorThread.join(1000);
            }
        }
    }

    @Test
    void ownerNotificationList() throws Exception {
        mockMvc.perform(get("/notifications").with(user("student1").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Notifications")));
    }

    @Test
    void ownerMarksANotificationRead() throws Exception {
        Notification note = saveNote(userRepository.findByUsername("student1").getId(), "/student/dashboard");
        mockMvc.perform(post("/notifications/" + note.getId() + "/read")
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf())
                        .param("follow", "false"))
                .andExpect(redirectedUrl("/notifications"));
        assertTrue(notificationRepository.findById(note.getId()).orElseThrow().getReadAt() != null);
    }

    @Test
    void ownerFollowsTheirOwnNotification() throws Exception {
        Notification note = saveNote(userRepository.findByUsername("student1").getId(), "/student/dashboard");
        mockMvc.perform(post("/notifications/" + note.getId() + "/read")
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf())
                        .param("follow", "true"))
                .andExpect(redirectedUrl("/student/dashboard"));
        assertTrue(notificationRepository.findById(note.getId()).orElseThrow().getReadAt() != null);
    }

    @Test
    void knowledgeSearchHitsOneSeededArticle() throws Exception {
        mockMvc.perform(get("/api/knowledge/search")
                        .param("course", "IST 440W")
                        .param("topic", "sprint goal")
                        .with(user("student1").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sprint goal versus a task list")));
    }

    @Test
    void knowledgeSearchMisses() throws Exception {
        mockMvc.perform(get("/api/knowledge/search")
                        .param("course", "IST 440W")
                        .param("topic", "zzzz-not-a-topic")
                        .with(user("student1").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("[]")));
    }

    @Test
    void knowledgeSearchRejectsACourseThatIsNotOffered() throws Exception {
        mockMvc.perform(get("/api/knowledge/search")
                        .param("course", "HIST 100")
                        .param("topic", "sprint goal")
                        .with(user("student1").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("[]")));
    }

    @Test
    void aBlankKnowledgeSearchReturnsNothing() throws Exception {
        mockMvc.perform(get("/api/knowledge/search")
                        .param("course", "IST 440W")
                        .param("topic", " ")
                        .with(user("student1").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("[]")));
        mockMvc.perform(get("/api/knowledge/search")
                        .param("course", " ")
                        .param("topic", "sprint goal")
                        .with(user("student1").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("[]")));
    }

    @Test
    void ownerSavesATakeawayAfterTheSessionIsResolved() throws Exception {
        User student = userRepository.findByUsername("student1");
        DuckDraft draft = escalatedDraft(student, "Takeaway topic");
        mockMvc.perform(post("/student/drafts/" + draft.getId() + "/takeaway")
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf())
                        .param("takeaway", "The loop variable now changes."))
                .andExpect(redirectedUrl("/student/dashboard"))
                .andExpect(flash().attribute("successMessage", "Saved what changed."));
        assertEquals("The loop variable now changes.", duckDraftRepository.findById(draft.getId()).orElseThrow().getTakeaway());
    }

    @Test
    void anotherStudentCannotSaveThatTakeaway() throws Exception {
        User student = userRepository.findByUsername("student1");
        saveStudent("other-student");
        DuckDraft draft = escalatedDraft(student, "Owned takeaway");
        mockMvc.perform(post("/student/drafts/" + draft.getId() + "/takeaway")
                        .with(user("other-student").roles("STUDENT"))
                        .with(csrf())
                        .param("takeaway", "Not my blocker."))
                .andExpect(redirectedUrl("/student/dashboard"))
                .andExpect(flash().attribute("errorMessage", "That blocker cannot be closed yet."));
        assertNull(duckDraftRepository.findById(draft.getId()).orElseThrow().getTakeaway());
    }

    @Test
    void adminDeclinesAnApplicant() throws Exception {
        User applicant = saveStudent("applicant-1");
        applicant.setRole("APPLICANT");
        applicant = userRepository.save(applicant);
        mockMvc.perform(post("/admin/applicants/" + applicant.getId() + "/decline")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attribute("successMessage", "Application declined."));
        assertEquals("DECLINED", userRepository.findById(applicant.getId()).orElseThrow().getRole());
    }

    @Test
    void anAdministratorCannotApproveAStudent() throws Exception {
        User student = userRepository.findByUsername("student1");
        mockMvc.perform(post("/admin/applicants/" + student.getId() + "/approve")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attribute("errorMessage", "That person is not waiting for approval."));
        assertEquals("STUDENT", userRepository.findByUsername("student1").getRole());
    }

    @Test
    void anApplicantWithNoCourseCannotBeApproved() throws Exception {
        User applicant = saveStudent("no-courses");
        applicant.setRole("APPLICANT");
        applicant.setAppliedSubjects("  ");
        applicant = userRepository.save(applicant);

        mockMvc.perform(post("/admin/applicants/" + applicant.getId() + "/approve")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attribute("errorMessage", "Choose at least one course before approving."));
        User stored = userRepository.findById(applicant.getId()).orElseThrow();
        assertEquals("APPLICANT", stored.getRole());
        assertNull(stored.getSubjects());
    }

    @Test
    void anAdministratorCannotDeclineAnApprovedTutor() throws Exception {
        User tutor = userRepository.findByUsername("tutor1");
        mockMvc.perform(post("/admin/applicants/" + tutor.getId() + "/decline")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attribute("errorMessage", "That person is not waiting for approval."));
        assertEquals("TUTOR", userRepository.findByUsername("tutor1").getRole());
    }

    @Test
    void adminCreatesAnAdministrator() throws Exception {
        mockMvc.perform(post("/admin/administrators")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("displayName", "New Admin")
                        .param("email", "newadmin@example.com")
                        .param("username", "newadmin")
                        .param("password", "temporary-1")
                        .param("confirmPassword", "temporary-1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("was created")));
        User created = userRepository.findByUsername("newadmin");
        assertEquals("ADMIN", created.getRole());
        assertTrue(created.isMustChangePassword());
    }

    @Test
    void wrongCurrentPasswordIsRejected() throws Exception {
        try {
            mockMvc.perform(post("/account/password")
                            .with(user("student1").roles("STUDENT"))
                            .with(csrf())
                            .param("currentPassword", "wrong-password")
                            .param("password", "replacement-1")
                            .param("confirmPassword", "replacement-1"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Current password is incorrect.")));
            assertTrue(passwordEncoder.matches("password", userRepository.findByUsername("student1").getPassword()));
        } finally {
            restoreStudentPassword();
        }
    }

    @Test
    void aMatchingNewPasswordIsSaved() throws Exception {
        try {
            mockMvc.perform(post("/account/password")
                            .with(user("student1").roles("STUDENT"))
                            .with(csrf())
                            .param("currentPassword", "password")
                            .param("password", "replacement-1")
                            .param("confirmPassword", "replacement-1"))
                    .andExpect(redirectedUrl("/student/dashboard"));
            User student = userRepository.findByUsername("student1");
            assertTrue(passwordEncoder.matches("replacement-1", student.getPassword()));
            assertFalse(student.isMustChangePassword());
        } finally {
            restoreStudentPassword();
        }
    }

    @Test
    void aNewAdminMustChangeTheTemporaryPasswordFirst() throws Exception {
        User admin = saveStudent("temp-admin");
        admin.setRole("ADMIN");
        admin.setMustChangePassword(true);
        userRepository.save(admin);
        mockMvc.perform(get("/admin").with(user("temp-admin").roles("ADMIN")))
                .andExpect(redirectedUrl("/account/password"));
        mockMvc.perform(get("/admin/insights").with(user("temp-admin").roles("ADMIN")))
                .andExpect(redirectedUrl("/account/password"));
    }

    @Test
    void anonymousVisitorsAreSentToLogin() throws Exception {
        for (String path : List.of("/student/dashboard", "/tutor/board", "/admin", "/api/knowledge/search")) {
            MvcResult result = mockMvc.perform(get(path)).andExpect(status().is3xxRedirection()).andReturn();
            assertTrue(result.getResponse().getRedirectedUrl().contains("/login"), path);
        }
    }

    @Test
    void aStudentCannotOpenTheTutorBoard() throws Exception {
        mockMvc.perform(get("/tutor/board").with(user("student1").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aTutorCannotOpenTheStudentDashboard() throws Exception {
        mockMvc.perform(get("/student/dashboard").with(user("tutor1").roles("TUTOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aDeclinedApplicantCannotOpenTheTutorBoard() throws Exception {
        User declined = declinedUser("declined-board");
        mockMvc.perform(get("/tutor/board").with(user(declined.getUsername()).roles("DECLINED")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aDeclinedApplicantCanReadTheDecision() throws Exception {
        User declined = declinedUser("declined-status");
        mockMvc.perform(get("/apply/status").with(user(declined.getUsername()).roles("DECLINED")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Your tutor application was declined.")));
    }

    @Test
    void aFourthOpenRequestIsRejected() throws Exception {
        User student = userRepository.findByUsername("student1");
        MockHttpSession session = duckSession("Loops", "I am stuck on the loop condition.");
        for (int i = 0; i < 3; i++) {
            requestRepository.save(newRequest(student, "HCDD 412", "Open " + i));
        }
        postBooking(session, "0", "Zoom", "Today", "Quick explanation (10-15 mins)")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("You already have 3 open requests.")));
        assertEquals(3, requestRepository.countByStudentIdAndStatus(student.getId(), "OPEN"));
    }

    @Test
    void aBountyAboveTheTokenBalanceIsRejected() throws Exception {
        setBalance("student1", 10);
        MockHttpSession session = duckSession("Loops", "I am stuck on the loop condition.");
        postBooking(session, "20", "Zoom", "Today", "Quick explanation (10-15 mins)")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Insufficient token balance to post this bounty.")));
        assertEquals(10, userRepository.findByUsername("student1").getTokenBalance());
        assertEquals(0, requestRepository.count());
    }

    @Test
    void twoConcurrentAcceptsAssignOneTutor() throws Exception {
        User student = userRepository.findByUsername("student1");
        User tutorA = userRepository.findByUsername("tutor1");
        User tutorB = saveStudent("racing-accept-tutor");
        tutorB.setRole("TUTOR");
        tutorB = userRepository.save(tutorB);
        for (int wave = 1; wave <= 20; wave++) {
            TutoringRequest request = requestRepository.save(newRequest(student, "HCDD 412", "Race " + wave));
            MeetingOffer offerA = saveOffer(request.getId(), tutorA.getId(), LocalDateTime.of(2027, 6, 4, 11, 0));
            MeetingOffer offerB = saveOffer(request.getId(), tutorB.getId(), LocalDateTime.of(2027, 6, 4, 12, 0));
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            List<String> refused = Collections.synchronizedList(new ArrayList<>());
            List<Future<?>> tasks = List.of(
                    pool.submit(() -> acceptTogether(start, student, request.getId(), offerA.getId(), refused)),
                    pool.submit(() -> acceptTogether(start, student, request.getId(), offerB.getId(), refused)));
            start.countDown();
            for (Future<?> task : tasks) {
                task.get(30, TimeUnit.SECONDS);
            }
            pool.shutdown();
            entityManager.clear();
            TutoringRequest stored = requestRepository.findById(request.getId()).orElseThrow();
            assertEquals("CLAIMED", stored.getStatus(), "wave " + wave + " " + refused);
            assertTrue(tutorA.getId().equals(stored.getAssignedTutorId())
                            || tutorB.getId().equals(stored.getAssignedTutorId()),
                    "wave " + wave + " assigned " + stored.getAssignedTutorId());
            long claims = eventRepository.findByRequestIdOrderByAtAsc(request.getId()).stream()
                    .filter(event -> "CLAIMED".equals(event.getToStatus()))
                    .count();
            assertEquals(1, claims, "wave " + wave);
        }
    }

    @Test
    void twoConcurrentResolvesPayTheTutorOnce() throws Exception {
        User student = userRepository.findByUsername("student1");
        User tutor = userRepository.findByUsername("tutor1");
        for (int wave = 1; wave <= 20; wave++) {
            int before = userRepository.findByUsername("tutor1").getTokenBalance();
            TutoringRequest request = economyService.createRequestWithBounty(
                    "student1", newRequest(student, "HCDD 412", "Resolve race " + wave), 5);
            request.setStatus("CLAIMED");
            request.setAssignedTutorId(tutor.getId());
            request = requestRepository.save(request);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            List<String> refused = Collections.synchronizedList(new ArrayList<>());
            Long requestId = request.getId();
            List<Future<?>> tasks = List.of(
                    pool.submit(() -> resolveTogether(start, student, requestId, refused)),
                    pool.submit(() -> resolveTogether(start, student, requestId, refused)));
            start.countDown();
            for (Future<?> task : tasks) {
                task.get(30, TimeUnit.SECONDS);
            }
            pool.shutdown();
            entityManager.clear();
            TutoringRequest stored = requestRepository.findById(requestId).orElseThrow();
            assertEquals("RESOLVED", stored.getStatus(), "wave " + wave + " " + refused);
            assertEquals(before + 5, userRepository.findByUsername("tutor1").getTokenBalance(),
                    "wave " + wave + " " + refused);
            long resolves = eventRepository.findByRequestIdOrderByAtAsc(requestId).stream()
                    .filter(event -> "RESOLVED".equals(event.getToStatus()))
                    .count();
            assertEquals(1, resolves, "wave " + wave + " " + refused);
        }
    }

    @Test
    void aSecondAcceptIsRejectedAfterTheRequestIsClaimed() throws Exception {
        User student = userRepository.findByUsername("student1");
        User tutorA = userRepository.findByUsername("tutor1");
        User tutorB = saveStudent("second-accept-tutor");
        tutorB.setRole("TUTOR");
        tutorB = userRepository.save(tutorB);
        TutoringRequest request = requestRepository.save(newRequest(student, "HCDD 412", "Second accept"));
        MeetingOffer first = saveOffer(request.getId(), tutorA.getId(), LocalDateTime.of(2027, 6, 4, 11, 0));

        mockMvc.perform(post("/student/requests/" + request.getId() + "/offers/" + first.getId() + "/accept")
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf()))
                .andExpect(redirectedUrl("/student/requests/" + request.getId()))
                .andExpect(flash().attribute("successMessage", "Time accepted. The session is claimed."));

        MeetingOffer late = saveOffer(request.getId(), tutorB.getId(), LocalDateTime.of(2027, 6, 4, 12, 0));
        mockMvc.perform(post("/student/requests/" + request.getId() + "/offers/" + late.getId() + "/accept")
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf()))
                .andExpect(redirectedUrl("/student/requests/" + request.getId()))
                .andExpect(flash().attribute("errorMessage", "This request was already claimed."));

        TutoringRequest stored = requestRepository.findById(request.getId()).orElseThrow();
        assertEquals("CLAIMED", stored.getStatus());
        assertEquals(tutorA.getId(), stored.getAssignedTutorId());
    }

    @Test
    void aStaleUserBalanceAsksTheCallerToRetry() {
        User student = saveStudent("stale-balance");
        setBalance(student.getUsername(), 40);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
                tx.executeWithoutResult(status -> {
                    User managed = userRepository.findByUsername("stale-balance");
                    jdbcTemplate.update(
                            "UPDATE users SET token_balance = 15, version = version + 1 WHERE id = ?",
                            student.getId());
                    managed.setTokenBalance(1);
                    invokeSaveUser(managed);
                }));
        assertEquals("This request changed. Open it and try again.", thrown.getMessage());
        entityManager.clear();
        assertEquals(40, userRepository.findByUsername("stale-balance").getTokenBalance());
    }

    @Test
    void aTutorCannotOpenARequestOutsideTheirSubjects() throws Exception {
        User student = userRepository.findByUsername("student1");
        TutoringRequest request = requestRepository.save(newRequest(student, "HIST 100", "Outside"));
        mockMvc.perform(get("/tutor/requests/" + request.getId()).with(user("tutor1").roles("TUTOR")))
                .andExpect(redirectedUrl("/tutor/board"))
                .andExpect(flash().attribute("errorMessage", "This request is outside your subjects."));
    }

    @Test
    void aTutorCannotCancelAnotherTutorsSession() throws Exception {
        User student = userRepository.findByUsername("student1");
        User other = saveStudent("other-tutor");
        other.setRole("TUTOR");
        other = userRepository.save(other);
        TutoringRequest request = requestRepository.save(newRequest(student, "HCDD 412", "Claimed elsewhere"));
        request.setStatus("CLAIMED");
        request.setAssignedTutorId(other.getId());
        request = requestRepository.save(request);
        mockMvc.perform(post("/tutor/requests/" + request.getId() + "/cancel")
                        .with(user("tutor1").roles("TUTOR"))
                        .with(csrf()))
                .andExpect(redirectedUrl("/tutor/board"))
                .andExpect(flash().attribute("errorMessage", "You can only cancel a session assigned to you."));
        assertEquals("CLAIMED", requestRepository.findById(request.getId()).orElseThrow().getStatus());
    }

    @Test
    void aPostWithoutTheCsrfTokenIsRejected() throws Exception {
        User student = userRepository.findByUsername("student1");
        TutoringRequest request = requestRepository.save(newRequest(student, "HCDD 412", "Needs the token"));
        mockMvc.perform(post("/student/requests/" + request.getId() + "/cancel")
                        .with(user("student1").roles("STUDENT")))
                .andExpect(status().isForbidden());
        assertEquals("OPEN", requestRepository.findById(request.getId()).orElseThrow().getStatus());
    }

    @Test
    void anonymousAccessToTheH2Console() throws Exception {
        MvcResult result = mockMvc.perform(get("/h2-console")).andExpect(status().is3xxRedirection()).andReturn();
        assertTrue(result.getResponse().getRedirectedUrl().contains("/login"));
    }

    @Test
    void aWrongRecoveryCodeIsRejected() throws Exception {
        mockMvc.perform(post("/forgot-password")
                        .with(csrf())
                        .param("email", "student1@rapidtutor.local")
                        .param("recoveryCode", "NOT-A-REAL-CODE")
                        .param("password", "replacement-1")
                        .param("confirmPassword", "replacement-1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("That email and recovery code do not match.")));
        assertTrue(passwordEncoder.matches("password", userRepository.findByUsername("student1").getPassword()));
    }

    @Test
    void aTooShortNewPasswordIsRejected() throws Exception {
        try {
            mockMvc.perform(post("/account/password")
                            .with(user("student1").roles("STUDENT"))
                            .with(csrf())
                            .param("currentPassword", "password")
                            .param("password", "short")
                            .param("confirmPassword", "short"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Use at least 10 characters.")));
            assertTrue(passwordEncoder.matches("password", userRepository.findByUsername("student1").getPassword()));
        } finally {
            restoreStudentPassword();
        }
    }

    @Test
    void aPasswordConfirmationThatDoesNotMatchIsRejected() throws Exception {
        try {
            mockMvc.perform(post("/account/password")
                            .with(user("student1").roles("STUDENT"))
                            .with(csrf())
                            .param("currentPassword", "password")
                            .param("password", "replacement-1")
                            .param("confirmPassword", "replacement-2"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Password confirmation does not match.")));
            assertTrue(passwordEncoder.matches("password", userRepository.findByUsername("student1").getPassword()));
        } finally {
            restoreStudentPassword();
        }
    }

    @Test
    void aPasswordThatMatchesTheUsernameIsRejected() throws Exception {
        saveStudent("longstudent");
        mockMvc.perform(post("/account/password")
                        .with(user("longstudent").roles("STUDENT"))
                        .with(csrf())
                        .param("currentPassword", "password")
                        .param("password", "longstudent")
                        .param("confirmPassword", "longstudent"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Password must be different from your username and email.")));
        assertTrue(passwordEncoder.matches("password", userRepository.findByUsername("longstudent").getPassword()));
    }

    @Test
    void aPasswordThatMatchesTheEmailIsRejected() throws Exception {
        User student = saveStudent("mailboxuser");
        student.setEmail("mailboxuser@example.com");
        userRepository.save(student);

        mockMvc.perform(post("/account/password")
                        .with(user("mailboxuser").roles("STUDENT"))
                        .with(csrf())
                        .param("currentPassword", "password")
                        .param("password", "mailboxuser@example.com")
                        .param("confirmPassword", "mailboxuser@example.com"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Password must be different from your username and email.")));
        assertTrue(passwordEncoder.matches("password", userRepository.findByUsername("mailboxuser").getPassword()));
    }

    @Test
    void aStudentWhoMustChangeTheirPasswordIsSentToThePasswordPage() throws Exception {
        User student = userRepository.findByUsername("student1");
        student.setMustChangePassword(true);
        userRepository.save(student);
        try {
            mockMvc.perform(get("/student/dashboard").with(user("student1").roles("STUDENT")))
                    .andExpect(redirectedUrl("/account/password"));
        } finally {
            student = userRepository.findByUsername("student1");
            student.setMustChangePassword(false);
            userRepository.save(student);
        }
    }

    @Test
    void aTutorWhoMustChangeTheirPasswordIsSentToThePasswordPage() throws Exception {
        User tutor = userRepository.findByUsername("tutor1");
        tutor.setMustChangePassword(true);
        userRepository.save(tutor);
        try {
            mockMvc.perform(get("/tutor/board").with(user("tutor1").roles("TUTOR")))
                    .andExpect(redirectedUrl("/account/password"));
        } finally {
            tutor = userRepository.findByUsername("tutor1");
            tutor.setMustChangePassword(false);
            userRepository.save(tutor);
        }
    }

    @Test
    void anInvalidMeetingMethodIsRejected() throws Exception {
        MockHttpSession session = duckSession("Loops", "I am stuck on the loop condition.");
        postBooking(session, "0", "Carrier pigeon", "Today", "Quick explanation (10-15 mins)")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Choose a meeting method.")));
        assertEquals(0, requestRepository.count());
    }

    @Test
    void anInvalidResponseTimeIsRejected() throws Exception {
        MockHttpSession session = duckSession("Loops", "I am stuck on the loop condition.");
        postBooking(session, "0", "Zoom", "Whenever", "Quick explanation (10-15 mins)")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Choose a response time.")));
        assertEquals(0, requestRepository.count());
    }

    @Test
    void anInvalidSessionLengthIsRejected() throws Exception {
        MockHttpSession session = duckSession("Loops", "I am stuck on the loop condition.");
        postBooking(session, "0", "Zoom", "Today", "All day")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Choose a session length.")));
        assertEquals(0, requestRepository.count());
    }

    @Test
    void aTopicOver120CharactersIsRejected() throws Exception {
        mockMvc.perform(post("/student/requests/duck")
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf())
                        .param("course", "HCDD 412")
                        .param("topic", "t".repeat(121))
                        .param("shortDescription", "A short description."))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Keep that entry under 120 characters.")));
        assertEquals(0, duckDraftRepository.count());
    }

    @Test
    void aTakeawayOver500CharactersIsRejected() throws Exception {
        User student = userRepository.findByUsername("student1");
        DuckDraft draft = escalatedDraft(student, "Long takeaway");
        mockMvc.perform(post("/student/drafts/" + draft.getId() + "/takeaway")
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf())
                        .param("takeaway", "w".repeat(501)))
                .andExpect(redirectedUrl("/student/dashboard"))
                .andExpect(flash().attribute("errorMessage", "Keep that entry under 500 characters."));
        assertNull(duckDraftRepository.findById(draft.getId()).orElseThrow().getTakeaway());
    }

    @Test
    void aReviewScoreBelow1IsRejected() throws Exception {
        TutoringRequest request = resolvedRequest();
        mockMvc.perform(post("/student/reviews")
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf())
                        .param("requestId", request.getId().toString())
                        .param("score", "0")
                        .param("feedback", "Too low"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Score must be between 1 and 5.")));
        assertEquals(0, reviewRepository.count());
    }

    @Test
    void aReviewScoreAbove5IsRejected() throws Exception {
        TutoringRequest request = resolvedRequest();
        mockMvc.perform(post("/student/reviews")
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf())
                        .param("requestId", request.getId().toString())
                        .param("score", "6")
                        .param("feedback", "Too high"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Score must be between 1 and 5.")));
        assertEquals(0, reviewRepository.count());
    }

    @Test
    void theReviewFormIsClosedUntilTheSessionIsResolved() throws Exception {
        User student = userRepository.findByUsername("student1");
        TutoringRequest request = requestRepository.save(newRequest(student, "HCDD 412", "Still open"));

        mockMvc.perform(get("/student/requests/" + request.getId() + "/review")
                        .with(user("student1").roles("STUDENT")))
                .andExpect(redirectedUrl("/student/dashboard"))
                .andExpect(flash().attribute("errorMessage", "Resolve the session before leaving a review."));
        assertEquals(0, reviewRepository.count());
    }

    @Test
    void aMissingStudentRequestStaysOnTheDashboard() throws Exception {
        mockMvc.perform(get("/student/requests/" + Long.MAX_VALUE)
                        .with(user("student1").roles("STUDENT")))
                .andExpect(redirectedUrl("/student/dashboard"))
                .andExpect(flash().attribute("errorMessage", "That request does not exist."));
    }

    @Test
    void aMissingTutorRequestStaysOnTheBoard() throws Exception {
        mockMvc.perform(get("/tutor/requests/" + Long.MAX_VALUE)
                        .with(user("tutor1").roles("TUTOR")))
                .andExpect(redirectedUrl("/tutor/board"))
                .andExpect(flash().attribute("errorMessage", "That request does not exist."));
    }

    @Test
    void anApplicantCannotOpenTheStudentDashboard() throws Exception {
        User applicant = saveStudent("applicant-student");
        applicant.setRole("APPLICANT");
        userRepository.save(applicant);
        mockMvc.perform(get("/student/dashboard").with(user("applicant-student").roles("APPLICANT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anApplicantCannotOpenTheAdminOverview() throws Exception {
        User applicant = saveStudent("applicant-admin");
        applicant.setRole("APPLICANT");
        userRepository.save(applicant);
        mockMvc.perform(get("/admin").with(user("applicant-admin").roles("APPLICANT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aDeclinedUserCannotOpenTheStudentDashboard() throws Exception {
        User declined = declinedUser("declined-student");
        mockMvc.perform(get("/student/dashboard").with(user(declined.getUsername()).roles("DECLINED")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aDeclinedUserCannotOpenTheAdminOverview() throws Exception {
        User declined = declinedUser("declined-admin");
        mockMvc.perform(get("/admin").with(user(declined.getUsername()).roles("DECLINED")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aDeclinedUserCanOpenNotifications() throws Exception {
        User declined = declinedUser("declined-notes");
        mockMvc.perform(get("/notifications").with(user(declined.getUsername()).roles("DECLINED")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Notifications")));
    }

    @Test
    void aDeclinedUserCanOpenThePasswordPage() throws Exception {
        User declined = declinedUser("declined-password");
        mockMvc.perform(get("/account/password").with(user(declined.getUsername()).roles("DECLINED")))
                .andExpect(status().isOk());
    }

    @Test
    void aDeclinedUserCanSearchTheDatabank() throws Exception {
        User declined = declinedUser("declined-search");
        mockMvc.perform(get("/api/knowledge/search")
                        .param("course", "IST 440W")
                        .param("topic", "sprint goal")
                        .with(user(declined.getUsername()).roles("DECLINED")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sprint goal versus a task list")));
    }

    @Test
    void aTopicAndADescriptionThatContainAnglesAreShownAsText() throws Exception {
        mockMvc.perform(post("/student/requests/duck")
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf())
                        .param("course", "HCDD 412")
                        .param("topic", "loops <i>x</i>")
                        .param("shortDescription", "a <b>tag</b> here"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("loops &lt;i&gt;x&lt;/i&gt;")))
                .andExpect(content().string(containsString("a &lt;b&gt;tag&lt;/b&gt; here")))
                .andExpect(content().string(not(containsString("<i>x</i>"))))
                .andExpect(content().string(not(containsString("<b>tag</b>"))));
    }

    private boolean balanceWave(int wave) throws Exception {
        User student = saveStudent("wave-student-" + wave);
        setBalance(student.getUsername(), 100);
        User tutorA = userRepository.findByUsername("tutor1");
        User tutorB = saveStudent("wave-tutor-" + wave);
        tutorB.setRole("TUTOR");
        tutorB.setTokenBalance(40);
        tutorB = userRepository.save(tutorB);
        TutoringRequest refundable = economyService.createRequestWithBounty(
                student.getUsername(), newRequest(student, "HCDD 412", "refund-" + wave), 5);
        TutoringRequest payA = economyService.createRequestWithBounty(
                student.getUsername(), newRequest(student, "HCDD 412", "pay-a-" + wave), 7);
        payA.setStatus("CLAIMED");
        payA.setAssignedTutorId(tutorA.getId());
        payA = requestRepository.save(payA);
        TutoringRequest payB = economyService.createRequestWithBounty(
                student.getUsername(), newRequest(student, "HCDD 412", "pay-b-" + wave), 9);
        payB.setStatus("CLAIMED");
        payB.setAssignedTutorId(tutorB.getId());
        payB = requestRepository.save(payB);
        TutoringRequest reviewed = economyService.createRequestWithBounty(
                student.getUsername(), newRequest(student, "HCDD 412", "review-" + wave), 0);
        reviewed.setStatus("RESOLVED");
        reviewed.setAssignedTutorId(tutorA.getId());
        reviewed = requestRepository.save(reviewed);
        entityManager.clear();
        int studentStart = userRepository.findByUsername(student.getUsername()).getTokenBalance();
        int tutorAStart = userRepository.findByUsername("tutor1").getTokenBalance();
        int tutorBStart = userRepository.findByUsername(tutorB.getUsername()).getTokenBalance();
        Long refundId = refundable.getId();
        Long payAId = payA.getId();
        Long payBId = payB.getId();
        Long reviewRequestId = reviewed.getId();
        Long studentId = student.getId();
        Long tutorAId = tutorA.getId();
        Long tutorBId = tutorB.getId();
        String studentName = student.getUsername();
        ExecutorService pool = Executors.newFixedThreadPool(16);
        List<Future<?>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            int n = i;
            tasks.add(pool.submit(() -> {
                try {
                    economyService.createRequestWithBounty(studentName, newRequest(student, "HCDD 412", "bounty-" + wave + "-" + n), 1);
                } catch (RuntimeException ignored) {
                    // a conflict rolls the post back
                }
            }));
        }
        tasks.add(pool.submit(() -> {
            try {
                economyService.cancelAndRefund(refundId);
            } catch (RuntimeException ignored) {
            }
        }));
        tasks.add(pool.submit(() -> {
            try {
                economyService.resolveAndPayout(payAId, tutorAId);
            } catch (RuntimeException ignored) {
            }
        }));
        tasks.add(pool.submit(() -> {
            try {
                economyService.resolveAndPayout(payBId, tutorBId);
            } catch (RuntimeException ignored) {
            }
        }));
        tasks.add(pool.submit(() -> {
            try {
                Review review = new Review();
                review.setRequestId(reviewRequestId);
                review.setStudentId(studentId);
                review.setTutorId(tutorAId);
                review.setScore(5);
                review.setFeedback("Clear");
                economyService.saveReviewAndReward(review);
            } catch (RuntimeException ignored) {
            }
        }));
        for (int i = 0; i < 4; i++) {
            tasks.add(pool.submit(() -> {
                try {
                    economyService.cancelAndRefund(refundId);
                } catch (RuntimeException ignored) {
                }
            }));
        }
        for (Future<?> task : tasks) {
            task.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();
        entityManager.clear();
        int added = 0;
        for (TutoringRequest stored : requestRepository.findAll()) {
            if (studentId.equals(stored.getStudentId()) && stored.getTopic() != null && stored.getTopic().startsWith("bounty-")) {
                added += stored.getBountyValue() == null ? 0 : stored.getBountyValue();
            }
        }
        int rewards = 0;
        for (Review review : reviewRepository.findAll()) {
            if (studentId.equals(review.getStudentId())) {
                rewards += 10;
            }
        }
        int studentExpected = studentStart - added + rewards;
        if ("CANCELED".equals(requestRepository.findById(refundId).orElseThrow().getStatus())) {
            studentExpected += 5;
        }
        int tutorAExpected = tutorAStart;
        int tutorBExpected = tutorBStart;
        TutoringRequest paidA = requestRepository.findById(payAId).orElseThrow();
        TutoringRequest paidB = requestRepository.findById(payBId).orElseThrow();
        if ("RESOLVED".equals(paidA.getStatus())) {
            tutorAExpected += 7;
        }
        if ("RESOLVED".equals(paidB.getStatus())) {
            tutorBExpected += 9;
        }
        boolean studentOk = studentExpected == userRepository.findByUsername(studentName).getTokenBalance();
        boolean tutorAOk = tutorAExpected == userRepository.findByUsername("tutor1").getTokenBalance();
        boolean tutorBOk = tutorBExpected == userRepository.findByUsername(tutorB.getUsername()).getTokenBalance();
        return studentOk && tutorAOk && tutorBOk && tasks.size() > 10;
    }

    private long waitUntilDone(List<Future<?>> calls, int needed, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            long done = calls.stream().filter(Future::isDone).count();
            if (done >= needed) {
                return done;
            }
            Thread.sleep(50);
        }
        return calls.stream().filter(Future::isDone).count();
    }

    private void duckQuietly(String topic) {
        try {
            mockMvc.perform(post("/student/requests/duck")
                    .with(user("student1").roles("STUDENT"))
                    .with(csrf())
                    .param("course", "HCDD 412")
                    .param("topic", topic)
                    .param("shortDescription", "The call should not fill the server."));
        } catch (Exception ignored) {
            // the hang socket closes when the run stops
        }
    }

    private org.springframework.test.web.servlet.ResultActions postBooking(
            MockHttpSession session, String bounty, String meeting, String response, String effort) throws Exception {
        return mockMvc.perform(post("/student/requests")
                .session(session)
                .with(user("student1").roles("STUDENT"))
                .with(csrf())
                .param("studentExplanation", "I tried the condition.")
                .param("bountyValue", bounty)
                .param("preferredMeetingMethod", meeting)
                .param("desiredResponseTime", response)
                .param("expectedEffort", effort));
    }

    private Notification saveNote(Long recipientId, String link) {
        Notification note = new Notification();
        note.setRecipientUserId(recipientId);
        note.setMessage("Your offer was updated.");
        note.setLink(link);
        note.setCreatedAt(LocalDateTime.now());
        return notificationRepository.save(note);
    }

    private DuckDraft escalatedDraft(User student, String topic) {
        TutoringRequest request = requestRepository.save(newRequest(student, "HCDD 412", topic));
        request.setStatus("RESOLVED");
        request = requestRepository.save(request);
        DuckDraft draft = DuckDraft.from(request, "Core concept: a note.\nDiagnostic question: one?\nStarting step: try.", student.getId());
        draft.setOutcome(DuckDraft.ESCALATED);
        draft.setRequestId(request.getId());
        return duckDraftRepository.save(draft);
    }

    private TutoringRequest resolvedRequest() {
        User student = userRepository.findByUsername("student1");
        User tutor = userRepository.findByUsername("tutor1");
        TutoringRequest request = requestRepository.save(newRequest(student, "HCDD 412", "Reviewed"));
        request.setStatus("RESOLVED");
        request.setAssignedTutorId(tutor.getId());
        return requestRepository.save(request);
    }

    private User declinedUser(String username) {
        User user = saveStudent(username);
        user.setRole("DECLINED");
        return userRepository.save(user);
    }

    private void restoreStudentPassword() {
        User student = userRepository.findByUsername("student1");
        student.setPassword(passwordEncoder.encode("password"));
        student.setMustChangePassword(false);
        userRepository.save(student);
    }

    private MockHttpSession duckSession(String topic, String description) throws Exception {
        MvcResult ready = mockMvc.perform(post("/student/requests/duck")
                        .with(user("student1").roles("STUDENT"))
                        .with(csrf())
                        .param("course", "HCDD 412")
                        .param("topic", topic)
                        .param("shortDescription", description))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(topic + " is the idea to clarify")))
                .andExpect(content().string(containsString("Walk it through")))
                .andExpect(content().string(containsString("Your answer")))
                .andExpect(content().string(containsString("I'm still stuck")))
                .andExpect(content().string(not(containsString("Meeting method"))))
                .andExpect(content().string(not(containsString("Follow-up"))))
                .andReturn();
        return (MockHttpSession) ready.getRequest().getSession(false);
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

    private void resolveTogether(CountDownLatch start, User student, Long requestId, List<String> refused) {
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("resolve did not start");
            }
            economyService.resolve(student, requestId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (RuntimeException e) {
            refused.add(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void acceptTogether(CountDownLatch start, User student, Long requestId, Long offerId,
                                List<String> refused) {
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("accept did not start");
            }
            meetingOfferService.accept(student, requestId, offerId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (RuntimeException e) {
            refused.add(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void invokeSaveUser(User user) {
        try {
            Method saveUser = EconomyService.class.getDeclaredMethod("saveUser", User.class);
            saveUser.setAccessible(true);
            saveUser.invoke(AopTestUtils.getUltimateTargetObject(economyService), user);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private MeetingOffer saveOffer(Long requestId, Long tutorId, LocalDateTime when) {
        MeetingOffer offer = new MeetingOffer();
        offer.setRequestId(requestId);
        offer.setTutorId(tutorId);
        offer.setOfferedTime(when);
        return offerRepository.save(offer);
    }

    private TutoringRequest openExpiringRequest(String responseTime, LocalDateTime created, int bounty) {
        User student = userRepository.findByUsername("student1");
        TutoringRequest request = requestRepository.save(newRequest(student, "HCDD 412", responseTime));
        request.setDesiredResponseTime(responseTime);
        request.setCreationTimestamp(created);
        request.setBountyValue(bounty);
        return requestRepository.save(request);
    }

    private Review review(Long requestId, Long tutorId, Long studentId, int score) {
        Review review = new Review();
        review.setRequestId(requestId);
        review.setTutorId(tutorId);
        review.setStudentId(studentId);
        review.setScore(score);
        review.setFeedback("Clear");
        return review;
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
