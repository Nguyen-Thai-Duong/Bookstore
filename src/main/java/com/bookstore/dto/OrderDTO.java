package com.bookstore.dto;

import com.bookstore.model.Order;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderDTO {

    private Long id;
    private UserDTO user;
    private VoucherDTO voucher;
    private BigDecimal totalAmount;
    private String status;
    private LocalDateTime orderDate;
    private String shippingAddress;
    private List<OrderDetailDTO> orderDetails;

    public static OrderDTO fromEntity(Order order) {
        if (order == null) {
            return null;
        }

        List<OrderDetailDTO> details = order.getOrderDetails() == null
                ? List.of()
                : order.getOrderDetails().stream().map(OrderDetailDTO::fromEntity).toList();

        return new OrderDTO(
                order.getId(),
                UserDTO.fromEntity(order.getUser()),
                VoucherDTO.fromEntity(order.getVoucher()),
                order.getTotalAmount(),
                order.getStatus(),
                order.getOrderDate(),
                order.getShippingAddress(),
                details);
    }
}