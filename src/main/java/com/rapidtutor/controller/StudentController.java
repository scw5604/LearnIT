package com.rapidtutor.controller;

import com.rapidtutor.model.Courses;
import com.rapidtutor.model.DuckDraft;
import com.rapidtutor.model.KnowledgeArticle;
import com.rapidtutor.model.MeetingOffer;
import com.rapidtutor.model.Review;
import com.rapidtutor.model.TutoringRequest;
import com.rapidtutor.model.User;
import com.rapidtutor.repository.DuckDraftRepository;
import com.rapidtutor.repository.KnowledgeArticleRepository;
import com.rapidtutor.repository.MeetingOfferRepository;
import com.rapidtutor.repository.RequestEventRepository;
import com.rapidtutor.repository.ReviewRepository;
import com.rapidtutor.repository.TutoringRequestRepository;
import com.rapidtutor.repository.UserRepository;
import com.rapidtutor.service.AiDiagnosisService;
import com.rapidtutor.service.EconomyService;
import com.rapidtutor.service.FormText;
import com.rapidtutor.service.KnowledgeBankService;
import com.rapidtutor.service.MeetingOfferService;
import com.rapidtutor.service.PublicNames;
import com.rapidtutor.service.RequestFields;
import com.rapidtutor.service.TriageCloseService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.rapidtutor.model.RequestStatus.CLAIMED;
import static com.rapidtutor.model.RequestStatus.OPEN;
import static com.rapidtutor.model.RequestStatus.RESOLVED;

@Controller
@RequestMapping("/student")
public class StudentController {

    private final EconomyService economyService;
    private final TutoringRequestRepository requestRepository;
    private final ReviewRepository reviewRepository;
    private final AiDiagnosisService aiDiagnosisService;
    private final UserRepository userRepository;
    private final MeetingOfferRepository offerRepository;
    private final MeetingOfferService meetingOfferService;
    private final RequestEventRepository eventRepository;
    private final KnowledgeArticleRepository knowledgeArticleRepository;
    private final KnowledgeBankService knowledgeBankService;
    private final TriageCloseService triageCloseService;
    private final DuckDraftRepository draftRepository;
    private final PublicNames publicNames;

    public StudentController(EconomyService economyService, TutoringRequestRepository requestRepository,
                             ReviewRepository reviewRepository, AiDiagnosisService aiDiagnosisService,
                             UserRepository userRepository, MeetingOfferRepository offerRepository,
                             MeetingOfferService meetingOfferService, RequestEventRepository eventRepository,
                             KnowledgeArticleRepository knowledgeArticleRepository,
                             KnowledgeBankService knowledgeBankService,
                             TriageCloseService triageCloseService,
                             DuckDraftRepository draftRepository, PublicNames publicNames) {
        this.economyService = economyService;
        this.requestRepository = requestRepository;
        this.reviewRepository = reviewRepository;
        this.aiDiagnosisService = aiDiagnosisService;
        this.userRepository = userRepository;
        this.offerRepository = offerRepository;
        this.meetingOfferService = meetingOfferService;
        this.eventRepository = eventRepository;
        this.knowledgeArticleRepository = knowledgeArticleRepository;
        this.knowledgeBankService = knowledgeBankService;
        this.triageCloseService = triageCloseService;
        this.draftRepository = draftRepository;
        this.publicNames = publicNames;
    }

    /**
     * Opening Talk it through drops the session draft id.
     * The Trying row remains until the student chooses Continue.
     */
    @GetMapping("/requests/new")
    public String showRequestForm(Model model, HttpSession session) {
        clearDuck(session);
        TutoringRequest request = new TutoringRequest();
        request.setCourse(Courses.HCDD_412);
        model.addAttribute("tutoringRequest", request);
        model.addAttribute("courses", Courses.OFFERED);
        return "student/create-request";
    }

