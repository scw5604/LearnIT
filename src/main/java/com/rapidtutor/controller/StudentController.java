package com.rapidtutor.controller;

import com.rapidtutor.model.Review;
import com.rapidtutor.model.TutoringRequest;
import com.rapidtutor.repository.ReviewRepository;
import com.rapidtutor.repository.TutoringRequestRepository;
import com.rapidtutor.service.AiDiagnosisService;
import com.rapidtutor.service.EconomyService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import com.rapidtutor.model.User;
import com.rapidtutor.repository.UserRepository;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/student")
public class StudentController {

    private final EconomyService economyService;
    private final TutoringRequestRepository requestRepository;
    private final ReviewRepository reviewRepository;
    private final AiDiagnosisService aiDiagnosisService;
    private final UserRepository userRepository;


    public StudentController(EconomyService economyService, TutoringRequestRepository requestRepository, ReviewRepository reviewRepository, AiDiagnosisService aiDiagnosisService, UserRepository userRepository) {
        this.economyService = economyService;
        this.requestRepository = requestRepository;
        this.reviewRepository = reviewRepository;
        this.aiDiagnosisService = aiDiagnosisService;
        this.userRepository = userRepository;
    }

    // Serves the Thymeleaf form
    @GetMapping("/requests/new")
    public String showRequestForm(Model model) {
        TutoringRequest request = new TutoringRequest();
        request.setCourse("HCDD 412");
        model.addAttribute("tutoringRequest", request);
        return "student/create-request";
    }

    // Processes the form submission
    @PostMapping("/requests")
    public String submitRequest(@ModelAttribute TutoringRequest request, Principal principal, RedirectAttributes redirectAttributes) {
        String username = (principal != null) ? principal.getName() : "student1";
        int offeredBounty = request.getBountyValue() != null ? request.getBountyValue() : 0;

        try {
            // Attempt to deduct tokens and save the ticket
            TutoringRequest savedRequest = economyService.createRequestWithBounty(username, request, offeredBounty);

            // Trigger AI Pre-Session Diagnosis asynchronously[cite: 1]
            aiDiagnosisService.generateDiagnosticScaffold(
                    savedRequest.getId(),
                    savedRequest.getCourse(),
                    savedRequest.getTopic(),
                    savedRequest.getShortDescription()
            );

            return "redirect:/student/dashboard";

        } catch (IllegalStateException | IllegalArgumentException e) {
            // Catch the insufficient balance (or invalid user) error and flash it to the UI
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/student/requests/new";
        }
    }
    @GetMapping("/dashboard")
    public String showDashboard(Model model, Principal principal) {
        String username = (principal != null) ? principal.getName() : "student1";

        // Look up the User object to get their numeric ID
        User student = userRepository.findByUsername(username);

        // Fetch ONLY the tickets matching this specific student's ID
        model.addAttribute("requests", requestRepository.findByStudentId(student.getId()));
        return "student/dashboard";
    }
    // Cancels an OPEN request
    @PostMapping("/requests/{id}/cancel")
    public String cancelRequest(@PathVariable Long id) {
        TutoringRequest request = requestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid request ID"));

        // Only OPEN requests can be canceled
        if ("OPEN".equals(request.getStatus())) {
            economyService.cancelAndRefund(id);
        }
        return "redirect:/student/dashboard";
    }

    // Resolves a CLAIMED request
    @PostMapping("/requests/{id}/resolve")
    public String resolveRequest(@PathVariable Long id) {
        TutoringRequest request = requestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid request ID"));

        // Only CLAIMED requests can be marked as resolved
        if ("CLAIMED".equals(request.getStatus())) {
            economyService.resolveAndPayout(id, request.getAssignedTutorId());
        }
        return "redirect:/student/requests/" + id + "/review";
    }
    // Displays the review form for a resolved ticket
    @GetMapping("/requests/{id}/review")
    public String showReviewForm(@PathVariable Long id, Model model) {
        TutoringRequest request = requestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid request ID"));

        if (!"RESOLVED".equalsIgnoreCase(request.getStatus())) {
            //return "redirect:/student/dashboard";
            return "redirect:/student/dashboard";
        }

        Review review = new Review();
        review.setTutorId(request.getAssignedTutorId());
        review.setStudentId(request.getStudentId());

        model.addAttribute("review", review);
        return "student/leave-review";
    }

    // Saves the pedagogical review to the H2 database
    @PostMapping("/reviews")
    public String submitReview(@ModelAttribute Review review) {
        reviewRepository.save(review);
        return "redirect:/student/dashboard";
    }

    // Displays the full details and Socratic triage notes for a student's request
    @GetMapping("/requests/{id}")
    public String showRequestDetails(@PathVariable Long id, Model model) {
        TutoringRequest request = requestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid request ID"));

        model.addAttribute("request", request);
        return "student/request-details";
    }
}
