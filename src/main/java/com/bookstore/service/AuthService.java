package com.bookstore.service;

import com.bookstore.model.Role;
import com.bookstore.model.User;
import com.bookstore.repository.RoleRepository;
import com.bookstore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${app.auth.default-role-id:2}")
    private Long defaultRoleId;

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public User register(String fullName, String email, String rawPassword, String phone, String address) {
        Role defaultRole = roleRepository.findById(defaultRoleId)
                .or(() -> roleRepository.findByRoleName("Customer"))
                .or(() -> roleRepository.findByRoleName("User"))
                .orElseThrow(() -> new IllegalStateException("Default role not found"));

        User user = new User();
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setPhone(phone);
        user.setAddress(address);
        user.setStatus("Active");
        user.setCreatedAt(LocalDateTime.now());
        user.setRole(defaultRole);
        return userRepository.save(user);
    }

    public User authenticate(String email, String rawPassword) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return null;
        }

        User user = userOpt.get();
        String stored = user.getPassword() == null ? "" : user.getPassword();

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

    public User updateProfile(Long userId, String fullName, String phone, String address) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return null;
        }

        User user = userOpt.get();
        user.setFullName(fullName);
        user.setPhone(phone);
        user.setAddress(address);
        return userRepository.save(user);
    }

    public boolean changePassword(Long userId, String currentPassword, String newPassword) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return false;
        }

        User user = userOpt.get();
        String stored = user.getPassword() == null ? "" : user.getPassword();

        boolean matches;
        if (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$")) {
            matches = passwordEncoder.matches(currentPassword, stored);
        } else {
            matches = stored.equals(currentPassword);
        }

        if (!matches) {
            return false;
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        return true;
    }

    public boolean isAdmin(User user) {
        if (user == null || user.getRole() == null) {
            return false;
        }

        String roleName = user.getRole().getRoleName();
        if (roleName != null && roleName.equalsIgnoreCase("admin")) {
            return true;
        }

        Long roleId = user.getRole().getId();
        return roleId != null && roleId == 1L;
    }
}
