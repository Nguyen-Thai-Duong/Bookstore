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
import java.util.Locale;
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

    public boolean hasOpenImportForProduct(Long productId) {
        if (productId == null) {
            return false;
        }

        return importRepository.findAll().stream()
                .filter(importOrder -> importOrder != null && !isClosedImportStatus(importOrder.getStatus()))
                .anyMatch(importOrder -> importOrder.getDetails() != null
                        && importOrder.getDetails().stream().anyMatch(detail -> detail != null
                                && detail.getProduct() != null
                                && productId.equals(detail.getProduct().getId())));
    }

    @Transactional
    public Import saveImport(Import importOrder) {
        if (importOrder != null && importOrder.getDetails() != null) {
            for (ImportDetail detail : importOrder.getDetails()) {
                if (detail == null || detail.getProduct() == null || detail.getProduct().getId() == null) {
                    continue;
                }
                Book latestProduct = bookRepository.findById(detail.getProduct().getId())
                        .orElseThrow(() -> new RuntimeException("Product not found for import detail"));
                if (latestProduct.isDiscontinued()) {
                    throw new RuntimeException(
                            "Import blocked because product is Discontinued: " + latestProduct.getTitle()
                                    + ". Please cancel this import.");
                }
            }
        }
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
            if (product != null && product.isDiscontinued()) {
                throw new RuntimeException(
                        "Cannot stock in discontinued product: " + product.getTitle()
                                + ". Please cancel this import.");
            }
            int receivedQty = (detail.getReceivedQuantity() != null) ? detail.getReceivedQuantity() : 0;
            
            int currentStock = (product.getStock() != null ? product.getStock() : 0);
            product.setStock(currentStock + receivedQty);
            bookRepository.save(product);
        }

        importOrder.setStatus("Completed");
        importRepository.save(importOrder);
    }

    @Transactional
    public void cancelImport(Integer id, String note) {
        Import importOrder = importRepository.findById(id).orElseThrow();
        importOrder.setStatus("Cancelled");
        importOrder.setNote(note);
        importRepository.save(importOrder);
    }

    private boolean isClosedImportStatus(String status) {
        if (status == null) {
            return false;
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        return "completed".equals(normalized) || "cancelled".equals(normalized);
    }
}
