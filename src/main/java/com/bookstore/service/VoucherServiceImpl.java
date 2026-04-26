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
        helper.setSubject("🎁 Gift from BookNest: " + discount + " Voucher");

        String htmlContent = 
            "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; border: 1px solid #e0e0e0; border-radius: 10px; overflow: hidden;'>" +
                "<div style='background-color: #F37021; padding: 20px; text-align: center;'>" +
                    "<h1 style='color: #ffffff; margin: 0;'>BookNest</h1>" +
                "</div>" +
                "<div style='padding: 30px; line-height: 1.6; color: #333333;'>" +
                    "<h2 style='color: #F37021;'>Congratulations!</h2>" +
                    "<p>You have received a special discount code from <b>BookNest</b>:</p>" +
                    "<div style='background: #fffaf7; padding: 20px; border: 2px dashed #F37021; text-align: center; margin: 20px 0;'>" +
                        "<span style='font-size: 28px; font-weight: bold; color: #F37021;'>" + voucherCode + "</span>" +
                    "</div>" +
                    "<p style='margin-top: 15px;'><b>Discount:</b> <span style='color: #ef4444; font-size: 20px; font-weight: bold;'>" + discount + "</span></p>" +
                    "<p style='font-size: 14px; color: #555;'>" +
                        "📅 <b>Start Date:</b> " + startDate + "<br>" +
                        "⌛ <b>End Date:</b> " + endDate + 
                    "</p>" +
                    "<p style='font-size: 13px; color: #7f8c8d; margin-top: 20px; border-top: 1px solid #eee; pt: 15px;'>" +
                        "Apply this code at checkout on the BookNest website." +
                    "</p>" +
                    "<p style='font-size: 12px; color: #95a5a6; text-align: center; margin-top: 30px;'>" +
                        "&copy; 2026 BookNest Store. All rights reserved." +
                    "</p>" +
                "</div>" +
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
