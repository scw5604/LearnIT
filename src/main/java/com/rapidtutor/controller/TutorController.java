package com.rapidtutor.controller;

import com.rapidtutor.model.TutoringRequest;
import com.rapidtutor.model.User;
import com.rapidtutor.repository.ReviewRepository;
import com.rapidtutor.repository.TutoringRequestRepository;
import com.rapidtutor.repository.UserRepository;
import com.rapidtutor.service.SubjectMatcher;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/tutor")
public class TutorController {

    private final TutoringRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;

    public TutorController(TutoringRequestRepository requestRepository, UserRepository userRepository,
                           ReviewRepository reviewRepository) {
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.reviewRepository = reviewRepository;
    }

    @GetMapping("/board")
    public String showTutorBoard(Model model, Principal principal) {
        User tutor = requireTutor(principal);

        List<TutoringRequest> openRequests = requestRepository.findByStatus("OPEN").stream()
                .filter(req -> SubjectMatcher.covers(tutor.getSubjects(), req.getCourse()))
                .toList();
        List<TutoringRequest> myClaimedRequests = requestRepository.findByStatus("CLAIMED").stream()
                .filter(req -> tutor.getId().equals(req.getAssignedTutorId()))
                .toList();

        model.addAttribute("requests", openRequests);
        model.addAttribute("myClaimedRequests", myClaimedRequests);
        model.addAttribute("tutorSubjects", tutor.getSubjects());
        model.addAttribute("myAverageScore", reviewRepository.getAverageScoreByTutorId(tutor.getId()));
        return "tutor/tutor-board";
    }

    @PostMapping("/claim/{id}")
    public String claimRequest(@PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
        User tutor = requireTutor(principal);

        try {
            TutoringRequest request = requestRepository.findById(id).orElse(null);
            if (request == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "That request does not exist.");
                return "redirect:/tutor/board";
            }
            if (!SubjectMatcher.covers(tutor.getSubjects(), request.getCourse())) {
                redirectAttributes.addFlashAttribute("errorMessage", "This request is outside your subjects.");
                return "redirect:/tutor/board";
            }
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
            redirectAttributes.addFlashAttribute("errorMessage", "Sorry, another tutor just claimed this request.");
        }

        return "redirect:/tutor/board";
    }

    @GetMapping("/requests/{id}")
    public String showRequestDetails(@PathVariable Long id, Principal principal, Model model,
                                     RedirectAttributes redirectAttributes) {
        User tutor = requireTutor(principal);
        TutoringRequest request = requestRepository.findById(id).orElse(null);
        if (request == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "That request does not exist.");
            return "redirect:/tutor/board";
        }

        boolean assignedToMe = tutor.getId().equals(request.getAssignedTutorId());
        boolean openInMySubjects = "OPEN".equals(request.getStatus())
                && SubjectMatcher.covers(tutor.getSubjects(), request.getCourse());
        if (!assignedToMe && !openInMySubjects) {
            redirectAttributes.addFlashAttribute("errorMessage", "This request is outside your subjects.");
            return "redirect:/tutor/board";
        }

        model.addAttribute("request", request);
        return "tutor/request-details";
    }

    private User requireTutor(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        User tutor = userRepository.findByUsername(principal.getName());
        if (tutor == null || !"TUTOR".equals(tutor.getRole())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return tutor;
    }
}
