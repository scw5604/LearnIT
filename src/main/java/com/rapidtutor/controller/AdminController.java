package com.rapidtutor.controller;

import com.rapidtutor.model.User;
import com.rapidtutor.repository.ReviewRepository;
import com.rapidtutor.repository.TutoringRequestRepository;
import com.rapidtutor.repository.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final TutoringRequestRepository requestRepository;
    private final ReviewRepository reviewRepository;

    public AdminController(UserRepository userRepository, TutoringRequestRepository requestRepository,
                           ReviewRepository reviewRepository) {
        this.userRepository = userRepository;
        this.requestRepository = requestRepository;
        this.reviewRepository = reviewRepository;
    }

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("users", userRepository.findAll());
        model.addAttribute("openCount", requestRepository.countByStatus("OPEN"));
        model.addAttribute("claimedCount", requestRepository.countByStatus("CLAIMED"));
        model.addAttribute("resolvedCount", requestRepository.countByStatus("RESOLVED"));
        model.addAttribute("canceledCount", requestRepository.countByStatus("CANCELED"));
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
        return "admin/insights";
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

    private List<TutorScore> tutorScores() {
        List<TutorScore> scores = new ArrayList<>();
        for (Object[] row : reviewRepository.summarizeByTutor()) {
            Long tutorId = ((Number) row[0]).longValue();
            double average = ((Number) row[1]).doubleValue();
            long reviews = ((Number) row[2]).longValue();
            String username = userRepository.findById(tutorId).map(User::getUsername).orElse("Tutor " + tutorId);
            scores.add(new TutorScore(username, average, reviews));
        }
        return scores;
    }

    public record LabelCount(String label, long count) {
    }

    public record TutorScore(String username, double averageScore, long reviewCount) {
    }
}
