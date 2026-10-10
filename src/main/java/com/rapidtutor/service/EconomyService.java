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
        User student = userRepository.findByUsername(username);

        if (student == null || !student.getRole().equals("STUDENT")) {
            throw new IllegalArgumentException("Invalid student account.");
        }

        int currentBalance = student.getTokenBalance() != null ? student.getTokenBalance() : 0;
        if (currentBalance < offeredBounty) {
            throw new IllegalStateException("Insufficient token balance to post this bounty.");
        }

        student.setTokenBalance(currentBalance - offeredBounty);
        userRepository.save(student);

        request.setStudentId(student.getId());
        request.setBountyValue(offeredBounty);
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

        if (request.getStatus().equals("RESOLVED")) {
            throw new IllegalStateException("Cannot cancel an already resolved request.");
        }

        User student = userRepository.findById(request.getStudentId())
                .orElseThrow(() -> new IllegalArgumentException("Student not found."));

        int currentBalance = student.getTokenBalance() != null ? student.getTokenBalance() : 0;
        student.setTokenBalance(currentBalance + request.getBountyValue());
        userRepository.save(student);

        request.setStatus("CANCELED");
        requestRepository.save(request);
    }
}