    @PostMapping("/requests/duck")
    public String talkItThrough(@RequestParam(required = false) String course,
                                @RequestParam(required = false) String topic,
                                @RequestParam(required = false) String shortDescription,
                                Principal principal, Model model, HttpSession session) {
        User student = requireStudent(principal);
        try {
            TutoringRequest request = RequestFields.blocker(course, topic, shortDescription);
            String note = aiDiagnosisService.prepareNote(request.getCourse(), request.getTopic(), request.getShortDescription());
            DuckDraft draft = draftRepository.save(DuckDraft.from(request, note, student.getId()));
            session.setAttribute(DuckDraft.SESSION_KEY, draft.getId());
            session.removeAttribute(DuckDraft.BOOKING_KEY);
            return duckForm(model, draft, null, null, null, false);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return requestForm(model, draftRequest(course, topic, shortDescription), e.getMessage());
        }
    }

    @PostMapping("/requests/duck/book")
    public String openBooking(@RequestParam(required = false) String studentExplanation,
                              Principal principal, Model model, HttpSession session,
                              RedirectAttributes redirectAttributes) {
        User student = requireStudent(principal);
        DuckDraft draft = duckDraft(session, student);
        if (draft == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Talk it through before posting.");
            return "redirect:/student/requests/new";
        }
        try {
            String explanation = checkedExplanation(studentExplanation);
            draft.setAnswer(explanation);
            draftRepository.save(draft);
            session.setAttribute(DuckDraft.BOOKING_KEY, Boolean.TRUE);
            return duckForm(model, draft, explanation, null, null, true);
        } catch (IllegalArgumentException e) {
            return duckForm(model, draft, studentExplanation, null, e.getMessage(), false);
        }
    }

    @PostMapping("/requests/duck/solved")
    public String figuredItOut(@RequestParam(required = false) String studentExplanation,
                               @RequestParam(required = false) String takeaway,
                               Principal principal, Model model, HttpSession session,
                               RedirectAttributes redirectAttributes) {
        User student = requireStudent(principal);
        DuckDraft draft = duckDraft(session, student);
        if (draft == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Talk it through before posting.");
            return "redirect:/student/requests/new";
        }
        try {
            String cleanTakeaway = checkedTakeaway(takeaway);
            if (studentExplanation != null && !studentExplanation.isBlank()) {
                draft.setAnswer(checkedExplanation(studentExplanation));
            }
            boolean added = knowledgeBankService.saveTriage(draft.getCourse(), draft.getTopic(), draft.savedNote());
            draft.setTakeaway(cleanTakeaway);
            draft.setOutcome(DuckDraft.SOLVED);
            draftRepository.save(draft);
            clearDuck(session);
            redirectAttributes.addFlashAttribute("successMessage",
                    added ? "Saved. The note was added to the databank." : "Saved.");
            return "redirect:/student/dashboard";
        } catch (IllegalArgumentException e) {
            return duckForm(model, draft, studentExplanation, takeaway, e.getMessage(), bookingOpen(session));
        }
    }

    @PostMapping("/requests/duck/reply")
    public String walkItThrough(@RequestParam(required = false) String studentExplanation,
                                Principal principal, Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        User student = requireStudent(principal);
        DuckDraft draft = duckDraft(session, student);
        if (draft == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Talk it through before posting.");
            return "redirect:/student/requests/new";
        }
        if (draft.getReply() != null) {
            return duckForm(model, draft, studentExplanation, null,
                    "Soquacktic already replied once. Start over for a new note.", bookingOpen(session));
        }
        try {
            String explanation = checkedExplanation(studentExplanation);
            String reply = aiDiagnosisService.prepareFollowUp(
                    draft.getCourse(),
                    draft.getTopic(),
                    draft.getDescription(),
                    draft.getNote(),
                    explanation);
            draft.setAnswer(explanation);
            draft.setReply(reply);
            draftRepository.save(draft);
            return duckForm(model, draft, explanation, null, null, bookingOpen(session));
        } catch (IllegalArgumentException e) {
            return duckForm(model, draft, studentExplanation, null, e.getMessage(), bookingOpen(session));
        }
    }

