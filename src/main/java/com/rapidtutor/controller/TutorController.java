package com.rapidtutor.controller;

import com.rapidtutor.model.MeetingOffer;
import com.rapidtutor.model.TutoringRequest;
import com.rapidtutor.model.User;
import com.rapidtutor.repository.MeetingOfferRepository;
import com.rapidtutor.repository.RequestEventRepository;
import com.rapidtutor.repository.ReviewRepository;
import com.rapidtutor.repository.TutoringRequestRepository;
import com.rapidtutor.repository.UserRepository;
import com.rapidtutor.service.EconomyService;
import com.rapidtutor.service.MeetingOfferService;
import com.rapidtutor.service.SubjectMatcher;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/tutor")
public class TutorController {

    private final TutoringRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;
    private final MeetingOfferRepository offerRepository;
    private final MeetingOfferService meetingOfferService;
    private final RequestEventRepository eventRepository;
    private final EconomyService economyService;

    public TutorController(TutoringRequestRepository requestRepository, UserRepository userRepository,
                           ReviewRepository reviewRepository, MeetingOfferRepository offerRepository,
                           MeetingOfferService meetingOfferService,
                           RequestEventRepository eventRepository, EconomyService economyService) {
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.reviewRepository = reviewRepository;
        this.offerRepository = offerRepository;
        this.meetingOfferService = meetingOfferService;
        this.eventRepository = eventRepository;
        this.economyService = economyService;
    }

    @GetMapping("/board")
    public String showTutorBoard(Model model, Principal principal) {
        User tutor = requireTutor(principal);

        List<TutoringRequest> openRequests = requestRepository.findByStatus("OPEN").stream()
                .filter(req -> SubjectMatcher.covers(tutor.getSubjects(), req.getCourse()))
                .toList();
        List<TutoringRequest> myClaimedRequests =
                requestRepository.findByStatusAndAssignedTutorId("CLAIMED", tutor.getId());

        Map<Long, List<MeetingOffer>> offersByRequest = new HashMap<>();
        for (MeetingOffer offer : offerRepository.findByTutorId(tutor.getId())) {
            offersByRequest.computeIfAbsent(offer.getRequestId(), id -> new ArrayList<>()).add(offer);
        }
        List<TutoringRequest> myOfferedRequests = new ArrayList<>();
        List<TutoringRequest> openWithoutMine = new ArrayList<>();
        for (TutoringRequest request : openRequests) {
            if (offersByRequest.containsKey(request.getId())) {
                myOfferedRequests.add(request);
            } else {
                openWithoutMine.add(request);
            }
        }

        model.addAttribute("requests", openWithoutMine);
        model.addAttribute("myOfferedRequests", myOfferedRequests);
        model.addAttribute("offersByRequest", offersByRequest);
        model.addAttribute("myClaimedRequests", myClaimedRequests);
        model.addAttribute("tutorSubjects", tutor.getSubjects());
        model.addAttribute("myAverageScore", reviewRepository.getAverageScoreByTutorId(tutor.getId()));
        model.addAttribute("myReviews", reviewRepository.findByTutorIdOrderByCreatedAtDesc(tutor.getId()));
        return "tutor/tutor-board";
    }

    @PostMapping("/requests/{id}/offers")
    public String offerTime(@PathVariable Long id, @RequestParam(required = false) String offeredTime,
                            @RequestParam(defaultValue = "details") String from,
                            Principal principal, RedirectAttributes redirectAttributes) {
        User tutor = requireTutor(principal);
        String redirect = "board".equals(from) ? "redirect:/tutor/board" : "redirect:/tutor/requests/" + id;
        LocalDateTime time = parseTime(offeredTime);
        if (time == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Enter a meeting time.");
            redirectAttributes.addFlashAttribute("timeError", true);
            return redirect;
        }
        try {
            meetingOfferService.saveOffer(tutor, id, time);
            redirectAttributes.addFlashAttribute("successMessage", "Time offered. The request stays open until the student accepts it.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("time")) {
                redirectAttributes.addFlashAttribute("timeError", true);
            }
            return redirect;
        }
        return redirect;
    }

    @PostMapping("/requests/{id}/offers/{offerId}/withdraw")
    public String withdrawOffer(@PathVariable Long id, @PathVariable Long offerId, Principal principal,
                                RedirectAttributes redirectAttributes) {
        User tutor = requireTutor(principal);
        try {
            meetingOfferService.withdraw(tutor, id, offerId);
            redirectAttributes.addFlashAttribute("successMessage", "Offer withdrawn.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/tutor/board";
    }

    @PostMapping("/requests/{id}/cancel")
    public String cancelClaimed(@PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
        User tutor = requireTutor(principal);
        try {
            economyService.cancelClaimedByTutor(tutor, id);
            redirectAttributes.addFlashAttribute("successMessage", "Session canceled and the bounty was refunded.");
        } catch (ObjectOptimisticLockingFailureException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "This request changed. Open it and try again.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
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
        model.addAttribute("tutorName", tutorDisplayName(request));
        model.addAttribute("events", eventRepository.findByRequestIdOrderByAtAsc(request.getId()));
        if ("OPEN".equals(request.getStatus())) {
            List<MeetingOffer> offers = offerRepository.findByRequestIdOrderByOfferedTimeAsc(request.getId());
            model.addAttribute("offers", offers);
            model.addAttribute("tutorNamesById", publicNames(offers));
        }
        return "tutor/request-details";
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
        Map<Long, String> names = new HashMap<>();
        if (tutorIds.isEmpty()) {
            return names;
        }
        for (User user : userRepository.findAllById(tutorIds)) {
            names.put(user.getId(), user.getPublicName());
        }
        return names;
    }

    private LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeException e) {
            return null;
        }
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
