package com.rapidtutor.controller;

import com.rapidtutor.model.AccountForm;
import com.rapidtutor.model.Review;
import com.rapidtutor.model.User;
import com.rapidtutor.repository.ReviewRepository;
import com.rapidtutor.repository.TutoringRequestRepository;
import com.rapidtutor.repository.UserRepository;
import com.rapidtutor.service.AccountService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final TutoringRequestRepository requestRepository;
    private final ReviewRepository reviewRepository;
    private final AccountService accountService;

    public AdminController(UserRepository userRepository, TutoringRequestRepository requestRepository,
                           ReviewRepository reviewRepository, AccountService accountService) {
        this.userRepository = userRepository;
        this.requestRepository = requestRepository;
        this.reviewRepository = reviewRepository;
        this.accountService = accountService;
    }

    @GetMapping
    public String dashboard(Model model) {
        fillOverview(model);
        if (!model.containsAttribute("accountForm")) {
            model.addAttribute("accountForm", new AccountForm());
        }
        return "admin/dashboard";
    }

    @PostMapping("/applicants/{id}/approve")
    public String approve(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            accountService.approveTutor(id);
            redirectAttributes.addFlashAttribute("successMessage", "Tutor approved.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin";
    }

    @PostMapping("/applicants/{id}/decline")
    public String decline(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            accountService.declineTutor(id);
            redirectAttributes.addFlashAttribute("successMessage", "Application declined.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin";
    }

    @PostMapping("/administrators")
    public String createAdministrator(@ModelAttribute AccountForm accountForm, Model model) {
        try {
            AccountService.CreatedAccount created = accountService.createAdministrator(
                    accountForm.getDisplayName(), accountForm.getEmail(), accountForm.getUsername(),
                    accountForm.getPassword(), accountForm.getConfirmPassword());
            model.addAttribute("successMessage",
                    "Administrator " + created.user().getUsername() + " was created. Recovery code: "
                            + created.recoveryCode() + ". Share the temporary password and this code. It will not be shown again.");
            model.addAttribute("accountForm", new AccountForm());
        } catch (DataIntegrityViolationException e) {
            accountForm.setPassword(null);
            accountForm.setConfirmPassword(null);
            model.addAttribute("accountForm", accountForm);
            model.addAttribute("errorMessage", duplicateAccountMessage(accountForm));
        } catch (IllegalArgumentException e) {
            accountForm.setPassword(null);
            accountForm.setConfirmPassword(null);
            model.addAttribute("accountForm", accountForm);
            model.addAttribute("errorMessage", e.getMessage());
        }
        fillOverview(model);
        return "admin/dashboard";
    }

    @GetMapping("/insights")
    public String insights(Model model) {
        Map<String, Long> counts = statusCounts();
        model.addAttribute("resolvedCount", countOf(counts, "RESOLVED"));
        model.addAttribute("resolvedWithNoteCount", requestRepository.countResolvedWithDiagnosticNote());
        List<LabelCount> courseCounts = toLabelCounts(requestRepository.countGroupedByCourse());
        List<LabelCount> topicCounts = toLabelCounts(requestRepository.countGroupedByTopic(PageRequest.of(0, 10)));
        model.addAttribute("courseCounts", courseCounts);
        model.addAttribute("topicCounts", topicCounts);
        model.addAttribute("courseMax", maxCount(courseCounts));
        model.addAttribute("topicMax", maxCount(topicCounts));
        model.addAttribute("tutorScores", tutorScores());
        model.addAttribute("scoreCounts", scoreCounts());
        model.addAttribute("recentReviews", recentReviews());
        return "admin/insights";
    }

    private void fillOverview(Model model) {
        Map<String, Long> counts = statusCounts();
        model.addAttribute("users", userRepository.findAll());
        model.addAttribute("applicants", userRepository.findByRoleOrderByUsernameAsc("APPLICANT"));
        model.addAttribute("openCount", countOf(counts, "OPEN"));
        model.addAttribute("claimedCount", countOf(counts, "CLAIMED"));
        model.addAttribute("resolvedCount", countOf(counts, "RESOLVED"));
        model.addAttribute("canceledCount", countOf(counts, "CANCELED"));
        model.addAttribute("expiredCount", countOf(counts, "EXPIRED"));
    }

    private Map<String, Long> statusCounts() {
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : requestRepository.countGroupedByStatus()) {
            if (row[0] != null) {
                counts.put(row[0].toString(), ((Number) row[1]).longValue());
            }
        }
        return counts;
    }

    private long countOf(Map<String, Long> counts, String status) {
        return counts.getOrDefault(status, 0L);
    }

    private String duplicateAccountMessage(AccountForm accountForm) {
        String username = accountForm.getUsername() == null ? "" : accountForm.getUsername().trim();
        if (userRepository.findByUsernameIgnoreCase(username) != null) {
            return "That username is already taken.";
        }
        return "That email is already registered.";
    }

    private long maxCount(List<LabelCount> counts) {
        long max = 0;
        for (LabelCount count : counts) {
            if (count.count() > max) {
                max = count.count();
            }
        }
        return max;
    }

    private List<LabelCount> toLabelCounts(List<Object[]> rows) {
        List<LabelCount> counts = new ArrayList<>();
        for (Object[] row : rows) {
            String label = row[0] == null ? "(none)" : row[0].toString();
            long count = ((Number) row[1]).longValue();
            counts.add(new LabelCount(label, count));
        }
        return counts;
    }

    private List<ScoreCount> scoreCounts() {
        long[] counts = new long[6];
        for (Object[] row : reviewRepository.countByScore()) {
            int score = ((Number) row[0]).intValue();
            if (score >= 1 && score <= 5) {
                counts[score] = ((Number) row[1]).longValue();
            }
        }
        List<ScoreCount> scores = new ArrayList<>();
        for (int score = 5; score >= 1; score--) {
            scores.add(new ScoreCount(score, counts[score]));
        }
        return scores;
    }

    private List<ReviewRow> recentReviews() {
        List<Review> reviews = reviewRepository.findTop20ByOrderByCreatedAtDesc();
        Map<Long, String> names = tutorNames(reviews.stream().map(Review::getTutorId).toList());
        List<ReviewRow> rows = new ArrayList<>();
        for (Review review : reviews) {
            String tutor = names.getOrDefault(review.getTutorId(), "Unknown");
            rows.add(new ReviewRow(tutor, review.getScore(), review.getFeedback(), review.getCreatedAt()));
        }
        return rows;
    }

    private List<TutorScore> tutorScores() {
        List<Object[]> rows = reviewRepository.summarizeByTutor();
        List<Long> tutorIds = new ArrayList<>();
        for (Object[] row : rows) {
            tutorIds.add(((Number) row[0]).longValue());
        }
        Map<Long, String> names = tutorNames(tutorIds);
        List<TutorScore> scores = new ArrayList<>();
        for (Object[] row : rows) {
            Long tutorId = ((Number) row[0]).longValue();
            double average = ((Number) row[1]).doubleValue();
            long reviews = ((Number) row[2]).longValue();
            scores.add(new TutorScore(names.getOrDefault(tutorId, "Tutor " + tutorId), average, reviews));
        }
        return scores;
    }

    private Map<Long, String> tutorNames(List<Long> tutorIds) {
        Map<Long, String> names = new HashMap<>();
        if (tutorIds.isEmpty()) {
            return names;
        }
        for (User user : userRepository.findAllById(tutorIds)) {
            names.put(user.getId(), user.getPublicName());
        }
        return names;
    }

    public record LabelCount(String label, long count) {
    }

    public record TutorScore(String username, double averageScore, long reviewCount) {
    }

    public record ScoreCount(int score, long count) {
    }

    public record ReviewRow(String tutorName, int score, String feedback, java.time.LocalDateTime createdAt) {
    }
}
