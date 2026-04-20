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
            helper.setSubject("Mã xác thực OTP - BookNest");

            String htmlContent = 
                "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; border: 1px solid #e0e0e0; border-radius: 10px; overflow: hidden;'>" +
                    "<div style='background-color: #8b5a3c; padding: 20px; text-align: center;'>" +
                        "<h1 style='color: #ffffff; margin: 0;'>BookNest</h1>" +
                    "</div>" +
                    "<div style='padding: 30px; line-height: 1.6; color: #333333;'>" +
                        "<h2 style='color: #8b5a3c;'>Xác thực tài khoản</h2>" +
                        "<p>Chào bạn,</p>" +
                        "<p>Cảm ơn bạn đã lựa chọn <b>BookNest</b>. Để hoàn tất quá trình đăng ký, vui lòng sử dụng mã OTP dưới đây:</p>" +
                        "<div style='text-align: center; margin: 30px 0;'>" +
                            "<span style='display: inline-block; padding: 15px 30px; background-color: #fdfcfb; border: 2px dashed #8b5a3c; font-size: 32px; font-weight: bold; color: #8b5a3c; letter-spacing: 5px; border-radius: 5px;'>" + otp + "</span>" +
                        "</div>" +
                        "<p style='color: #e74c3c;'><b>Lưu ý:</b> Mã này sẽ hết hạn sau <b>180 giây</b>.</p>" +
                        "<p>Nếu bạn không yêu cầu mã này, vui lòng bỏ qua email này hoặc liên hệ với bộ phận hỗ trợ.</p>" +
                        "<hr style='border: 0; border-top: 1px solid #eeeeee; margin: 20px 0;'>" +
                        "<p style='font-size: 12px; color: #7f8c8d; text-align: center;'>Đây là email tự động, vui lòng không phản hồi email này.<br>&copy; 2026 BookNest Team.</p>" +
                    "</div>" +
                "</div>";

            helper.setText(htmlContent, true);
            mailSender.send(message);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            throw new RuntimeException("Lỗi gửi email: " + e.getMessage());
        }
    }
}