    @PostMapping("/requests/duck/enough")
    public String enoughFromNote(@RequestParam(required = false) Long articleId,
                                 @RequestParam(required = false) String takeaway,
                                 Principal principal, RedirectAttributes redirectAttributes) {
        User student = requireStudent(principal);
        KnowledgeArticle article = articleId == null
                ? null
                : knowledgeArticleRepository.findById(articleId).orElse(null);
        if (article == null || !KnowledgeBankService.TRIAGE.equals(article.getKind())) {
            redirectAttributes.addFlashAttribute("errorMessage", "That note cannot close this.");
            return "redirect:/student/requests/new";
        }
        try {
            String cleanTakeaway = checkedTakeaway(takeaway);
            draftRepository.save(DuckDraft.clearedFromNote(
                    article.getCourse(),
                    article.getTopic(),
                    article.getNoteText(),
                    cleanTakeaway,
                    student.getId()));
            redirectAttributes.addFlashAttribute("successMessage", "Saved.");
            return "redirect:/student/dashboard";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/student/requests/new";
        }
    }

    @GetMapping("/drafts/{id}")
    public String continueDraft(@PathVariable Long id, Principal principal, Model model, HttpSession session,
                                RedirectAttributes redirectAttributes) {
        User student = requireStudent(principal);
        DuckDraft draft = draftRepository.findByIdAndStudentId(id, student.getId()).orElse(null);
        if (draft == null || !DuckDraft.TRYING.equals(draft.getOutcome())) {
            redirectAttributes.addFlashAttribute("errorMessage", "You cannot continue that one.");
            return "redirect:/student/dashboard";
        }
        session.setAttribute(DuckDraft.SESSION_KEY, draft.getId());
        session.removeAttribute(DuckDraft.BOOKING_KEY);
        return duckForm(model, draft, null, null, null, false);
    }

