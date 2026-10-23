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
import com.rapidtutor.service.NotificationService;
import com.rapidtutor.service.RequestLifecycle;
import com.rapidtutor.service.SubjectMatcher;
import org.springframework.http.HttpStatus;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final RequestLifecycle requestLifecycle;
    private final NotificationService notificationService;

    public TutorController(TutoringRequestRepository requestRepository, UserRepository userRepository,
                           ReviewRepository reviewRepository, MeetingOfferRepository offerRepository,
                           MeetingOfferService meetingOfferService,
                           RequestEventRepository eventRepository, EconomyService economyService,
                           RequestLifecycle requestLifecycle, NotificationService notificationService) {
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.reviewRepository = reviewRepository;
        this.offerRepository = offerRepository;
        this.meetingOfferService = meetingOfferService;
        this.eventRepository = eventRepository;
        this.economyService = economyService;
        this.requestLifecycle = requestLifecycle;
        this.notificationService = notificationService;
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

        Set<Long> myOfferRequestIds = new HashSet<>();
        for (MeetingOffer offer : offerRepository.findByTutorId(tutor.getId())) {
            myOfferRequestIds.add(offer.getRequestId());
        }
        List<TutoringRequest> myOfferedRequests = new ArrayList<>();
        List<TutoringRequest> openWithoutMine = new ArrayList<>();
        for (TutoringRequest request : openRequests) {
            if (myOfferRequestIds.contains(request.getId())) {
                myOfferedRequests.add(request);
            } else {
                openWithoutMine.add(request);
            }
        }

        Map<Long, List<MeetingOffer>> offersByRequest = new HashMap<>();
        List<MeetingOffer> allOffers = new ArrayList<>();
        for (TutoringRequest request : openRequests) {
            List<MeetingOffer> offers = offerRepository.findByRequestIdOrderByOfferedTimeAsc(request.getId());
            offersByRequest.put(request.getId(), offers);
            allOffers.addAll(offers);
        }

        model.addAttribute("requests", openWithoutMine);
        model.addAttribute("myOfferedRequests", myOfferedRequests);
        model.addAttribute("offersByRequest", offersByRequest);
        model.addAttribute("tutorNamesById", publicNames(allOffers));
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
        TutoringRequest request = requestRepository.findById(id).orElse(null);
        if (request == null || !tutor.getId().equals(request.getAssignedTutorId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "You can only cancel a session assigned to you.");
            return "redirect:/tutor/board";
        }
        try {
            Long studentId = request.getStudentId();
            economyService.cancelClaimed(id);
            requestLifecycle.record(id, "CLAIMED", "CANCELED", tutor.getUsername());
            notificationService.notify(studentId, tutor.getPublicName() + " canceled the session.", "/student/dashboard");
            redirectAttributes.addFlashAttribute("successMessage", "Session canceled and the bounty was refunded.");
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
        Map<Long, String> names = new HashMap<>();
        for (MeetingOffer offer : offers) {
            if (offer.getTutorId() != null && !names.containsKey(offer.getTutorId())) {
                userRepository.findById(offer.getTutorId())
                        .ifPresent(user -> names.put(user.getId(), user.getPublicName()));
            }
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
