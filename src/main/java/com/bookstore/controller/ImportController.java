package com.bookstore.controller;

import com.bookstore.model.Book;
import com.bookstore.model.ImportReceipt;
// Thay bằng Repository/Service thực tế của bạn
import com.bookstore.repository.BookRepository;
import com.bookstore.repository.ImportReceiptRepository;
import com.bookstore.model.ImportDetail;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/admin/imports")
public class ImportController {

    // Ví dụ dùng Repository, nếu project bạn dùng Service thì đổi lại tương ứng nhé
    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private ImportReceiptRepository importReceiptRepository;

    // ==========================================
    // 1. Giao diện Danh sách Import (Có Filter + Phân Trang)
    // ==========================================
    @GetMapping
    public String listImports(Model model,
                              @RequestParam(required = false) String search,
                              @RequestParam(required = false) String date,
                              @RequestParam(required = false) String status,
                              @RequestParam(required = false) BigDecimal minPrice,
                              @RequestParam(required = false) BigDecimal maxPrice,
                              @RequestParam(defaultValue = "0") int page) {

        // Tạo biến phân trang: Lấy trang hiện tại, 10 items/trang, sắp xếp Ngày tạo mới nhất lên đầu
        Pageable pageable = PageRequest.of(page, 10, Sort.by("createdAt").descending());

        // Xử lý chuỗi rỗng từ filter
        if (search != null && search.trim().isEmpty()) search = null;
        if (status != null && status.trim().isEmpty()) status = null;

        // Gọi DB lấy dữ liệu (Page)
        Page<ImportReceipt> importPage = importReceiptRepository.searchImports(search, status, minPrice, maxPrice, pageable);

        // Gởi sang giao diện
        model.addAttribute("importPage", importPage);

        return "admin/imports";
    }

    // ==========================================
    // 2. Giao diện Tạo mới Import
    // ==========================================
    @GetMapping("/create")
    public String createImportForm(Model model) {
        // Lấy danh sách sách truyền vào dropdown
        List<Book> books = bookRepository.findAll();
        model.addAttribute("books", books);

        return "admin/imports/form"; // Trỏ đúng file form.html
    }

    // ==========================================
    // 3. API Xử lý Lưu Đơn Nhập (Nhận JSON từ form.html)
    // ==========================================
    @PostMapping("/create")
    @ResponseBody
    public ResponseEntity<?> saveImport(@RequestBody List<ImportItemDTO> items) {
        try {
            if (items == null || items.isEmpty()) {
                return ResponseEntity.badRequest().body("List is empty");
            }

            // ĐÃ BỎ COMMENT ĐỂ CODE CHẠY THỰC TẾ
            ImportReceipt receipt = new ImportReceipt();
            receipt.setUsername("admin"); // Tạm gán admin, sau này lấy từ user đăng nhập

            int totalQuantity = 0;
            BigDecimal totalAmount = BigDecimal.ZERO;

            for (ImportItemDTO item : items) {
                Book book = bookRepository.findById(item.getBookId()).orElseThrow();

                ImportDetail detail = new ImportDetail();
                detail.setImportReceipt(receipt);
                detail.setBook(book);
                detail.setQuantity(item.getQuantity());
                detail.setPrice(item.getPrice());

                BigDecimal subTotal = item.getPrice().multiply(new BigDecimal(item.getQuantity()));
                detail.setSubtotal(subTotal);

                receipt.getDetails().add(detail);

                totalQuantity += item.getQuantity();
                totalAmount = totalAmount.add(subTotal);
            }

            receipt.setTotalQuantity(totalQuantity);
            receipt.setTotalAmount(totalAmount);

            // Lưu vào DB (Spring JPA sẽ lưu cả Receipt và các Detail nhờ CascadeType.ALL)
            importReceiptRepository.save(receipt);

            return ResponseEntity.ok("Import created successfully!");
        } catch (Exception e) {
            e.printStackTrace(); // In lỗi ra console để dễ debug nếu có
            return ResponseEntity.internalServerError().body("Error creating import: " + e.getMessage());
        }
    }
// Đừng quên import ImportStatus nếu bạn dùng Enum nhé
    // import com.bookstore.model.enums.ImportStatus;
    // import com.bookstore.model.ImportDetail;

    // ==========================================
    // 4. Giao diện Xem chi tiết (Nút View)
    // ==========================================
    @GetMapping("/{id}")
    public String viewImportDetails(@PathVariable Long id, Model model) {
        // Query lấy đơn nhập từ Database
        ImportReceipt importReceipt = importReceiptRepository.findById(id).orElse(null);

        if (importReceipt == null) {
            return "redirect:/admin/imports"; // Nếu không tìm thấy, quay lại trang list
        }

        // Đẩy dữ liệu sang view.html
        model.addAttribute("importReceipt", importReceipt);

        return "admin/imports/view";
    }

    // ==========================================
    // 5. API Xử lý update Status (Nút Approve/Cancel)
    // ==========================================
    @PostMapping("/{id}/status")
    public String updateStatus(@PathVariable Long id, @RequestParam String action) {
        ImportReceipt receipt = importReceiptRepository.findById(id).orElse(null);

        if (receipt != null && "PENDING".equals(receipt.getStatus().name())) {

            // ĐÃ SỬA THÀNH "approve"
            if ("approve".equals(action)) {

                // ĐÃ SỬA THÀNH ImportStatus.APPROVED
                receipt.setStatus(com.bookstore.model.enums.ImportStatus.APPROVED);

                for (ImportDetail detail : receipt.getDetails()) {
                    Book book = detail.getBook();
                    int currentStock = book.getStock() != null ? book.getStock() : 0;
                    book.setStock(currentStock + detail.getQuantity());

                    bookRepository.save(book);
                }

            } else if ("cancel".equals(action)) {
                receipt.setStatus(com.bookstore.model.enums.ImportStatus.CANCELLED);
            }

            importReceiptRepository.save(receipt);
        }

        return "redirect:/admin/imports";
    }

    // ==========================================
    // DTO để nhận dữ liệu JSON từ Client (Javascript)
    // ==========================================
    public static class ImportItemDTO {
        private Long bookId;
        private String bookName;
        private Integer quantity;
        private BigDecimal price;

        // Getters and Setters
        public Long getBookId() { return bookId; }
        public void setBookId(Long bookId) { this.bookId = bookId; }

        public String getBookName() { return bookName; }
        public void setBookName(String bookName) { this.bookName = bookName; }

        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }

        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
    }
}