    @PostMapping("/requests")
    public String submitRequest(@RequestParam(required = false) String course,
                                @RequestParam(required = false) String topic,
                                @RequestParam(required = false) String shortDescription,
                                @RequestParam(required = false) String preferredMeetingMethod,
                                @RequestParam(required = false) String desiredResponseTime,
                                @RequestParam(required = false) String expectedEffort,
                                @RequestParam(required = false) Integer bountyValue,
                                @RequestParam(required = false) String studentExplanation,
                                Principal principal, Model model, HttpSession session) {
        User student = requireStudent(principal);
        DuckDraft draft = duckDraft(session, student);
        if (draft != null) {
            try {
                String explanation = checkedExplanation(studentExplanation);
                TutoringRequest request = draft.toRequest();
                RequestFields.booking(request, preferredMeetingMethod, desiredResponseTime, expectedEffort, bountyValue);
                TutoringRequest created = economyService.createRequestWithBounty(
                        student.getUsername(), request, request.getBountyValue(),
                        draft.savedNote(), explanation);
                draft.setAnswer(explanation);
                draft.setOutcome(DuckDraft.ESCALATED);
                draft.setRequestId(created.getId());
                draftRepository.save(draft);
                clearDuck(session);
                return "redirect:/student/dashboard";
            } catch (IllegalStateException | IllegalArgumentException e) {
                rememberBooking(model, preferredMeetingMethod, desiredResponseTime, expectedEffort, bountyValue);
                session.setAttribute(DuckDraft.BOOKING_KEY, Boolean.TRUE);
                return duckForm(model, draft, studentExplanation, null, e.getMessage(), true);
            }
        }
        try {
            TutoringRequest request = RequestFields.blocker(course, topic, shortDescription);
            return requestForm(model, request, "Talk it through before posting.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            return requestForm(model, draftRequest(course, topic, shortDescription), e.getMessage());
        }
    }

    @PostMapping("/drafts/{id}/takeaway")
    public String saveTakeaway(@PathVariable Long id, @RequestParam(required = false) String takeaway,
                               Principal principal, RedirectAttributes redirectAttributes) {
        User student = requireStudent(principal);
        DuckDraft draft = draftRepository.findByIdAndStudentId(id, student.getId()).orElse(null);
        if (draft == null || !DuckDraft.ESCALATED.equals(draft.getOutcome()) || draft.getRequestId() == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "That one cannot be closed yet.");
            return "redirect:/student/dashboard";
        }
        TutoringRequest request = requestRepository.findById(draft.getRequestId()).orElse(null);
        if (request == null || !student.getId().equals(request.getStudentId()) || !RESOLVED.equals(request.getStatus())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Resolve the session before writing what changed.");
            return "redirect:/student/dashboard";
        }
        try {
            draft.setTakeaway(checkedTakeaway(takeaway));
            draftRepository.save(draft);
            redirectAttributes.addFlashAttribute("successMessage", "Saved what changed.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/student/dashboard";
    }

    @GetMapping("/knowledge")
    public String showKnowledge(@RequestParam(required = false) String course,
                                @RequestParam(required = false) String topic,
                                Principal principal, Model model) {
        requireStudent(principal);
        String canonical = Courses.canonical(course);
        String query = topic == null ? "" : topic.trim();
        List<KnowledgeArticle> articles;
        if (canonical != null && !query.isEmpty()) {
            articles = knowledgeArticleRepository
                    .findByCourseIgnoreCaseAndTopicContainingIgnoreCaseOrderByTopicAsc(canonical, query);
        } else if (canonical != null) {
            articles = knowledgeArticleRepository.findByCourseIgnoreCaseOrderByTopicAsc(canonical);
        } else if (!query.isEmpty()) {
            articles = knowledgeArticleRepository.findByTopicContainingIgnoreCaseOrderByCourseAscTopicAsc(query);
        } else {
            articles = knowledgeArticleRepository.findAllByOrderByCourseAscTopicAsc();
        }
        model.addAttribute("articles", articles);
        model.addAttribute("courses", Courses.OFFERED);
        model.addAttribute("selectedCourse", canonical == null ? "" : canonical);
        model.addAttribute("topicQuery", query);
        return "student/knowledge";
    }

    @GetMapping("/dashboard")
    public String showDashboard(Model model, Principal principal) {
        User student = requireStudent(principal);
        List<DuckDraft> drafts = draftRepository.findByStudentIdOrderByCreatedAtDesc(student.getId());
        List<Long> requestIds = new ArrayList<>();
        for (DuckDraft draft : drafts) {
            if (draft.getRequestId() != null && !requestIds.contains(draft.getRequestId())) {
                requestIds.add(draft.getRequestId());
            }
        }
        Map<Long, String> requestStatuses = new HashMap<>();
        if (!requestIds.isEmpty()) {
            for (TutoringRequest request : requestRepository.findAllById(requestIds)) {
                requestStatuses.put(request.getId(), request.getStatus());
            }
        }
        model.addAttribute("drafts", drafts);
        model.addAttribute("requestStatuses", requestStatuses);
        return "student/dashboard";
    }

    @PostMapping("/requests/{id}/cancel")
    public String cancelRequest(@PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
        User student = requireStudent(principal);
        try {
            String from = economyService.cancel(student, id);
            if (CLAIMED.equals(from)) {
                redirectAttributes.addFlashAttribute("successMessage", "Session canceled and the bounty was refunded.");
            } else {
                redirectAttributes.addFlashAttribute("successMessage", "Request canceled and the bounty was refunded.");
            }
        } catch (ObjectOptimisticLockingFailureException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "This request changed. Open it and try again.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/student/dashboard";
    }

    @PostMapping("/requests/{id}/resolve")
    public String resolveRequest(@PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
        User student = requireStudent(principal);
        try {
            economyService.resolve(student, id);
            redirectAttributes.addFlashAttribute("successMessage", "Session marked resolved. The bounty was paid to your tutor.");
            return "redirect:/student/requests/" + id + "/review";
        } catch (ObjectOptimisticLockingFailureException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "This request changed. Open it and try again.");
            return "redirect:/student/dashboard";
        } catch (IllegalStateException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/student/dashboard";
        }
    }

    @GetMapping("/requests/{id}/review")
    public String showReviewForm(@PathVariable Long id, Principal principal, Model model,
                                 RedirectAttributes redirectAttributes) {
        User student = requireStudent(principal);
        TutoringRequest request = ownedRequest(id, student, redirectAttributes);
        if (request == null) {
            return "redirect:/student/dashboard";
        }
        if (!RESOLVED.equals(request.getStatus())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Resolve the session before leaving a review.");
            return "redirect:/student/dashboard";
        }
        if (request.getAssignedTutorId() == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "This request has no tutor to review.");
            return "redirect:/student/dashboard";
        }
        if (reviewRepository.findByRequestId(id).isPresent()) {
            redirectAttributes.addFlashAttribute("errorMessage", "You already reviewed this session.");
            return "redirect:/student/dashboard";
        }
        model.addAttribute("request", request);
        return "student/leave-review";
    }

    @PostMapping("/reviews")
    public String submitReview(@RequestParam Long requestId,
                               @RequestParam Integer score,
                               @RequestParam(required = false) String feedback,
                               Principal principal,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        User student = requireStudent(principal);
        TutoringRequest request = ownedRequest(requestId, student, redirectAttributes);
        if (request == null) {
            return "redirect:/student/dashboard";
        }
        if (!RESOLVED.equals(request.getStatus())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Resolve the session before leaving a review.");
            return "redirect:/student/dashboard";
        }
        if (request.getAssignedTutorId() == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "This request has no tutor to review.");
            return "redirect:/student/dashboard";
        }
        if (reviewRepository.findByRequestId(requestId).isPresent()) {
            redirectAttributes.addFlashAttribute("errorMessage", "You already reviewed this session.");
            return "redirect:/student/dashboard";
        }
        if (score == null || score < 1 || score > 5) {
            model.addAttribute("request", request);
            model.addAttribute("score", score);
            model.addAttribute("feedback", feedback);
            model.addAttribute("errorMessage", "Score must be between 1 and 5.");
            return "student/leave-review";
        }
        String trimmedFeedback = FormText.blankToNull(feedback);
        if (trimmedFeedback != null && trimmedFeedback.length() > 1000) {
            model.addAttribute("request", request);
            model.addAttribute("score", score);
            model.addAttribute("feedback", feedback);
            model.addAttribute("errorMessage", "Keep feedback under 1000 characters.");
            return "student/leave-review";
        }

        Review review = new Review();
        review.setRequestId(request.getId());
        review.setTutorId(request.getAssignedTutorId());
        review.setStudentId(student.getId());
        review.setScore(score);
        review.setFeedback(trimmedFeedback);
        try {
            economyService.saveReviewAndReward(review);
        } catch (DataIntegrityViolationException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "You already reviewed this session.");
            return "redirect:/student/dashboard";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Thanks for the review. 10 tokens were added to your balance.");
        return "redirect:/student/dashboard";
    }

