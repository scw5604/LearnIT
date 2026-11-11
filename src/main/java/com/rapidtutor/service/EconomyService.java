package com.rapidtutor.service;

import com.rapidtutor.model.MeetingOffer;
import com.rapidtutor.model.Review;
import com.rapidtutor.model.TutoringRequest;
import com.rapidtutor.model.User;
import com.rapidtutor.repository.MeetingOfferRepository;
import com.rapidtutor.repository.ReviewRepository;
import com.rapidtutor.repository.TutoringRequestRepository;
import com.rapidtutor.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.OptimisticLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static com.rapidtutor.model.RequestStatus.CANCELED;
import static com.rapidtutor.model.RequestStatus.CLAIMED;
import static com.rapidtutor.model.RequestStatus.EXPIRED;
import static com.rapidtutor.model.RequestStatus.OPEN;
import static com.rapidtutor.model.RequestStatus.RESOLVED;

@Service
public class EconomyService {

    static final int REVIEW_REWARD = 10;
    static final int MAX_OPEN_REQUESTS = 3;

    private final UserRepository userRepository;
    private final TutoringRequestRepository requestRepository;
    private final ReviewRepository reviewRepository;
    private final MeetingOfferRepository offerRepository;
    private final RequestLifecycle lifecycle;
    private final NotificationService notificationService;
    private final EntityManager entityManager;
    private final Clock clock;

