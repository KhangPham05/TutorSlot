package com.tutorslot.security;

import com.tutorslot.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

// Wraps our own User model so Spring Security has a username/password/authority view of it.
// Username is the user's email, and role (CUSTOMER/PROVIDER) maps to a single ROLE_* authority.
public class UserPrincipal implements UserDetails {

    private final User user;

    public UserPrincipal(User user) {
        this.user = user;
    }

    public User user() {
        return user;
    }

    @Override
    public String getUsername() {
        return user.email();
    }

    @Override
    public String getPassword() {
        return user.passwordHash();
    }

    @Override
    public List<GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.role()));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
