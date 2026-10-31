package com.rapidtutor;

import com.rapidtutor.model.TutoringRequest;
import com.rapidtutor.repository.TutoringRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@TestPropertySource(properties = "rapidtutor.demo-users.enabled=false")
public class ConcurrencyTests {

    @Autowired
    private TutoringRequestRepository repository;

    @Test
    public void testOptimisticLockingRejectsSimultaneousClaims() {
        TutoringRequest created = new TutoringRequest();
        created.setCourse("HCDD 412");
        created.setTopic("Optimistic lock");
        created.setShortDescription("Two tutors claim the same request.");
        created = repository.saveAndFlush(created);

        TutoringRequest req1 = repository.findById(created.getId()).orElseThrow();
        TutoringRequest req2 = repository.findById(created.getId()).orElseThrow();

        req1.setStatus("CLAIMED");
        repository.saveAndFlush(req1);

        req2.setStatus("CLAIMED");
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> repository.saveAndFlush(req2));

        assertEquals("CLAIMED", repository.findById(created.getId()).orElseThrow().getStatus());
    }
}
