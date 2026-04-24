package com.bookstore.service;

import com.bookstore.model.Book;
import com.bookstore.model.Import;
import com.bookstore.model.ImportDetail;
import com.bookstore.repository.BookRepository;
import com.bookstore.repository.ImportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ImportService {

    private final ImportRepository importRepository;
    private final BookRepository bookRepository;

    public List<Import> getAllImports() {
        return importRepository.findAll();
    }

    public List<Import> getAllImportsSorted() {
        return importRepository.findAllByOrderByIdDesc();
    }

    public Optional<Import> getImportById(Integer id) {
        return importRepository.findById(id);
    }

    @Transactional
    public Import saveImport(Import importOrder) {
        return importRepository.save(importOrder);
    }

    @Transactional
    public void approveImport(Integer importId) {
        Import importOrder = importRepository.findById(importId).orElseThrow();
        importOrder.setStatus("Pending");
        importRepository.save(importOrder);
    }

    @Transactional
    public void receiveImport(Integer importId, List<ImportDetail> updatedDetails) {
        Import importOrder = importRepository.findById(importId).orElseThrow();
        for (ImportDetail detail : importOrder.getDetails()) {
            for (ImportDetail updated : updatedDetails) {
                if (detail.getId().equals(updated.getId())) {
                    detail.setReceivedQuantity(updated.getReceivedQuantity());
                }
            }
        }
        importOrder.setStatus("Received");
        importRepository.save(importOrder);
    }

    @Transactional
    public void finalizeImport(Integer importId, String note) {
        Import importOrder = importRepository.findById(importId).orElseThrow();
        
        BigDecimal newTotal = BigDecimal.ZERO;
        for (ImportDetail detail : importOrder.getDetails()) {
            int qty = (detail.getReceivedQuantity() != null) ? detail.getReceivedQuantity() : 0;
            newTotal = newTotal.add(detail.getUnitPrice().multiply(BigDecimal.valueOf(qty)));
        }
        
        importOrder.setTotalAmount(newTotal);
        importOrder.setNote(note);
        importOrder.setStatus("Finalized");
        importRepository.save(importOrder);
    }

    @Transactional
    public void confirmAndStockIn(Integer importId) {
        Import importOrder = importRepository.findById(importId)
                .orElseThrow(() -> new RuntimeException("Import record not found"));

        if (!"Finalized".equalsIgnoreCase(importOrder.getStatus())) {
            throw new RuntimeException("Import must be Finalized by Admin before stock in");
        }

        for (ImportDetail detail : importOrder.getDetails()) {
            Book product = detail.getProduct();
            int receivedQty = (detail.getReceivedQuantity() != null) ? detail.getReceivedQuantity() : 0;
            
            int currentStock = (product.getStock() != null ? product.getStock() : 0);
            product.setStock(currentStock + receivedQty);
            bookRepository.save(product);
        }

        importOrder.setStatus("Completed");
        importRepository.save(importOrder);
    }

    @Transactional
    public void cancelImport(Integer id) {
        Import importOrder = importRepository.findById(id).orElseThrow();
        importOrder.setStatus("Cancelled");
        importRepository.save(importOrder);
    }
}
