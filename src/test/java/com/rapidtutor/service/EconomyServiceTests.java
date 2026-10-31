package com.rapidtutor.service;

import com.rapidtutor.model.TutoringRequest;
import com.rapidtutor.model.User;
import com.rapidtutor.repository.TutoringRequestRepository;
import com.rapidtutor.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class EconomyServiceTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TutoringRequestRepository requestRepository;

    private EconomyService economyService;

    @BeforeEach
    void setUp() {
        economyService = new EconomyService(userRepository, requestRepository, null, null, null, null, null);
    }

    @Test
    public void testCreateRequestWithInsufficientBalanceThrowsException() {
        User student = new User();
        student.setUsername("student1");
        student.setRole("STUDENT");
        student.setTokenBalance(10);

        when(userRepository.findByUsername("student1")).thenReturn(student);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
                economyService.createRequestWithBounty("student1", new TutoringRequest(), 20));

        assertEquals("Insufficient token balance to post this bounty.", thrown.getMessage());
        assertEquals(10, student.getTokenBalance());
        verify(userRepository, never()).saveAndFlush(any());
        verify(requestRepository, never()).save(any());
    }

    @Test
    public void testCreateRequestWithNullDatabaseBalanceHandlesNullSafely() {
        User student = new User();
        student.setUsername("student1");
        student.setRole("STUDENT");
        student.setTokenBalance(null);

        when(userRepository.findByUsername("student1")).thenReturn(student);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
                economyService.createRequestWithBounty("student1", new TutoringRequest(), 10));

        assertEquals("Insufficient token balance to post this bounty.", thrown.getMessage());
        assertNull(student.getTokenBalance());
        verify(userRepository, never()).saveAndFlush(any());
        verify(requestRepository, never()).save(any());
    }

    @Test
    public void aNonStudentCannotCreateARequest() {
        User tutor = new User();
        tutor.setUsername("tutor1");
        tutor.setRole("TUTOR");
        tutor.setTokenBalance(100);
        when(userRepository.findByUsername("missing")).thenReturn(null);
        when(userRepository.findByUsername("tutor1")).thenReturn(tutor);

        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class, () ->
                economyService.createRequestWithBounty("missing", new TutoringRequest(), 10));
        IllegalArgumentException notStudent = assertThrows(IllegalArgumentException.class, () ->
                economyService.createRequestWithBounty("tutor1", new TutoringRequest(), 10));

        assertEquals("Invalid student account.", missing.getMessage());
        assertEquals("Invalid student account.", notStudent.getMessage());
        assertEquals(100, tutor.getTokenBalance());
        verify(userRepository, never()).saveAndFlush(any());
        verify(requestRepository, never()).save(any());
    }

    @Test
    public void aNullBountyIsRejected() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                economyService.createRequestWithBounty("student1", new TutoringRequest(), null));

        assertEquals("Bounty must be between 0 and 50 tokens.", thrown.getMessage());
        verify(userRepository, never()).findByUsername(any());
        verify(userRepository, never()).saveAndFlush(any());
        verify(requestRepository, never()).save(any());
    }
}
