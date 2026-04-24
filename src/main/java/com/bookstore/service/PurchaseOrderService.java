package com.bookstore.service;

import com.bookstore.model.Book;
import com.bookstore.model.PurchaseOrder;
import com.bookstore.model.PurchaseOrderDetail;
import com.bookstore.repository.BookRepository;
import com.bookstore.repository.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private final PurchaseOrderRepository poRepository;
    private final BookRepository bookRepository;

    public List<PurchaseOrder> getAllOrders() {
        return poRepository.findAll();
    }

    public Optional<PurchaseOrder> getOrderById(Integer id) {
        return poRepository.findById(id);
    }

    @Transactional
    public PurchaseOrder saveOrder(PurchaseOrder order) {
        return poRepository.save(order);
    }

    // Bước 2: Admin duyệt đơn
    @Transactional
    public void approveOrder(Integer poId) {
        PurchaseOrder order = poRepository.findById(poId).orElseThrow();
        order.setStatus("Pending");
        poRepository.save(order);
    }

    // Bước 3: Staff báo cáo hàng về
    @Transactional
    public void receiveOrder(Integer poId, List<PurchaseOrderDetail> updatedDetails) {
        PurchaseOrder order = poRepository.findById(poId).orElseThrow();
        for (PurchaseOrderDetail detail : order.getDetails()) {
            for (PurchaseOrderDetail updated : updatedDetails) {
                if (detail.getId().equals(updated.getId())) {
                    detail.setReceivedQuantity(updated.getReceivedQuantity());
                }
            }
        }
        order.setStatus("Received");
        poRepository.save(order);
    }

    // Bước 4: Admin chốt số liệu
    @Transactional
    public void finalizeOrder(Integer poId, String note) {
        PurchaseOrder order = poRepository.findById(poId).orElseThrow();
        order.setNote(note);
        order.setStatus("Finalized");
        poRepository.save(order);
    }

    // Bước 5: Staff xác nhận nhập kho & cập nhật tồn kho
    @Transactional
    public void confirmAndStockIn(Integer poId) {
        PurchaseOrder order = poRepository.findById(poId)
                .orElseThrow(() -> new RuntimeException("Purchase Order not found"));

        if (!"Finalized".equalsIgnoreCase(order.getStatus())) {
            throw new RuntimeException("Order must be Finalized by Admin before stock in");
        }

        for (PurchaseOrderDetail detail : order.getDetails()) {
            Book product = detail.getProduct();
            int receivedQty = (detail.getReceivedQuantity() != null) ? detail.getReceivedQuantity() : 0;
            
            int currentStock = (product.getStock() != null ? product.getStock() : 0);
            product.setStock(currentStock + receivedQty);
            bookRepository.save(product);
        }

        order.setStatus("Completed");
        poRepository.save(order);
    }

    @Transactional
    public void cancelOrder(Integer id) {
        PurchaseOrder order = poRepository.findById(id).orElseThrow();
        order.setStatus("Cancelled");
        poRepository.save(order);
    }
}
