package com.bookstore.service;

import com.bookstore.model.User;
import java.util.List;
import java.util.Optional;

public interface UserService {
    List<User> getAllUsers();

    Optional<User> getUserById(Long id);

    Optional<User> getUserByEmail(String email);

    User saveUser(User user);

    void deleteUser(Long id);

    boolean existsByEmail(String email);

    // Thêm hàm này để gửi OTP từ Controller
    void sendOtp(String email, String otp);
}
