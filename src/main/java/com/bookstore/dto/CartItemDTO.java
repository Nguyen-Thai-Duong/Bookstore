package com.bookstore.dto;

import com.bookstore.model.CartItem;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartItemDTO {

    private Long id;
    private Long cartId;
    private BookDTO book;
    private Integer quantity;

    public static CartItemDTO fromEntity(CartItem cartItem) {
        if (cartItem == null) {
            return null;
        }

        Long cartId = cartItem.getCart() == null ? null : cartItem.getCart().getId();
        return new CartItemDTO(cartItem.getId(), cartId, BookDTO.fromEntity(cartItem.getBook()),
                cartItem.getQuantity());
    }

    public BigDecimal getLineTotal() {
        if (book == null || book.getPrice() == null || quantity == null) {
            return BigDecimal.ZERO;
        }

        return book.getPrice().multiply(BigDecimal.valueOf(quantity));
    }
}