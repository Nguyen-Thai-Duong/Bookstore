package com.bookstore.service;

import com.bookstore.model.CartItem;
import com.bookstore.repository.CartItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HiddenBookCartCleanupService {

    private final CartItemRepository cartItemRepository;

    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void cleanupHiddenBooksFromCarts() {
        List<CartItem> allItems = cartItemRepository.findAll();
        if (allItems.isEmpty()) {
            return;
        }

        List<CartItem> expiredHiddenItems = allItems.stream()
                .filter(item -> item.getBook() != null && item.getBook().shouldRemoveFromCartNow())
                .toList();

        if (!expiredHiddenItems.isEmpty()) {
            cartItemRepository.deleteAllInBatch(expiredHiddenItems);
        }
    }
}
