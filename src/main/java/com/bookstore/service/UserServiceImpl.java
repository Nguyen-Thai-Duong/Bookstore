package com.bookstore.service;

import com.bookstore.model.User;
import com.bookstore.repository.UserRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    public List<User> getCustomersOnly() {
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() != null && (u.getRole().getId() == 2L || "Customer".equalsIgnoreCase(u.getRole().getRoleName())))
                .toList();
    }

    @Override
    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    @Override
    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    public User saveUser(User user) {
        return userRepository.save(user);
    }

    @Override
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    public void sendOtp(String email, String otp) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(senderEmail, "BookNest Store");
            helper.setTo(email);
            helper.setSubject("OTP Verification - BookNest");

            String htmlContent = 
                "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; border: 1px solid #e0e0e0; border-radius: 10px; overflow: hidden;'>" +
                    "<div style='background-color: #F37021; padding: 20px; text-align: center;'>" +
                        "<h1 style='color: #ffffff; margin: 0;'>BookNest</h1>" +
                    "</div>" +
                    "<div style='padding: 30px; line-height: 1.6; color: #333333;'>" +
                        "<h2 style='color: #F37021;'>Email Verification</h2>" +
                        "<p>Hello,</p>" +
                        "<p>Thank you for choosing <b>BookNest</b>. To complete your registration, please use the OTP code below:</p>" +
                        "<div style='text-align: center; margin: 30px 0;'>" +
                            "<span style='display: inline-block; padding: 15px 30px; background-color: #fffaf7; border: 2px dashed #F37021; font-size: 32px; font-weight: bold; color: #F37021; letter-spacing: 5px; border-radius: 5px;'>" + otp + "</span>" +
                        "</div>" +
                        "<p style='color: #ef4444;'><b>Note:</b> This code will expire in <b>180 seconds</b>.</p>" +
                        "<p>If you did not request this code, please ignore this email or contact our support team.</p>" +
                        "<hr style='border: 0; border-top: 1px solid #eeeeee; margin: 20px 0;'>" +
                        "<p style='font-size: 12px; color: #7f8c8d; text-align: center;'>This is an automated email, please do not reply.<br>&copy; 2026 BookNest Team.</p>" +
                    "</div>" +
                "</div>";

            helper.setText(htmlContent, true);
            mailSender.send(message);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            throw new RuntimeException("Email sending failed: " + e.getMessage());
        }
    }
}
