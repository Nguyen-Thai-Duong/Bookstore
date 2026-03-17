package org.example.bookstore.service;

import org.example.bookstore.model.User;
import org.example.bookstore.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${app.auth.default-role-id:2}")
    private int defaultRoleId;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public User register(String fullName, String email, String rawPassword, String phone, String address) {
        User user = new User();
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setPhone(phone);
        user.setAddress(address);
        user.setStatus("Active");
        user.setCreatedAt(LocalDateTime.now());
        user.setRoleID(defaultRoleId);
        return userRepository.save(user);
    }

    public User authenticate(String email, String rawPassword) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return null;
        }
        User user = userOpt.get();
        String stored = user.getPassword() == null ? "" : user.getPassword();

        // Accept both BCrypt-hashed and legacy plain-text passwords.
        boolean matches;
        if (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$")) {
            matches = passwordEncoder.matches(rawPassword, stored);
        } else {
            matches = stored.equals(rawPassword);
        }

        if (!matches) {
            return null;
        }

        if ("Inactive".equalsIgnoreCase(user.getStatus())) {
            return null;
        }

        return user;
    }
}