    @GetMapping("/requests/{id}")
    public String showRequestDetails(@PathVariable Long id, Principal principal, Model model,
                                     RedirectAttributes redirectAttributes) {
        User student = requireStudent(principal);
        TutoringRequest request = ownedRequest(id, student, redirectAttributes);
        if (request == null) {
            return "redirect:/student/dashboard";
        }
        model.addAttribute("request", request);
        model.addAttribute("tutorName", publicNames.of(request.getAssignedTutorId()));
        model.addAttribute("alreadyReviewed", reviewRepository.findByRequestId(request.getId()).isPresent());
        model.addAttribute("events", eventRepository.findByRequestIdOrderByAtAsc(request.getId()));
        if (OPEN.equals(request.getStatus())) {
            List<MeetingOffer> offers = offerRepository.findByRequestIdOrderByOfferedTimeAsc(request.getId());
            model.addAttribute("offers", offers);
            model.addAttribute("tutorNamesById", publicNames.byId(offers.stream().map(MeetingOffer::getTutorId).toList()));
        }
        return "student/request-details";
    }

    @PostMapping("/requests/{id}/offers/{offerId}/accept")
    public String acceptOffer(@PathVariable Long id, @PathVariable Long offerId, Principal principal,
                              RedirectAttributes redirectAttributes) {
        User student = requireStudent(principal);
        try {
            meetingOfferService.accept(student, id, offerId);
            redirectAttributes.addFlashAttribute("successMessage", "Time accepted. The session is claimed.");
        } catch (ObjectOptimisticLockingFailureException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "This request was already claimed.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/student/requests/" + id;
    }

    @PostMapping("/requests/{id}/triage")
    public String closeFromNote(@PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
        User student = requireStudent(principal);
        try {
            boolean added = triageCloseService.close(student, id);
            redirectAttributes.addFlashAttribute("successMessage", added
                    ? "Request closed. Your bounty was refunded, and the note was saved to the databank."
                    : "Request closed. Your bounty was refunded.");
            return "redirect:/student/requests/" + id;
        } catch (ObjectOptimisticLockingFailureException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "This request changed. Open it and try again.");
            return "redirect:/student/requests/" + id;
        } catch (IllegalStateException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/student/requests/" + id;
        }
    }

