package com.rapidtutor.controller;

import com.rapidtutor.model.TutoringRequest;
import com.rapidtutor.model.User;
import com.rapidtutor.repository.TutoringRequestRepository;
import com.rapidtutor.repository.UserRepository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/tutor")
public class TutorController {

    private final TutoringRequestRepository requestRepository;
    private final UserRepository userRepository;

    public TutorController(TutoringRequestRepository requestRepository, UserRepository userRepository) {
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/board")
    public String showTutorBoard(Model model, Principal principal) {
        // Fallback to "tutor1" if offline testing
        String username = (principal != null) ? principal.getName() : "tutor1";
        User tutor = userRepository.findByUsername(username);

        List<TutoringRequest> allRequests = requestRepository.findAll();

        // 1. Filter tickets that are OPEN and available for anyone to claim
        List<TutoringRequest> openRequests = allRequests.stream()
                .filter(req -> "OPEN".equals(req.getStatus()))
                .collect(Collectors.toList());

        // 2. Filter tickets that are CLAIMED specifically by the logged-in tutor
        List<TutoringRequest> myClaimedRequests = allRequests.stream()
                .filter(req -> "CLAIMED".equals(req.getStatus()) && tutor.getId().equals(req.getAssignedTutorId()))
                .collect(Collectors.toList());

        model.addAttribute("requests", openRequests);
        model.addAttribute("myClaimedRequests", myClaimedRequests);

        return "tutor/tutor-board";
    }

    // Handles the atomic claim workflow guarded by JPA optimistic locking
    @PostMapping("/claim/{id}")
    public String claimRequest(@PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
        String username = (principal != null) ? principal.getName() : "tutor1";
        User tutor = userRepository.findByUsername(username);

        try {
            TutoringRequest request = requestRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid request ID"));

            if (!"OPEN".equals(request.getStatus())) {
                redirectAttributes.addFlashAttribute("errorMessage", "This request is no longer available.");
                return "redirect:/tutor/board";
            }

            request.setStatus("CLAIMED");
            request.setAssignedTutorId(tutor.getId());

            request.setClaimedTimestamp(LocalDateTime.now());

            requestRepository.save(request);
            redirectAttributes.addFlashAttribute("successMessage", "You have successfully claimed the request!");

        } catch (ObjectOptimisticLockingFailureException e) {
            // Catches the version mismatch if another tutor claimed it milliseconds prior
            redirectAttributes.addFlashAttribute("errorMessage", "Sorry, another tutor just claimed this request.");
        }

        return "redirect:/tutor/board";
    }
    // Displays the full details of a specific request
    @GetMapping("/requests/{id}")
    public String showRequestDetails(@PathVariable Long id, Model model) {
        TutoringRequest request = requestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid request ID"));

        model.addAttribute("request", request);
        return "tutor/request-details";
    }
}
