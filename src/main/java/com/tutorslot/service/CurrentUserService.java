package com.tutorslot.service;

import com.tutorslot.exception.NotFoundException;
import com.tutorslot.model.User;
import com.tutorslot.repository.UserRepository;
import org.springframework.stereotype.Service;

// Resolves the logged-in user's own record from the authenticated email -- never from a form
// field. Controllers call this instead of reading UserRepository directly.
@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User requireByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("No user with email " + email));
    }
}
