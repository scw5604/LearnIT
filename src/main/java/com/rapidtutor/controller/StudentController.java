package com.rapidtutor.controller;

import com.rapidtutor.model.Review;
import com.rapidtutor.model.TutoringRequest;
import com.rapidtutor.model.User;
import com.rapidtutor.repository.ReviewRepository;
import com.rapidtutor.repository.TutoringRequestRepository;
import com.rapidtutor.repository.UserRepository;
import com.rapidtutor.service.AiDiagnosisService;
import com.rapidtutor.service.EconomyService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

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
        request.setCourse("HCDD 412");
        model.addAttribute("tutoringRequest", request);
        return "student/create-request";
    }

    @PostMapping("/requests")
    public String submitRequest(@ModelAttribute TutoringRequest request, Principal principal,
                                RedirectAttributes redirectAttributes) {
        String username = (principal != null) ? principal.getName() : "student1";
        int offeredBounty = request.getBountyValue() != null ? request.getBountyValue() : 0;

        try {
            TutoringRequest savedRequest = economyService.createRequestWithBounty(username, request, offeredBounty);
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
        String username = (principal != null) ? principal.getName() : "student1";
        User student = userRepository.findByUsername(username);
        model.addAttribute("requests", requestRepository.findByStudentId(student.getId()));
        return "student/dashboard";
    }

    @PostMapping("/requests/{id}/cancel")
    public String cancelRequest(@PathVariable Long id) {
        TutoringRequest request = requestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid request ID"));
        if ("OPEN".equals(request.getStatus())) {
            economyService.cancelAndRefund(id);
        }
        return "redirect:/student/dashboard";
    }

    @PostMapping("/requests/{id}/resolve")
    public String resolveRequest(@PathVariable Long id) {
        TutoringRequest request = requestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid request ID"));
        if ("CLAIMED".equals(request.getStatus())) {
            economyService.resolveAndPayout(id, request.getAssignedTutorId());
        }
        return "redirect:/student/requests/" + id + "/review";
    }

    @GetMapping("/requests/{id}/review")
    public String showReviewForm(@PathVariable Long id, Model model) {
        TutoringRequest request = requestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid request ID"));
        if (!"RESOLVED".equalsIgnoreCase(request.getStatus())) {
            return "redirect:/student/dashboard";
        }
        Review review = new Review();
        review.setTutorId(request.getAssignedTutorId());
        review.setStudentId(request.getStudentId());
        model.addAttribute("review", review);
        return "student/leave-review";
    }

    @PostMapping("/reviews")
    public String submitReview(@ModelAttribute Review review) {
        reviewRepository.save(review);
        return "redirect:/student/dashboard";
    }

    @GetMapping("/requests/{id}")
    public String showRequestDetails(@PathVariable Long id, Model model) {
        TutoringRequest request = requestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid request ID"));
        model.addAttribute("request", request);
        return "student/request-details";
    }
}
