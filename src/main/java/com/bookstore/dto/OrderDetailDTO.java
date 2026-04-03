package com.bookstore.dto;

import com.bookstore.model.OrderDetail;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderDetailDTO {

    private Long id;
    private BookDTO book;
    private Integer quantity;
    private BigDecimal unitPrice;

    public static OrderDetailDTO fromEntity(OrderDetail orderDetail) {
        if (orderDetail == null) {
            return null;
        }

        return new OrderDetailDTO(
                orderDetail.getId(),
                BookDTO.fromEntity(orderDetail.getBook()),
                orderDetail.getQuantity(),
                orderDetail.getUnitPrice());
    }
}