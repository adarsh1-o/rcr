package com.example.demo.service;

import com.example.demo.dto.AuthResponse;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.exception.DuplicateResourceException;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new DuplicateResourceException("Email already registered");
        }

        Role role = req.getRole() == null ? Role.STUDENT : req.getRole();
        if (role == Role.ADMIN) {
            throw new AccessDeniedException("Administrator accounts must be provisioned by an administrator.");
        }

        User saved = User.builder()
                .name(req.getName())
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .role(role)
                .active(true)
                .build();

        return AuthResponse.builder()
                .userId(userRepository.save(saved).getId())
                .name(saved.getName())
                .email(saved.getEmail())
                .role(saved.getRole())
                .token(null) // token populated by controller after jwt generation
                .build();
    }

    public Optional<User> findActiveByEmail(String email) {
        return userRepository.findByEmail(email).filter(User::isActive);
    }

    public User login(LoginRequest req) {
        User user = findActiveByEmail(req.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        return user;
    }
}

