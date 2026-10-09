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

    /**
     * Deducts the bounty from the student's balance and creates the ticket in one atomic transaction.
     */
    @Transactional
    public TutoringRequest createRequestWithBounty(String username, TutoringRequest request, Integer offeredBounty) {
        User student = userRepository.findByUsername(username);

        if (student == null || !student.getRole().equals("STUDENT")) {
            throw new IllegalArgumentException("Invalid student account.");
        }

        // Null-safe balance check (defaults to 0 if the database column is empty from earlier seeding)
        int currentBalance = student.getTokenBalance() != null ? student.getTokenBalance() : 0;

        if (currentBalance < offeredBounty) {
            throw new IllegalStateException("Insufficient token balance to post this bounty.");
        }

        // Deduct tokens and hold them in the ticket's "escrow"
        student.setTokenBalance(currentBalance - offeredBounty);
        userRepository.save(student);

        request.setStudentId(student.getId());
        request.setBountyValue(offeredBounty);

        return requestRepository.save(request);
    }

    /**
     * Transfers the escrowed bounty to the tutor upon successful resolution.
     */
    @Transactional
    public void resolveAndPayout(Long requestId, Long tutorId) {
        TutoringRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found."));

        if (!request.getStatus().equals("CLAIMED")) {
            throw new IllegalStateException("Only claimed requests can be resolved.");
        }

        // Change from findByUsername to findById
        User tutor = userRepository.findById(tutorId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid tutor account."));

        // Payout the bounty safely handling nulls
        int currentBalance = tutor.getTokenBalance() != null ? tutor.getTokenBalance() : 0;
        int bounty = request.getBountyValue() != null ? request.getBountyValue() : 0;

        tutor.setTokenBalance(currentBalance + bounty);
        userRepository.save(tutor);

        // Update ticket state
        request.setStatus("RESOLVED");
        request.setResolvedTimestamp(LocalDateTime.now());
        requestRepository.save(request);
    }

    /**
     * Refunds the tokens if a student cancels their own request before it is resolved.
     */
    @Transactional
    public void cancelAndRefund(Long requestId) {
        TutoringRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found."));

        if (request.getStatus().equals("RESOLVED")) {
            throw new IllegalStateException("Cannot cancel an already resolved request.");
        }

        User student = userRepository.findById(request.getStudentId())
                .orElseThrow(() -> new IllegalArgumentException("Student not found."));

        // Refund the tokens
        int currentBalance = student.getTokenBalance() != null ? student.getTokenBalance() : 0;
        student.setTokenBalance(currentBalance + request.getBountyValue());
        userRepository.save(student);

        request.setStatus("CANCELED");
        requestRepository.save(request);
    }
}
