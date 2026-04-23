package com.bookstore.service;

import com.bookstore.model.Supplier;
import com.bookstore.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;

    // Chỉ lấy những nhà cung cấp đang Active để hiển thị
    public List<Supplier> getAllActiveSuppliers() {
        return supplierRepository.findAll().stream()
                .filter(s -> "Active".equalsIgnoreCase(s.getStatus()))
                .collect(Collectors.toList());
    }

    public List<Supplier> getAllSuppliers() {
        return supplierRepository.findAll();
    }

    public Optional<Supplier> getSupplierById(Integer id) {
        return supplierRepository.findById(id);
    }

    public Supplier saveSupplier(Supplier supplier) {
        if (supplier.getStatus() == null) {
            supplier.setStatus("Active");
        }
        return supplierRepository.save(supplier);
    }

    // Xóa mềm: Chuyển sang Inactive
    public void deleteSoft(Integer id) {
        supplierRepository.findById(id).ifPresent(supplier -> {
            supplier.setStatus("Inactive");
            supplierRepository.save(supplier);
        });
    }
}
