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
import com.rapidtutor.service.KnowledgeBankService;
import com.rapidtutor.service.MeetingOfferService;
import com.rapidtutor.service.RequestFields;
import com.rapidtutor.service.TriageCloseService;
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

    public StudentController(EconomyService economyService, TutoringRequestRepository requestRepository,
                             ReviewRepository reviewRepository, AiDiagnosisService aiDiagnosisService,
                             UserRepository userRepository, MeetingOfferRepository offerRepository,
                             MeetingOfferService meetingOfferService, RequestEventRepository eventRepository,
                             KnowledgeArticleRepository knowledgeArticleRepository,
                             KnowledgeBankService knowledgeBankService,
                             TriageCloseService triageCloseService,
                             DuckDraftRepository draftRepository) {
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
    }

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
            String explanation = requiredExplanation(studentExplanation);
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
            String cleanTakeaway = requiredTakeaway(takeaway);
            if (studentExplanation != null && !studentExplanation.isBlank()) {
                draft.setAnswer(requiredExplanation(studentExplanation));
            }
            knowledgeBankService.saveTriage(draft.getCourse(), draft.getTopic(), draft.savedNote());
            draft.setTakeaway(cleanTakeaway);
            draft.setOutcome(DuckDraft.SOLVED);
            draftRepository.save(draft);
            clearDuck(session);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Saved to your blockers. The note was added to the databank.");
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
            String explanation = requiredExplanation(studentExplanation);
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
                String explanation = requiredExplanation(studentExplanation);
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
            redirectAttributes.addFlashAttribute("errorMessage", "That blocker cannot be closed yet.");
            return "redirect:/student/dashboard";
        }
        TutoringRequest request = requestRepository.findById(draft.getRequestId()).orElse(null);
        if (request == null || !student.getId().equals(request.getStudentId()) || !"RESOLVED".equals(request.getStatus())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Resolve the session before writing what changed.");
            return "redirect:/student/dashboard";
        }
        try {
            draft.setTakeaway(requiredTakeaway(takeaway));
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

    @GetMapping("/requests")
    public String showRequests(Model model, Principal principal) {
        User student = requireStudent(principal);
        List<TutoringRequest> requests = requestRepository.findByStudentIdOrderByCreationTimestampDesc(student.getId());
        List<Long> openIds = new ArrayList<>();
        List<Long> tutorIds = new ArrayList<>();
        for (TutoringRequest request : requests) {
            if ("OPEN".equals(request.getStatus())) {
                openIds.add(request.getId());
            }
            if (request.getAssignedTutorId() != null && !tutorIds.contains(request.getAssignedTutorId())) {
                tutorIds.add(request.getAssignedTutorId());
            }
        }
        Map<Long, Long> offerCounts = new HashMap<>();
        if (!openIds.isEmpty()) {
            for (Object[] row : offerRepository.countGroupedByRequestId(openIds)) {
                offerCounts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
            }
        }
        Map<Long, String> namesByTutorId = namesFor(tutorIds);
        Map<Long, String> tutorNames = new HashMap<>();
        List<TutoringRequest> offeredRequests = new ArrayList<>();
        List<TutoringRequest> otherRequests = new ArrayList<>();
        for (TutoringRequest request : requests) {
            long offers = offerCounts.getOrDefault(request.getId(), 0L);
            if ("OPEN".equals(request.getStatus())) {
                offerCounts.put(request.getId(), offers);
            }
            String tutorName = request.getAssignedTutorId() == null ? null : namesByTutorId.get(request.getAssignedTutorId());
            tutorNames.put(request.getId(), tutorName);
            if ("OPEN".equals(request.getStatus()) && offers > 0) {
                offeredRequests.add(request);
            } else {
                otherRequests.add(request);
            }
        }
        model.addAttribute("requests", requests);
        model.addAttribute("offeredRequests", offeredRequests);
        model.addAttribute("otherRequests", otherRequests);
        model.addAttribute("offerCounts", offerCounts);
        model.addAttribute("tutorNames", tutorNames);
        return "student/requests";
    }

    @PostMapping("/requests/{id}/cancel")
    public String cancelRequest(@PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
        User student = requireStudent(principal);
        try {
            String from = economyService.cancel(student, id);
            if ("CLAIMED".equals(from)) {
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
        if (!"RESOLVED".equals(request.getStatus())) {
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
        if (!"RESOLVED".equals(request.getStatus())) {
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
        String trimmedFeedback = blankToNull(feedback);
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
        economyService.saveReviewAndReward(review);
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
        model.addAttribute("tutorName", tutorDisplayName(request));
        model.addAttribute("alreadyReviewed", reviewRepository.findByRequestId(request.getId()).isPresent());
        model.addAttribute("events", eventRepository.findByRequestIdOrderByAtAsc(request.getId()));
        if ("OPEN".equals(request.getStatus())) {
            List<MeetingOffer> offers = offerRepository.findByRequestIdOrderByOfferedTimeAsc(request.getId());
            model.addAttribute("offers", offers);
            model.addAttribute("tutorNamesById", publicNames(offers));
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
            triageCloseService.close(student, id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Request closed. Your bounty was refunded, and the note was saved to the databank.");
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

    private String requiredExplanation(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Explain what you have already tried.");
        }
        String trimmed = value.trim();
        if (trimmed.length() > 2000) {
            throw new IllegalArgumentException("Keep that explanation under 2000 characters.");
        }
        return trimmed;
    }

    private String tutorDisplayName(TutoringRequest request) {
        if (request.getAssignedTutorId() == null) {
            return null;
        }
        return userRepository.findById(request.getAssignedTutorId()).map(User::getPublicName).orElse(null);
    }

    private Map<Long, String> publicNames(List<MeetingOffer> offers) {
        List<Long> tutorIds = new ArrayList<>();
        for (MeetingOffer offer : offers) {
            if (offer.getTutorId() != null && !tutorIds.contains(offer.getTutorId())) {
                tutorIds.add(offer.getTutorId());
            }
        }
        return namesFor(tutorIds);
    }

    private Map<Long, String> namesFor(List<Long> tutorIds) {
        Map<Long, String> names = new HashMap<>();
        if (tutorIds.isEmpty()) {
            return names;
        }
        for (User user : userRepository.findAllById(tutorIds)) {
            names.put(user.getId(), user.getPublicName());
        }
        return names;
    }

    private TutoringRequest draftRequest(String course, String topic, String description) {
        TutoringRequest request = new TutoringRequest();
        request.setCourse(course);
        request.setTopic(topic);
        request.setShortDescription(description);
        return request;
    }

    private String requiredTakeaway(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Write what you understand now.");
        }
        String trimmed = value.trim();
        if (trimmed.length() > 500) {
            throw new IllegalArgumentException("Keep that entry under 500 characters.");
        }
        return trimmed;
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

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
