package com.rapidtutor.controller;

import com.rapidtutor.model.Courses;
import com.rapidtutor.model.DuckDraft;
import com.rapidtutor.model.KnowledgeArticle;
import com.rapidtutor.model.MeetingOffer;
import com.rapidtutor.model.Review;
import com.rapidtutor.model.TutoringRequest;
import com.rapidtutor.model.User;
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
import com.rapidtutor.service.NotificationService;
import com.rapidtutor.service.RequestFields;
import com.rapidtutor.service.RequestLifecycle;
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
    private final RequestLifecycle requestLifecycle;
    private final RequestEventRepository eventRepository;
    private final NotificationService notificationService;
    private final KnowledgeArticleRepository knowledgeArticleRepository;
    private final KnowledgeBankService knowledgeBankService;
    private final TriageCloseService triageCloseService;

    public StudentController(EconomyService economyService, TutoringRequestRepository requestRepository,
                             ReviewRepository reviewRepository, AiDiagnosisService aiDiagnosisService,
                             UserRepository userRepository, MeetingOfferRepository offerRepository,
                             MeetingOfferService meetingOfferService,
                             RequestLifecycle requestLifecycle, RequestEventRepository eventRepository,
                             NotificationService notificationService,
                             KnowledgeArticleRepository knowledgeArticleRepository,
                             KnowledgeBankService knowledgeBankService,
                             TriageCloseService triageCloseService) {
        this.economyService = economyService;
        this.requestRepository = requestRepository;
        this.reviewRepository = reviewRepository;
        this.aiDiagnosisService = aiDiagnosisService;
        this.userRepository = userRepository;
        this.offerRepository = offerRepository;
        this.meetingOfferService = meetingOfferService;
        this.requestLifecycle = requestLifecycle;
        this.eventRepository = eventRepository;
        this.notificationService = notificationService;
        this.knowledgeArticleRepository = knowledgeArticleRepository;
        this.knowledgeBankService = knowledgeBankService;
        this.triageCloseService = triageCloseService;
    }

    @GetMapping("/requests/new")
    public String showRequestForm(Model model, HttpSession session) {
        session.removeAttribute(DuckDraft.SESSION_KEY);
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
                                @RequestParam(required = false) String preferredMeetingMethod,
                                @RequestParam(required = false) String desiredResponseTime,
                                @RequestParam(required = false) String expectedEffort,
                                @RequestParam(required = false) Integer bountyValue,
                                Principal principal, Model model, HttpSession session) {
        requireStudent(principal);
        try {
            TutoringRequest request = RequestFields.copy(course, topic, shortDescription, preferredMeetingMethod,
                    desiredResponseTime, expectedEffort, bountyValue);
            String note = aiDiagnosisService.prepareNote(request.getCourse(), request.getTopic(), request.getShortDescription());
            DuckDraft draft = DuckDraft.from(request, note);
            session.setAttribute(DuckDraft.SESSION_KEY, draft);
            return duckForm(model, draft, null, null);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return requestForm(model, draftRequest(course, topic, shortDescription, preferredMeetingMethod,
                    desiredResponseTime, expectedEffort, bountyValue), e.getMessage());
        }
    }

    @PostMapping("/requests/duck/solved")
    public String figuredItOut(Principal principal, HttpSession session, RedirectAttributes redirectAttributes) {
        requireStudent(principal);
        DuckDraft draft = duckDraft(session);
        if (draft == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Talk it through before posting.");
            return "redirect:/student/requests/new";
        }
        knowledgeBankService.saveTriage(draft.getCourse(), draft.getTopic(), draft.savedNote());
        session.removeAttribute(DuckDraft.SESSION_KEY);
        redirectAttributes.addFlashAttribute("successMessage", "Saved to the databank. No request was posted.");
        return "redirect:/student/knowledge";
    }

    @PostMapping("/requests/duck/reply")
    public String walkItThrough(@RequestParam(required = false) String studentExplanation,
                                Principal principal, Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        requireStudent(principal);
        DuckDraft draft = duckDraft(session);
        if (draft == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Talk it through before posting.");
            return "redirect:/student/requests/new";
        }
        try {
            String explanation = requiredExplanation(studentExplanation);
            String reply = aiDiagnosisService.prepareFollowUp(
                    draft.getCourse(),
                    draft.getTopic(),
                    draft.getShortDescription(),
                    draft.getNote(),
                    explanation);
            draft.setReply(reply);
            session.setAttribute(DuckDraft.SESSION_KEY, draft);
            return duckForm(model, draft, explanation, null);
        } catch (IllegalArgumentException e) {
            return duckForm(model, draft, studentExplanation, e.getMessage());
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
        DuckDraft draft = duckDraft(session);
        if (draft != null) {
            try {
                String explanation = requiredExplanation(studentExplanation);
                TutoringRequest savedRequest = economyService.createRequestWithBounty(
                        student.getUsername(), draft.toRequest(), draft.getBountyValue());
                savedRequest.setAiDiagnosticNote(draft.savedNote());
                savedRequest.setStudentExplanation(explanation);
                savedRequest = requestRepository.save(savedRequest);
                requestLifecycle.record(savedRequest.getId(), null, "OPEN", student.getUsername());
                if (savedRequest.getAiDiagnosticNote() == null || savedRequest.getAiDiagnosticNote().isBlank()) {
                    aiDiagnosisService.generateDiagnosticScaffold(
                            savedRequest.getId(),
                            savedRequest.getCourse(),
                            savedRequest.getTopic(),
                            savedRequest.getShortDescription());
                }
                session.removeAttribute(DuckDraft.SESSION_KEY);
                return "redirect:/student/dashboard";
            } catch (IllegalStateException | IllegalArgumentException e) {
                return duckForm(model, draft, studentExplanation, e.getMessage());
            }
        }
        try {
            TutoringRequest request = RequestFields.copy(course, topic, shortDescription, preferredMeetingMethod,
                    desiredResponseTime, expectedEffort, bountyValue);
            return requestForm(model, request, "Talk it through before posting.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            return requestForm(model, draftRequest(course, topic, shortDescription, preferredMeetingMethod,
                    desiredResponseTime, expectedEffort, bountyValue), e.getMessage());
        }
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
        List<TutoringRequest> requests = requestRepository.findByStudentId(student.getId());
        Map<Long, Long> offerCounts = new HashMap<>();
        Map<Long, String> tutorNames = new HashMap<>();
        List<TutoringRequest> offeredRequests = new ArrayList<>();
        List<TutoringRequest> otherRequests = new ArrayList<>();
        for (TutoringRequest request : requests) {
            long offers = 0;
            if ("OPEN".equals(request.getStatus())) {
                offers = offerRepository.countByRequestId(request.getId());
                offerCounts.put(request.getId(), offers);
            }
            tutorNames.put(request.getId(), tutorDisplayName(request));
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
        return "student/dashboard";
    }

    @PostMapping("/requests/{id}/cancel")
    public String cancelRequest(@PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
        User student = requireStudent(principal);
        TutoringRequest request = ownedRequest(id, student, redirectAttributes);
        if (request == null) {
            return "redirect:/student/dashboard";
        }
        if ("OPEN".equals(request.getStatus())) {
            try {
                economyService.cancelAndRefund(id);
                meetingOfferService.deleteOffers(id);
                requestLifecycle.record(id, "OPEN", "CANCELED", student.getUsername());
                redirectAttributes.addFlashAttribute("successMessage", "Request canceled and the bounty was refunded.");
            } catch (IllegalStateException | IllegalArgumentException e) {
                redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            }
            return "redirect:/student/dashboard";
        }
        if ("CLAIMED".equals(request.getStatus())) {
            Long tutorId = request.getAssignedTutorId();
            try {
                economyService.cancelClaimed(id);
                requestLifecycle.record(id, "CLAIMED", "CANCELED", student.getUsername());
                notificationService.notify(tutorId, student.getPublicName() + " canceled the session.", "/tutor/board");
                redirectAttributes.addFlashAttribute("successMessage", "Session canceled and the bounty was refunded.");
            } catch (IllegalStateException | IllegalArgumentException e) {
                redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            }
            return "redirect:/student/dashboard";
        }
        redirectAttributes.addFlashAttribute("errorMessage", "Only an open or claimed session can be canceled.");
        return "redirect:/student/dashboard";
    }

    @PostMapping("/requests/{id}/resolve")
    public String resolveRequest(@PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
        User student = requireStudent(principal);
        TutoringRequest request = ownedRequest(id, student, redirectAttributes);
        if (request == null) {
            return "redirect:/student/dashboard";
        }
        if (!"CLAIMED".equals(request.getStatus())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only claimed requests can be resolved.");
            return "redirect:/student/dashboard";
        }
        try {
            economyService.resolveAndPayout(id, request.getAssignedTutorId());
            requestLifecycle.record(id, "CLAIMED", "RESOLVED", student.getUsername());
            redirectAttributes.addFlashAttribute("successMessage", "Session marked resolved. The bounty was paid to your tutor.");
            return "redirect:/student/requests/" + id + "/review";
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

        Review review = new Review();
        review.setRequestId(request.getId());
        review.setTutorId(request.getAssignedTutorId());
        review.setStudentId(student.getId());
        review.setScore(score);
        review.setFeedback(blankToNull(feedback));
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

    private String duckForm(Model model, DuckDraft draft, String explanation, String errorMessage) {
        model.addAttribute("errorMessage", errorMessage);
        model.addAttribute("tutoringRequest", draft.toRequest());
        model.addAttribute("duckNote", draft.getNote());
        model.addAttribute("duckReply", draft.getReply());
        model.addAttribute("studentExplanation", explanation);
        model.addAttribute("duckReady", true);
        model.addAttribute("courses", Courses.OFFERED);
        return "student/create-request";
    }

    private DuckDraft duckDraft(HttpSession session) {
        Object stored = session.getAttribute(DuckDraft.SESSION_KEY);
        if (stored instanceof DuckDraft draft) {
            return draft;
        }
        return null;
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
        Map<Long, String> names = new HashMap<>();
        for (MeetingOffer offer : offers) {
            if (offer.getTutorId() != null && !names.containsKey(offer.getTutorId())) {
                userRepository.findById(offer.getTutorId())
                        .ifPresent(user -> names.put(user.getId(), user.getPublicName()));
            }
        }
        return names;
    }

    private TutoringRequest draftRequest(String course, String topic, String description, String meetingMethod,
                                         String responseTime, String effort, Integer bounty) {
        TutoringRequest request = new TutoringRequest();
        request.setCourse(course);
        request.setTopic(topic);
        request.setShortDescription(description);
        request.setPreferredMeetingMethod(meetingMethod);
        request.setDesiredResponseTime(responseTime);
        request.setExpectedEffort(effort);
        request.setBountyValue(bounty == null ? 0 : bounty);
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

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
