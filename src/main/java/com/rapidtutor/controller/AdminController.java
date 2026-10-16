package com.rapidtutor.controller;

import com.rapidtutor.model.AccountForm;
import com.rapidtutor.model.Review;
import com.rapidtutor.model.User;
import com.rapidtutor.repository.ReviewRepository;
import com.rapidtutor.repository.TutoringRequestRepository;
import com.rapidtutor.repository.UserRepository;
import com.rapidtutor.service.AccountService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

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
        long resolved = requestRepository.countByStatus("RESOLVED");
        model.addAttribute("resolvedCount", resolved);
        model.addAttribute("resolvedWithNoteCount", requestRepository.countResolvedWithDiagnosticNote());
        model.addAttribute("courseCounts", toLabelCounts(requestRepository.countGroupedByCourse()));
        model.addAttribute("topicCounts", toLabelCounts(requestRepository.countGroupedByTopic()));
        model.addAttribute("tutorScores", tutorScores());
        model.addAttribute("scoreCounts", scoreCounts());
        model.addAttribute("recentReviews", recentReviews());
        return "admin/insights";
    }

    private void fillOverview(Model model) {
        model.addAttribute("users", userRepository.findAll());
        model.addAttribute("applicants", userRepository.findByRoleOrderByUsernameAsc("APPLICANT"));
        model.addAttribute("openCount", requestRepository.countByStatus("OPEN"));
        model.addAttribute("claimedCount", requestRepository.countByStatus("CLAIMED"));
        model.addAttribute("resolvedCount", requestRepository.countByStatus("RESOLVED"));
        model.addAttribute("canceledCount", requestRepository.countByStatus("CANCELED"));
        model.addAttribute("expiredCount", requestRepository.countByStatus("EXPIRED"));
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
        List<ReviewRow> rows = new ArrayList<>();
        for (Review review : reviewRepository.findTop20ByOrderByCreatedAtDesc()) {
            String tutor = userRepository.findById(review.getTutorId()).map(User::getPublicName).orElse("Unknown");
            rows.add(new ReviewRow(tutor, review.getScore(), review.getFeedback(), review.getCreatedAt()));
        }
        return rows;
    }

    private List<TutorScore> tutorScores() {
        List<TutorScore> scores = new ArrayList<>();
        for (Object[] row : reviewRepository.summarizeByTutor()) {
            Long tutorId = ((Number) row[0]).longValue();
            double average = ((Number) row[1]).doubleValue();
            long reviews = ((Number) row[2]).longValue();
            String username = userRepository.findById(tutorId).map(User::getPublicName).orElse("Tutor " + tutorId);
            scores.add(new TutorScore(username, average, reviews));
        }
        return scores;
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