    private String requestForm(Model model, TutoringRequest request, String errorMessage) {
        model.addAttribute("errorMessage", errorMessage);
        model.addAttribute("tutoringRequest", request);
        model.addAttribute("courses", Courses.OFFERED);
        return "student/create-request";
    }

    private String duckForm(Model model, DuckDraft draft, String explanation, String takeaway,
                            String errorMessage, boolean bookingOpen) {
        model.addAttribute("errorMessage", errorMessage);
        model.addAttribute("tutoringRequest", draft.toRequest());
        model.addAttribute("duckNote", draft.getNote());
        model.addAttribute("duckReply", draft.getReply());
        model.addAttribute("studentExplanation", explanation != null ? explanation : draft.getAnswer());
        model.addAttribute("takeaway", takeaway != null ? takeaway : draft.getTakeaway());
        model.addAttribute("duckReady", true);
        model.addAttribute("bookingOpen", bookingOpen);
        model.addAttribute("courses", Courses.OFFERED);
        return "student/create-request";
    }

    private void rememberBooking(Model model, String meetingMethod, String responseTime, String effort, Integer bounty) {
        model.addAttribute("preferredMeetingMethod", meetingMethod);
        model.addAttribute("desiredResponseTime", responseTime);
        model.addAttribute("expectedEffort", effort);
        model.addAttribute("bountyValue", bounty);
    }

    private DuckDraft duckDraft(HttpSession session, User student) {
        Object stored = session.getAttribute(DuckDraft.SESSION_KEY);
        if (!(stored instanceof Long id)) {
            return null;
        }
        return draftRepository.findByIdAndStudentId(id, student.getId()).orElse(null);
    }

    private boolean bookingOpen(HttpSession session) {
        return Boolean.TRUE.equals(session.getAttribute(DuckDraft.BOOKING_KEY));
    }

    private void clearDuck(HttpSession session) {
        session.removeAttribute(DuckDraft.SESSION_KEY);
        session.removeAttribute(DuckDraft.BOOKING_KEY);
    }

    private TutoringRequest draftRequest(String course, String topic, String description) {
        TutoringRequest request = new TutoringRequest();
        request.setCourse(course);
        request.setTopic(topic);
        request.setShortDescription(description);
        return request;
    }

    private User requireStudent(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        User student = userRepository.findByUsername(principal.getName());
        if (student == null || !"STUDENT".equals(student.getRole())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return student;
    }

    private TutoringRequest ownedRequest(Long id, User student, RedirectAttributes redirectAttributes) {
        TutoringRequest request = requestRepository.findById(id).orElse(null);
        if (request == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "That request does not exist.");
            return null;
        }
        if (!student.getId().equals(request.getStudentId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "You can only manage your own requests.");
            return null;
        }
        return request;
    }

    private static String checkedExplanation(String value) {
        return FormText.required(value, "Explain what you have already tried.", 2000,
                "Keep that explanation under 2000 characters.");
    }

    private static String checkedTakeaway(String value) {
        return FormText.required(value, "Write what you understand now.", 500);
    }
}