package com.bookstore.service;

import com.bookstore.model.Voucher;
import com.bookstore.repository.VoucherRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
public class VoucherServiceImpl implements VoucherService {

    @Autowired
    private VoucherRepository voucherRepository;

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
            // If endDate is saved with 00:00:00, treat it as end of that day.
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
