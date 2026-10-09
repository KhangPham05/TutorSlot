package com.tutorslot.service;

import com.tutorslot.exception.NotFoundException;
import com.tutorslot.model.Provider;
import com.tutorslot.model.User;
import com.tutorslot.repository.ProviderRepository;
import com.tutorslot.repository.UserRepository;
import org.springframework.stereotype.Service;

// Resolves the logged-in user's own record from the authenticated email -- never from a form
// field. Controllers call this instead of reading UserRepository/ProviderRepository directly.
@Service
public class CurrentUserService {

    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;

    public CurrentUserService(UserRepository userRepository, ProviderRepository providerRepository) {
        this.userRepository = userRepository;
        this.providerRepository = providerRepository;
    }

    public User requireByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("No user with email " + email));
    }

    public Provider requireProviderByEmail(String email) {
        User user = requireByEmail(email);
        return providerRepository.findByUserId(user.userId())
                .orElseThrow(() -> new NotFoundException("No provider profile for " + email));
    }
}
