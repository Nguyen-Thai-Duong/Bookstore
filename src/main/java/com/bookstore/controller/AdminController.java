package com.bookstore.controller;

import com.bookstore.model.Book;
import com.bookstore.model.Category;
import com.bookstore.model.Order;
import com.bookstore.model.User;
import com.bookstore.model.Voucher;
import com.bookstore.repository.ReviewRepository;
import com.bookstore.service.BookService;
import com.bookstore.service.CategoryService;
import com.bookstore.service.OrderService;
import com.bookstore.service.RoleService;
import com.bookstore.service.UserService;
import com.bookstore.service.VoucherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private BookService bookService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private UserService userService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private VoucherService voucherService;

    @Autowired
    private ReviewRepository reviewRepository;

    @GetMapping
    public String dashboard(Model model) {
        // Get statistics
        var allBooks = bookService.getAllBooks();
        var allCategories = categoryService.getAllCategories();
        var allUsers = userService.getAllUsers();

        // Calculate total stock
        int totalStock = allBooks.stream()
                .mapToInt(book -> book.getStock() != null ? book.getStock() : 0)
                .sum();

        // Get recent books (last 5)
        var recentBooks = allBooks.stream()
                .limit(5)
                .toList();

        var latestReviews = reviewRepository.findAllByOrderByCreatedAtDesc();

        // Add attributes to model
        model.addAttribute("totalBooks", allBooks.size());
        model.addAttribute("totalCategories", allCategories.size());
        model.addAttribute("totalStock", totalStock);
        model.addAttribute("totalUsers", allUsers.size());
        model.addAttribute("recentBooks", recentBooks);
        model.addAttribute("categories", allCategories);
        model.addAttribute("latestReviews", latestReviews);

        return "admin/dashboard";
    }

    @GetMapping("/books")
    public String listBooks(Model model) {
        model.addAttribute("books", bookService.getAllBooks());
        return "admin/books";
    }

    @GetMapping("/books/new")
    public String showCreateBookForm(Model model) {
        model.addAttribute("book", new Book());
        model.addAttribute("categories", categoryService.getAllCategories());
        return "admin/books/form";
    }

    @GetMapping("/books/edit/{id}")
    public String showEditBookForm(@PathVariable Long id, Model model) {
        bookService.getBookById(id).ifPresent(book -> {
            model.addAttribute("book", book);
            model.addAttribute("categories", categoryService.getAllCategories());
        });
        return "admin/books/form";
    }

    @PostMapping("/books/save")
    public String saveBook(@ModelAttribute Book book,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile) {
        final String[] existingImageUrl = { null };

        if (book.getId() != null) {
            bookService.getBookById(book.getId()).ifPresent(existingBook -> {
                existingImageUrl[0] = existingBook.getImageUrl();
                if (book.getCreatedAt() == null) {
                    book.setCreatedAt(existingBook.getCreatedAt());
                }
                if (book.getImageUrl() == null || book.getImageUrl().isBlank()) {
                    book.setImageUrl(existingBook.getImageUrl());
                }
            });
        }

        if (book.getImageUrl() != null) {
            book.setImageUrl(book.getImageUrl().trim());
        }

        if (imageFile != null && !imageFile.isEmpty()) {
            try {
                // Nếu có upload file thì ưu tiên dùng file thay cho URL.
                String uploadedImageUrl = storeBookImage(imageFile, existingImageUrl[0]);
                book.setImageUrl(uploadedImageUrl);
            } catch (IOException e) {
                throw new RuntimeException("Không thể lưu ảnh sách", e);
            }
        }

        bookService.saveBook(book);
        return "redirect:/admin/books";
    }

    private String storeBookImage(MultipartFile imageFile, String oldImageUrl) throws IOException {
        Path uploadDir = Paths.get("images", "books").toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);

        String originalName = imageFile.getOriginalFilename() != null ? imageFile.getOriginalFilename() : "book-image";
        String extension = extractExtension(originalName);
        String fileName = UUID.randomUUID() + extension;

        Path targetPath = uploadDir.resolve(fileName).normalize();
        Files.copy(imageFile.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        deleteOldBookImageIfLocal(oldImageUrl);
        return "/images/books/" + fileName;
    }

    private void deleteOldBookImageIfLocal(String oldImageUrl) {
        if (oldImageUrl == null || !oldImageUrl.startsWith("/images/books/")) {
            return;
        }

        String oldFileName = oldImageUrl.substring("/images/books/".length());
        Path oldPath = Paths.get("images", "books", oldFileName).toAbsolutePath().normalize();
        try {
            Files.deleteIfExists(oldPath);
        } catch (IOException ignored) {
            // Keep update flow stable even if old image cleanup fails.
        }
    }

    private String extractExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return ".jpg";
        }

        String extension = fileName.substring(dotIndex).toLowerCase(Locale.ROOT);
        if (extension.equals(".png") || extension.equals(".jpg") || extension.equals(".jpeg")
                || extension.equals(".gif") || extension.equals(".webp")) {
            return extension;
        }

        return ".jpg";
    }

    @GetMapping("/books/delete/{id}")
    public String deleteBook(@PathVariable Long id) {
        bookService.deleteBook(id);
        return "redirect:/admin/books";
    }

    @GetMapping("/books/search")
    public String searchBooks(@RequestParam(required = false) String title,
            @RequestParam(required = false) String author,
            Model model) {
        var books = bookService.getAllBooks();

        if (title != null && !title.isEmpty()) {
            books = books.stream()
                    .filter(book -> book.getTitle().toLowerCase().contains(title.toLowerCase()))
                    .toList();
        }

        if (author != null && !author.isEmpty()) {
            books = books.stream()
                    .filter(book -> book.getAuthor().toLowerCase().contains(author.toLowerCase()))
                    .toList();
        }

        model.addAttribute("books", books);
        return "admin/books";
    }

    @GetMapping("/books/{id}")
    public String viewBook(@PathVariable Long id, Model model) {
        bookService.getBookById(id).ifPresent(book -> model.addAttribute("book", book));
        return "admin/books/view";
    }

    @GetMapping("/categories")
    public String listCategories(Model model) {
        model.addAttribute("categories", categoryService.getAllCategories());
        return "admin/categories";
    }

    @GetMapping("/categories/new")
    public String showCreateCategoryForm(Model model) {
        model.addAttribute("category", new Category());
        return "admin/categories/form";
    }

    @GetMapping("/categories/edit/{id}")
    public String showEditCategoryForm(@PathVariable Long id, Model model) {
        categoryService.getCategoryById(id).ifPresent(category -> model.addAttribute("category", category));
        return "admin/categories/form";
    }

    @PostMapping("/categories/save")
    public String saveCategory(@ModelAttribute Category category) {
        categoryService.saveCategory(category);
        return "redirect:/admin/categories";
    }

    @GetMapping("/categories/delete/{id}")
    public String deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return "redirect:/admin/categories";
    }

    @GetMapping("/categories/{id}")
    public String viewCategory(@PathVariable Long id, Model model) {
        categoryService.getCategoryById(id).ifPresent(category -> model.addAttribute("category", category));
        return "admin/categories/view";
    }

    @GetMapping("/users")
    public String listUsers(Model model) {
        model.addAttribute("users", userService.getAllUsers());
        model.addAttribute("roles", roleService.getAllRoles());
        return "admin/users";
    }

    @GetMapping("/users/{id}")
    public String viewUser(@PathVariable Long id, Model model) {
        userService.getUserById(id).ifPresent(user -> model.addAttribute("user", user));
        return "admin/users/view";
    }

    @GetMapping("/users/new")
    public String showCreateUserForm(Model model) {
        model.addAttribute("user", new User());
        model.addAttribute("roles", roleService.getAllRoles());
        return "admin/users/form";
    }

    @GetMapping("/users/edit/{id}")
    public String showEditUserForm(@PathVariable Long id, Model model) {
        userService.getUserById(id).ifPresent(user -> {
            model.addAttribute("user", user);
            model.addAttribute("roles", roleService.getAllRoles());
        });
        return "admin/users/form";
    }

    @PostMapping("/users/save")
    public String saveUser(@ModelAttribute User user) {
        userService.saveUser(user);
        return "redirect:/admin/users";
    }

    @GetMapping("/users/toggle-status/{id}")
    public String toggleUserStatus(@PathVariable Long id) {
        userService.getUserById(id).ifPresent(user -> {
            if ("Active".equals(user.getStatus())) {
                user.setStatus("Inactive");
            } else {
                user.setStatus("Active");
            }
            userService.saveUser(user);
        });
        return "redirect:/admin/users";
    }

    @GetMapping("/users/search")
    public String searchUsers(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String roleId,
            Model model) {
        var users = userService.getAllUsers();

        if (keyword != null && !keyword.isEmpty()) {
            users = users.stream()
                    .filter(user -> user.getFullName().toLowerCase().contains(keyword.toLowerCase())
                            || user.getEmail().toLowerCase().contains(keyword.toLowerCase()))
                    .toList();
        }

        if (status != null && !status.isEmpty() && !status.equals("Tất cả trạng thái")) {
            users = users.stream()
                    .filter(user -> user.getStatus().equals(status))
                    .toList();
        }

        if (roleId != null && !roleId.isEmpty() && !roleId.equals("Tất cả vai trò")) {
            try {
                Long rid = Long.parseLong(roleId);
                users = users.stream()
                        .filter(user -> user.getRole() != null && user.getRole().getId().equals(rid))
                        .toList();
            } catch (NumberFormatException e) {
                // Ignore invalid role ID
            }
        }

        model.addAttribute("users", users);
        model.addAttribute("roles", roleService.getAllRoles());
        return "admin/users";
    }

    @GetMapping("/orders")
    public String listOrders(Model model) {
        model.addAttribute("orders", orderService.getAllOrders());
        return "admin/orders";
    }

    @GetMapping("/orders/{id}")
    public String viewOrder(@PathVariable Long id, Model model) {
        var orderOptional = orderService.getOrderById(id);
        if (orderOptional.isEmpty()) {
            return "redirect:/admin/orders";
        }
        model.addAttribute("order", orderOptional.get());
        return "admin/orders/view";
    }

    @GetMapping("/orders/edit/{id}")
    public String showEditOrderForm(@PathVariable Long id, Model model) {
        var orderOptional = orderService.getOrderById(id);
        if (orderOptional.isEmpty()) {
            return "redirect:/admin/orders";
        }
        model.addAttribute("order", orderOptional.get());
        return "admin/orders/form";
    }

    @PostMapping("/orders/save")
    public String saveOrder(@ModelAttribute Order order) {
        orderService.saveOrder(order);
        return "redirect:/admin/orders";
    }

    @GetMapping("/orders/search")
    public String searchOrders(@RequestParam(required = false) String status,
            @RequestParam(required = false) String orderId,
            Model model) {
        var orders = orderService.getAllOrders();

        if (status != null && !status.isEmpty() && !status.equals("Tất cả trạng thái")) {
            orders = orders.stream()
                    .filter(order -> order.getStatus().equals(status))
                    .toList();
        }

        if (orderId != null && !orderId.isEmpty()) {
            try {
                Long id = Long.parseLong(orderId);
                orders = orders.stream()
                        .filter(order -> order.getId().equals(id))
                        .toList();
            } catch (NumberFormatException e) {
                // Ignore invalid order ID
            }
        }

        model.addAttribute("orders", orders);
        return "admin/orders";
    }

    @GetMapping("/vouchers")
    public String listVouchers(Model model) {
        model.addAttribute("vouchers", voucherService.getAllVouchers());
        return "admin/vouchers";
    }

    @GetMapping("/vouchers/{id}")
    public String viewVoucher(@PathVariable Long id, Model model) {
        voucherService.getVoucherById(id).ifPresent(voucher -> model.addAttribute("voucher", voucher));
        return "admin/vouchers/view";
    }

    @GetMapping("/vouchers/new")
    public String showCreateVoucherForm(Model model) {
        model.addAttribute("voucher", new Voucher());
        return "admin/vouchers/form";
    }

    @GetMapping("/vouchers/edit/{id}")
    public String showEditVoucherForm(@PathVariable Long id, Model model) {
        voucherService.getVoucherById(id).ifPresent(voucher -> model.addAttribute("voucher", voucher));
        return "admin/vouchers/form";
    }

    @PostMapping("/vouchers/save")
    public String saveVoucher(@ModelAttribute Voucher voucher) {
        voucherService.saveVoucher(voucher);
        return "redirect:/admin/vouchers";
    }

    @GetMapping("/vouchers/delete/{id}")
    public String deleteVoucher(@PathVariable Long id) {
        voucherService.deleteVoucher(id);
        return "redirect:/admin/vouchers";
    }

    @GetMapping("/vouchers/toggle-status/{id}")
    public String toggleVoucherStatus(@PathVariable Long id) {
        voucherService.getVoucherById(id).ifPresent(voucher -> {
            if ("Active".equals(voucher.getStatus())) {
                voucher.setStatus("Inactive");
            } else {
                voucher.setStatus("Active");
            }
            voucherService.saveVoucher(voucher);
        });
        return "redirect:/admin/vouchers";
    }

    @GetMapping("/vouchers/search")
    public String searchVouchers(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            Model model) {
        var vouchers = voucherService.getAllVouchers();

        if (keyword != null && !keyword.isEmpty()) {
            vouchers = vouchers.stream()
                    .filter(voucher -> voucher.getCode().toLowerCase().contains(keyword.toLowerCase()))
                    .toList();
        }

        if (status != null && !status.isEmpty() && !status.equals("Tất cả trạng thái")) {
            vouchers = vouchers.stream()
                    .filter(voucher -> voucher.getStatus().equals(status))
                    .toList();
        }

        model.addAttribute("vouchers", vouchers);
        return "admin/vouchers";
    }

    // API endpoint for revenue statistics
    @GetMapping("/api/revenue-statistics")
    @ResponseBody
    public Map<String, Object> getRevenueStatistics(
            @RequestParam(defaultValue = "day") String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start;

        // Default date ranges based on period
        if (startDate == null || startDate.isEmpty()) {
            switch (period.toLowerCase()) {
                case "day":
                    start = end.minusDays(30); // Last 30 days
                    break;
                case "month":
                    start = end.minusMonths(12); // Last 12 months
                    break;
                case "year":
                    start = end.minusYears(5); // Last 5 years
                    break;
                default:
                    start = end.minusDays(30);
            }
        } else {
            start = parseDateValue(startDate, false);
        }

        if (endDate != null && !endDate.isEmpty()) {
            end = parseDateValue(endDate, true);
        }

        if (end.isBefore(start)) {
            LocalDateTime temp = start;
            start = end;
            end = temp;
        }

        return orderService.getRevenueStatistics(period, start, end);
    }

    private LocalDateTime parseDateValue(String value, boolean endOfDay) {
        try {
            return LocalDateTime.parse(value);
        } catch (Exception ignored) {
            LocalDate localDate = LocalDate.parse(value);
            return endOfDay ? localDate.atTime(LocalTime.MAX) : localDate.atStartOfDay();
        }
    }
}
