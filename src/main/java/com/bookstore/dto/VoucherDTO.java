package com.bookstore.dto;

import com.bookstore.model.Voucher;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoucherDTO {

    private Long id;
    private String code;
    private Integer discountPercent;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Integer quantity;
    private String status;

    public static VoucherDTO fromEntity(Voucher voucher) {
        if (voucher == null) {
            return null;
        }

        return new VoucherDTO(
                voucher.getId(),
                voucher.getCode(),
                voucher.getDiscountPercent(),
                voucher.getStartDate(),
                voucher.getEndDate(),
                voucher.getQuantity(),
                voucher.getStatus());
    }

    public Voucher toEntity() {
        return new Voucher(id, code, discountPercent, startDate, endDate, quantity, status, null);
    }
}