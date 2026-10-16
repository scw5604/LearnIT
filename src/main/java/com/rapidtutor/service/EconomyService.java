package com.rapidtutor.service;

import com.rapidtutor.model.TutoringRequest;
import com.rapidtutor.model.User;
import com.rapidtutor.repository.TutoringRequestRepository;
import com.rapidtutor.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class EconomyService {

    private final UserRepository userRepository;
    private final TutoringRequestRepository requestRepository;

    public EconomyService(UserRepository userRepository, TutoringRequestRepository requestRepository) {
        this.userRepository = userRepository;
        this.requestRepository = requestRepository;
    }

    /** Deducts bounty from the student and creates the request in one transaction. */
    @Transactional
    public TutoringRequest createRequestWithBounty(String username, TutoringRequest request, Integer offeredBounty) {
        if (offeredBounty == null || offeredBounty < 0 || offeredBounty > 50) {
            throw new IllegalArgumentException("Bounty must be between 0 and 50 tokens.");
        }

        User student = userRepository.findByUsername(username);

        if (student == null || !student.getRole().equals("STUDENT")) {
            throw new IllegalArgumentException("Invalid student account.");
        }

        int currentBalance = student.getTokenBalance() != null ? student.getTokenBalance() : 0;
        if (currentBalance < offeredBounty) {
            throw new IllegalStateException("Insufficient token balance to post this bounty.");
        }
        if (requestRepository.countByStudentIdAndStatus(student.getId(), "OPEN") >= 3) {
            throw new IllegalStateException("You already have 3 open requests.");
        }

        student.setTokenBalance(currentBalance - offeredBounty);
        userRepository.save(student);

        request.setStudentId(student.getId());
        request.setBountyValue(offeredBounty);
        request.setAssignedTutorId(null);
        request.setClaimedTimestamp(null);
        request.setResolvedTimestamp(null);
        request.setCanceledTimestamp(null);
        request.setOfferedMeetingTime(null);
        request.setAiDiagnosticNote(null);
        request.setStatus("OPEN");
        return requestRepository.save(request);
    }

    /** Pays the bounty to the assigned tutor and marks the request resolved. */
    @Transactional
    public void resolveAndPayout(Long requestId, Long tutorId) {
        TutoringRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found."));

        if (!request.getStatus().equals("CLAIMED")) {
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
        userRepository.save(tutor);

        request.setStatus("RESOLVED");
        request.setResolvedTimestamp(LocalDateTime.now());
        requestRepository.save(request);
    }

    /** Refunds the bounty and marks the request canceled. */
    @Transactional
    public void cancelAndRefund(Long requestId) {
        TutoringRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found."));

        if (!"OPEN".equals(request.getStatus())) {
            throw new IllegalStateException("Only open requests can be canceled.");
        }

        refundStudent(request);

        request.setStatus("CANCELED");
        request.setCanceledTimestamp(LocalDateTime.now());
        requestRepository.save(request);
    }

    /** Refunds the bounty when a claimed session ends before it is resolved. */
    @Transactional
    public void cancelClaimed(Long requestId) {
        TutoringRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found."));
        if (!"CLAIMED".equals(request.getStatus())) {
            throw new IllegalStateException("Only a claimed session can be canceled this way.");
        }
        refundStudent(request);
        request.setStatus("CANCELED");
        request.setCanceledTimestamp(LocalDateTime.now());
        requestRepository.save(request);
    }

    /** Refunds the bounty and closes an open request that outlived its response time. */
    @Transactional
    public void expireAndRefund(Long requestId) {
        TutoringRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found."));
        if (!"OPEN".equals(request.getStatus())) {
            throw new IllegalStateException("Only open requests can expire.");
        }
        refundStudent(request);
        request.setStatus("EXPIRED");
        requestRepository.save(request);
    }

    private void refundStudent(TutoringRequest request) {
        User student = userRepository.findById(request.getStudentId())
                .orElseThrow(() -> new IllegalArgumentException("Student not found."));
        int currentBalance = student.getTokenBalance() != null ? student.getTokenBalance() : 0;
        int bounty = request.getBountyValue() != null ? request.getBountyValue() : 0;
        student.setTokenBalance(currentBalance + bounty);
        userRepository.save(student);
    }
}
