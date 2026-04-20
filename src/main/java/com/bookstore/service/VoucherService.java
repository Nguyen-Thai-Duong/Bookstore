package com.bookstore.service;

import com.bookstore.model.Voucher;
import jakarta.mail.MessagingException;
import java.util.List;
import java.util.Optional;

public interface VoucherService {
    List<Voucher> getAllVouchers();

    Optional<Voucher> getVoucherById(Long id);

    Optional<Voucher> getVoucherByCode(String code);

    Voucher saveVoucher(Voucher voucher);

    void deleteVoucher(Long id);

    boolean existsByCode(String code);

    void sendVoucherEmail(String to, String voucherCode, String discount, String startDate, String endDate) throws MessagingException;
}
