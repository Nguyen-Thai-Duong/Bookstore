package com.bookstore.service;

import com.bookstore.model.Voucher;
import com.bookstore.repository.VoucherRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
public class VoucherServiceImpl implements VoucherService {

    @Autowired
    private VoucherRepository voucherRepository;

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    @Override
    public List<Voucher> getAllVouchers() {
        return voucherRepository.findAll().stream()
                .map(this::normalizeVoucherStatus)
                .toList();
    }

    @Override
    public Optional<Voucher> getVoucherById(Long id) {
        return voucherRepository.findById(id).map(this::normalizeVoucherStatus);
    }

    @Override
    public Optional<Voucher> getVoucherByCode(String code) {
        return voucherRepository.findByCode(code).map(this::normalizeVoucherStatus);
    }

    @Override
    public Voucher saveVoucher(Voucher voucher) {
        return voucherRepository.save(normalizeVoucherStatus(voucher));
    }

    @Override
    public void deleteVoucher(Long id) {
        voucherRepository.deleteById(id);
    }

    @Override
    public boolean existsByCode(String code) {
        return voucherRepository.findByCode(code).isPresent();
    }

    @Override
    public void sendVoucherEmail(String to, String voucherCode, String discount, String startDate, String endDate) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(senderEmail);
        helper.setTo(to);
        helper.setSubject("🎁 Quà tặng từ BookNest: Voucher " + discount);

        String htmlContent = 
            "<div style='font-family: sans-serif; line-height: 1.5; color: #333;'>" +
                "<h3>Chúc mừng! Bạn nhận được một mã ưu đãi:</h3>" +
                "<div style='background: #fff3cd; padding: 15px; border: 1px dashed #ffc107; text-align: center;'>" +
                    "<span style='font-size: 24px; font-weight: bold; color: #856404;'>" + voucherCode + "</span>" +
                "</div>" +
                "<p style='margin-top: 15px;'><b>Giảm giá:</b> <span style='color: #d9534f; font-size: 18px;'>" + discount + "</span></p>" +
                "<p style='font-size: 14px;'>" +
                    "📅 <b>Bắt đầu:</b> " + startDate + "<br>" +
                    "⌛ <b>Kết thúc:</b> " + endDate + 
                "</p>" +
                "<p style='font-size: 12px; color: #888;'>Áp dụng khi thanh toán tại website BookNest.</p>" +
            "</div>";

        helper.setText(htmlContent, true);
        mailSender.send(message);
    }

    private Voucher normalizeVoucherStatus(Voucher voucher) {
        if (voucher == null) {
            return null;
        }

        String currentStatus = voucher.getStatus() == null ? "" : voucher.getStatus().trim();
        if ("Inactive".equalsIgnoreCase(currentStatus)) {
            return voucher;
        }

        LocalDateTime now = LocalDateTime.now();
        if (voucher.getStartDate() != null && now.isBefore(voucher.getStartDate())) {
            voucher.setStatus("Inactive");
            return voucher;
        }

        LocalDateTime endDate = voucher.getEndDate();
        if (endDate != null) {
            LocalDateTime effectiveEnd = endDate.toLocalTime().equals(LocalTime.MIDNIGHT)
                    ? endDate.toLocalDate().atTime(LocalTime.MAX)
                    : endDate;
            if (now.isAfter(effectiveEnd)) {
                voucher.setStatus("Inactive");
                return voucher;
            }
        }

        if (voucher.getQuantity() != null && voucher.getQuantity() <= 0) {
            voucher.setStatus("Inactive");
            return voucher;
        }

        voucher.setStatus("Active");
        return voucher;
    }
}