    public EconomyService(UserRepository userRepository, TutoringRequestRepository requestRepository,
                          ReviewRepository reviewRepository, MeetingOfferRepository offerRepository,
                          RequestLifecycle lifecycle, NotificationService notificationService,
                          EntityManager entityManager, Clock clock) {
        this.userRepository = userRepository;
        this.requestRepository = requestRepository;
        this.reviewRepository = reviewRepository;
        this.offerRepository = offerRepository;
        this.lifecycle = lifecycle;
        this.notificationService = notificationService;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    /** Deducts the bounty, stores the note and explanation, and records the open event. */
    @Transactional
    public TutoringRequest createRequestWithBounty(String username, TutoringRequest request, Integer offeredBounty,
                                                    String note, String explanation) {
        if (offeredBounty == null || offeredBounty < 0 || offeredBounty > RequestFields.MAX_BOUNTY) {
            throw new IllegalArgumentException("Bounty must be between 0 and " + RequestFields.MAX_BOUNTY + " tokens.");
        }

        User student = userRepository.findByUsername(username);

        if (student == null || !"STUDENT".equals(student.getRole())) {
            throw new IllegalArgumentException("Invalid student account.");
        }

        int currentBalance = student.getTokenBalance() != null ? student.getTokenBalance() : 0;
        if (currentBalance < offeredBounty) {
            throw new IllegalStateException("Insufficient token balance to post this bounty.");
        }
        if (requestRepository.countByStudentIdAndStatus(student.getId(), OPEN) >= MAX_OPEN_REQUESTS) {
            throw new IllegalStateException("You already have " + MAX_OPEN_REQUESTS + " open requests.");
        }

        student.setTokenBalance(currentBalance - offeredBounty);
        saveUser(student);

        request.setStudentId(student.getId());
        request.setBountyValue(offeredBounty);
        request.setAssignedTutorId(null);
        request.setClaimedTimestamp(null);
        request.setResolvedTimestamp(null);
        request.setCanceledTimestamp(null);
        request.setOfferedMeetingTime(null);
        request.setAiDiagnosticNote(note);
        request.setStudentExplanation(explanation);
        request.setCreationTimestamp(now());
        request.setStatus(OPEN);
        TutoringRequest saved = requestRepository.save(request);
        lifecycle.record(saved.getId(), null, OPEN, username);
        return saved;
    }

    /**
     * Cancels an open or claimed request owned by the student.
     * Returns the status it left, so the page can keep its current message.
     */
    @Transactional
    public String cancel(User student, Long requestId) {
        TutoringRequest request = owned(student, requestId);
        if (OPEN.equals(request.getStatus())) {
            List<MeetingOffer> offers = offerRepository.findByRequestIdOrderByOfferedTimeAsc(requestId);
            cancelAndRefund(requestId);
            offerRepository.deleteByRequestId(requestId);
            for (MeetingOffer offer : offers) {
                notificationService.notify(offer.getTutorId(),
                        student.getPublicName() + " canceled the request.",
                        "/tutor/board");
            }
            lifecycle.record(requestId, OPEN, CANCELED, student.getUsername());
            return OPEN;
        }
        if (CLAIMED.equals(request.getStatus())) {
            Long tutorId = request.getAssignedTutorId();
            cancelClaimed(requestId);
            lifecycle.record(requestId, CLAIMED, CANCELED, student.getUsername());
            notificationService.notify(tutorId, student.getPublicName() + " canceled the session.", "/tutor/board");
            return CLAIMED;
        }
        throw new IllegalStateException("Only an open or claimed session can be canceled.");
    }

    /** Refunds a claimed session when the assigned tutor cancels it, and tells the student. */
    @Transactional
    public void cancelClaimedByTutor(User tutor, Long requestId) {
        TutoringRequest request = requestRepository.findById(requestId).orElse(null);
        if (request == null || tutor == null || !tutor.getId().equals(request.getAssignedTutorId())) {
            throw new IllegalArgumentException("You can only cancel a session assigned to you.");
        }
        Long studentId = request.getStudentId();
        cancelClaimed(requestId);
        lifecycle.record(requestId, CLAIMED, CANCELED, tutor.getUsername());
        notificationService.notify(studentId, tutor.getPublicName() + " canceled the session.", "/student/dashboard");
    }

    /** Pays the assigned tutor and records the resolve for a request the student owns. */
    @Transactional
    public void resolve(User student, Long requestId) {
        TutoringRequest request = owned(student, requestId);
        if (!CLAIMED.equals(request.getStatus())) {
            throw new IllegalStateException("Only claimed requests can be resolved.");
        }
        resolveAndPayout(requestId, request.getAssignedTutorId());
        lifecycle.record(requestId, CLAIMED, RESOLVED, student.getUsername());
    }

    /** The tutor id must match the request, so a caller cannot pay someone else. */
    @Transactional
    public void resolveAndPayout(Long requestId, Long tutorId) {
        TutoringRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found."));

        if (!CLAIMED.equals(request.getStatus())) {
            throw new IllegalStateException("Only claimed requests can be resolved.");
        }
        if (tutorId == null || !tutorId.equals(request.getAssignedTutorId())) {
            throw new IllegalArgumentException("This tutor is not assigned to the request.");
        }

        User tutor = userRepository.findById(tutorId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid tutor account."));

        int currentBalance = tutor.getTokenBalance() != null ? tutor.getTokenBalance() : 0;
        int bounty = request.getBountyValue() != null ? request.getBountyValue() : 0;
        tutor.setTokenBalance(currentBalance + bounty);
        saveUser(tutor);

        request.setStatus(RESOLVED);
        request.setResolvedTimestamp(now());
        requestRepository.save(request);
    }

    /** Refunds an open request that the student closed from the diagnostic note. No tutor is paid. */
    @Transactional
    public void resolveFromTriage(Long requestId) {
        TutoringRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found."));
        if (!OPEN.equals(request.getStatus())) {
            throw new IllegalStateException("Only an open request can be closed from the note.");
        }
        if (request.getAiDiagnosticNote() == null || request.getAiDiagnosticNote().isBlank()) {
            throw new IllegalStateException("A diagnostic note is not ready yet.");
        }
        refundStudent(request);
        request.setStatus(RESOLVED);
        request.setResolvedTimestamp(now());
        requestRepository.save(request);
    }

    /** Saves the review and adds the review reward once, in the same transaction. */
    @Transactional
    public void saveReviewAndReward(Review review) {
        reviewRepository.saveAndFlush(review);
        User student = userRepository.findById(review.getStudentId())
                .orElseThrow(() -> new IllegalArgumentException("Student not found."));
        int currentBalance = student.getTokenBalance() != null ? student.getTokenBalance() : 0;
        student.setTokenBalance(currentBalance + REVIEW_REWARD);
        saveUser(student);
    }

    @Transactional
    public void cancelAndRefund(Long requestId) {
        TutoringRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found."));

        if (!OPEN.equals(request.getStatus())) {
            throw new IllegalStateException("Only open requests can be canceled.");
        }

        refundStudent(request);

        request.setStatus(CANCELED);
        request.setCanceledTimestamp(now());
        requestRepository.save(request);
    }

    /** Refunds the bounty when a claimed session ends before it is resolved. */
    @Transactional
    public void cancelClaimed(Long requestId) {
        TutoringRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found."));
        if (!CLAIMED.equals(request.getStatus())) {
            throw new IllegalStateException("Only a claimed session can be canceled this way.");
        }
        refundStudent(request);
        request.setStatus(CANCELED);
        request.setCanceledTimestamp(now());
        requestRepository.save(request);
    }

    /** Refunds one open request that outlived its response time, and tells tutors who had offered. */
    @Transactional
    public void expireAndRefund(Long requestId) {
        TutoringRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found."));
        if (!OPEN.equals(request.getStatus())) {
            throw new IllegalStateException("Only open requests can expire.");
        }
        List<MeetingOffer> offers = offerRepository.findByRequestIdOrderByOfferedTimeAsc(requestId);
        refundStudent(request);
        request.setStatus(EXPIRED);
        requestRepository.save(request);
        offerRepository.deleteByRequestId(requestId);
        for (MeetingOffer offer : offers) {
            notificationService.notify(offer.getTutorId(),
                    "A request you offered on has expired.",
                    "/tutor/board");
        }
        lifecycle.record(requestId, OPEN, EXPIRED, "system");
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private TutoringRequest owned(User student, Long requestId) {
        TutoringRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("That request does not exist."));
        if (student == null || !student.getId().equals(request.getStudentId())) {
            throw new IllegalArgumentException("You can only manage your own requests.");
        }
        return request;
    }

    private void refundStudent(TutoringRequest request) {
        User student = userRepository.findById(request.getStudentId())
                .orElseThrow(() -> new IllegalArgumentException("Student not found."));
        int currentBalance = student.getTokenBalance() != null ? student.getTokenBalance() : 0;
        int bounty = request.getBountyValue() != null ? request.getBountyValue() : 0;
        student.setTokenBalance(currentBalance + bounty);
        saveUser(student);
    }

    /**
     * A second session already wrote this balance. Tell the page to reload instead of returning 500.
     */
    private void saveUser(User user) {
        try {
            entityManager.lock(user, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
            userRepository.saveAndFlush(user);
        } catch (OptimisticLockingFailureException | OptimisticLockException e) {
            throw new IllegalStateException("This request changed. Open it and try again.");
        }
    }
}
