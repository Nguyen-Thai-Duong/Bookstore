package com.bookstore.dto;

import com.bookstore.model.Cart;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartDTO {

    private Long id;
    private UserDTO user;
    private LocalDateTime createdAt;

    public static CartDTO fromEntity(Cart cart) {
        if (cart == null) {
            return null;
        }

        return new CartDTO(cart.getId(), UserDTO.fromEntity(cart.getUser()), cart.getCreatedAt());
    }
}