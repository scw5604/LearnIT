package com.rapidtutor.service;

import com.rapidtutor.model.User;
import com.rapidtutor.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Looks up the name shown for a user id, so pages do not copy names onto requests or offers. */
@Service
public class PublicNames {

    private final UserRepository userRepository;

    public PublicNames(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public String of(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId).map(User::getPublicName).orElse(null);
    }

    public Map<Long, String> byId(Collection<Long> userIds) {
        Set<Long> ids = new LinkedHashSet<>(userIds);
        ids.removeIf(Objects::isNull);
        Map<Long, String> names = new HashMap<>();
        if (ids.isEmpty()) {
            return names;
        }
        for (User user : userRepository.findAllById(ids)) {
            names.put(user.getId(), user.getPublicName());
        }
        return names;
    }
}
