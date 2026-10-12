package com.rapidtutor.controller;

import com.rapidtutor.model.Courses;
import com.rapidtutor.model.Review;
import com.rapidtutor.model.TutoringRequest;
import com.rapidtutor.model.User;
import com.rapidtutor.repository.ReviewRepository;
import com.rapidtutor.repository.TutoringRequestRepository;
import com.rapidtutor.repository.UserRepository;
import com.rapidtutor.service.AiDiagnosisService;
import com.rapidtutor.service.EconomyService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;
import java.util.Objects;

@Controller
@RequestMapping("/student")
public class StudentController {

    private final EconomyService economyService;
    private final TutoringRequestRepository requestRepository;
    private final ReviewRepository reviewRepository;
    private final AiDiagnosisService aiDiagnosisService;
    private final UserRepository userRepository;

    public StudentController(EconomyService economyService, TutoringRequestRepository requestRepository,
                             ReviewRepository reviewRepository, AiDiagnosisService aiDiagnosisService,
                             UserRepository userRepository) {
        this.economyService = economyService;
        this.requestRepository = requestRepository;
        this.reviewRepository = reviewRepository;
        this.aiDiagnosisService = aiDiagnosisService;
        this.userRepository = userRepository;
    }

    @GetMapping("/requests/new")
    public String showRequestForm(Model model) {
        TutoringRequest request = new TutoringRequest();
        request.setCourse(Courses.HCDD_412);
        model.addAttribute("tutoringRequest", request);
        model.addAttribute("courses", Courses.OFFERED);
        return "student/create-request";
    }

    @PostMapping("/requests")
    public String submitRequest(@ModelAttribute TutoringRequest request, Principal principal,
                                RedirectAttributes redirectAttributes) {
        User student = requireStudent(principal);
        String course = Courses.canonical(request.getCourse());
        if (course == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Choose one of the listed courses.");
            return "redirect:/student/requests/new";
        }
        request.setCourse(course);

        int offeredBounty = request.getBountyValue() != null ? request.getBountyValue() : 0;

        try {
            TutoringRequest savedRequest = economyService.createRequestWithBounty(student.getUsername(), request, offeredBounty);
            aiDiagnosisService.generateDiagnosticScaffold(
                    savedRequest.getId(),
                    savedRequest.getCourse(),
                    savedRequest.getTopic(),
                    savedRequest.getShortDescription()
            );
            return "redirect:/student/dashboard";
        } catch (IllegalStateException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/student/requests/new";
        }
    }

    @GetMapping("/dashboard")
    public String showDashboard(Model model, Principal principal) {
        User student = requireStudent(principal);
        model.addAttribute("requests", requestRepository.findByStudentId(student.getId()));
        List<Long> reviewedRequestIds = reviewRepository.findByStudentId(student.getId()).stream()
                .map(Review::getRequestId)
                .filter(Objects::nonNull)
                .toList();
        model.addAttribute("reviewedRequestIds", reviewedRequestIds);
        return "student/dashboard";
    }

    @PostMapping("/requests/{id}/cancel")
    public String cancelRequest(@PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
        User student = requireStudent(principal);
        TutoringRequest request = ownedRequest(id, student, redirectAttributes);
        if (request == null) {
            return "redirect:/student/dashboard";
        }
        if (!"OPEN".equals(request.getStatus())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only open requests can be canceled.");
            return "redirect:/student/dashboard";
        }
        try {
            economyService.cancelAndRefund(id);
            redirectAttributes.addFlashAttribute("successMessage", "Request canceled and the bounty was refunded.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
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
        if (reviewRepository.findByRequestId(requestId).isPresent()) {
            redirectAttributes.addFlashAttribute("errorMessage", "You already reviewed this session.");
            return "redirect:/student/dashboard";
        }
        if (score == null || score < 1 || score > 5) {
            redirectAttributes.addFlashAttribute("errorMessage", "Score must be between 1 and 5.");
            return "redirect:/student/requests/" + requestId + "/review";
        }

        Review review = new Review();
        review.setRequestId(request.getId());
        review.setTutorId(request.getAssignedTutorId());
        review.setStudentId(student.getId());
        review.setScore(score);
        review.setFeedback(blankToNull(feedback));
        reviewRepository.save(review);
        redirectAttributes.addFlashAttribute("successMessage", "Thanks for the review.");
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
        return "student/request-details";
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
