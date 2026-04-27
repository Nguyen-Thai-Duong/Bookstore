package com.bookstore.controller;

import com.bookstore.dto.BookDTO;
import com.bookstore.dto.CategoryDTO;
import com.bookstore.dto.OrderDTO;
import com.bookstore.dto.ReviewDTO;
import com.bookstore.dto.UserDTO;
import com.bookstore.dto.UserFormDTO;
import com.bookstore.dto.VoucherDTO;
import com.bookstore.model.Book;
import com.bookstore.model.Category;
import com.bookstore.model.Order;
import com.bookstore.model.User;
import com.bookstore.model.Voucher;
import com.bookstore.repository.BookRepository;
import com.bookstore.repository.CartItemRepository;
import com.bookstore.repository.OrderDetailRepository;
import com.bookstore.repository.ProductTypeRepository;
import com.bookstore.repository.ReviewRepository;
import com.bookstore.service.BookService;
import com.bookstore.service.CategoryService;
import com.bookstore.service.ImportService;
import com.bookstore.service.OrderService;
import com.bookstore.service.RoleService;
import com.bookstore.service.UserService;
import com.bookstore.service.VoucherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final Long BOOK_PRODUCT_TYPE_ID = 1L;

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

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private OrderDetailRepository orderDetailRepository;

    @Autowired
    private ProductTypeRepository productTypeRepository;

    @Autowired
    private ImportService importService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @GetMapping
    public String dashboard(Model model) {
        // Get statistics
        var allBooks = bookService.getProductsByProductType(BOOK_PRODUCT_TYPE_ID).stream()
                .filter(book -> book != null && !book.isDiscontinued() && "Active".equalsIgnoreCase(book.getStatus()))
                .toList();
        var allStationery = bookService.getProductsByProductType(2L).stream()
                .filter(item -> item != null && !item.isDiscontinued() && "Active".equalsIgnoreCase(item.getStatus()))
                .toList();

        var allActiveProducts = bookRepository.findAll().stream()
                .filter(product -> product != null && !product.isDiscontinued()
                        && "Active".equalsIgnoreCase(product.getStatus()))
                .toList();

        var allProductTypes = productTypeRepository.findAll().stream()
                .sorted((left, right) -> {
                    if (left.getId() == null && right.getId() == null) {
                        return 0;
                    }
                    if (left.getId() == null) {
                        return 1;
                    }
                    if (right.getId() == null) {
                        return -1;
                    }
                    return left.getId().compareTo(right.getId());
                })
                .toList();
        var allUsers = userService.getAllUsers();

        // Calculate total stock
        int totalStock = allActiveProducts.stream()
                .mapToInt(product -> product.getStock() != null ? product.getStock() : 0)
                .sum();

        // Get recent books (last 5)
        var recentBooks = allBooks.stream()
                .limit(5)
                .map(BookDTO::fromEntity)
                .toList();

        var recentStationery = allStationery.stream()
                .limit(5)
                .map(BookDTO::fromEntity)
                .toList();

        var latestReviews = reviewRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(ReviewDTO::fromEntity)
                .toList();

        // Add attributes to model
        model.addAttribute("totalProducts", allBooks.size() + allStationery.size());
        model.addAttribute("totalProductTypes", allProductTypes.size());
        model.addAttribute("totalStock", totalStock);
        model.addAttribute("totalUsers", allUsers.size());
        model.addAttribute("recentBooks", recentBooks);
        model.addAttribute("recentStationery", recentStationery);
        model.addAttribute("productTypes", allProductTypes.stream()
                .map(type -> {
                    var row = new LinkedHashMap<String, Object>();
                    row.put("id", type.getId());
                    row.put("name", type.getName());
                    long count = 0;
                    if (type.getId() != null) {
                        count = allActiveProducts.stream()
                                .filter(p -> p.getCategory() != null
                                        && p.getCategory().getProductType() != null
                                        && type.getId().equals(p.getCategory().getProductType().getId()))
                                .count();
                    }
                    row.put("productCount", count);
                    return row;
                })
                .toList());
        model.addAttribute("latestReviews", latestReviews);

        return "admin/dashboard";
    }

    @GetMapping("/books")
    public String listBooks(Model model) {
        model.addAttribute("books", bookRepository.findByProductType(BOOK_PRODUCT_TYPE_ID).stream()
                .filter(book -> book != null && !book.isDiscontinued() && "Active".equalsIgnoreCase(book.getStatus()))
                .map(BookDTO::fromEntity)
                .toList());
        return "admin/books";
    }

    @GetMapping("/books/new")
    public String showCreateBookForm(Model model) {
        BookDTO dto = new BookDTO();
        dto.setProductTypeId(BOOK_PRODUCT_TYPE_ID);
        populateBookFormModel(model, dto);
        return "admin/books/add-book";
    }

    @GetMapping("/books/edit/{id}")
    public String showEditBookForm(@PathVariable Long id, Model model) {
        var bookOpt = bookService.getBookById(id);
        if (bookOpt.isEmpty()) {
            return "redirect:/admin/books";
        }
        Book book = bookOpt.get();
        if (book.isDiscontinued() || !"Active".equalsIgnoreCase(book.getStatus())) {
            return "redirect:/admin/books";
        }
        populateBookFormModel(model, BookDTO.fromEntity(book));
        return "admin/books/add-book";
    }

    @PostMapping("/books/save")
    public String saveBook(@Valid @ModelAttribute("book") BookDTO bookDto,
            BindingResult bindingResult,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            Map<String, String> validationErrors = new LinkedHashMap<>();
            collectBindingErrors(bindingResult, validationErrors);
            validationErrors.putAll(validateBookFields(bookDto));

            Long categoryId = bookDto.getCategory() != null ? bookDto.getCategory().getId() : null;
            if (categoryId == null) {
                validationErrors.putIfAbsent("category.id", "Category is required.");
            }

            Optional<Category> categoryOpt = categoryId == null ? Optional.<Category>empty()
                    : categoryService.getCategoryById(categoryId);
            if (categoryId != null && (categoryOpt.isEmpty()
                    || categoryOpt.get().getProductType() == null
                    || categoryOpt.get().getProductType().getId() == null
                    || !BOOK_PRODUCT_TYPE_ID.equals(categoryOpt.get().getProductType().getId()))) {
                validationErrors.put("category.id", "Invalid category for Book product type.");
            }

            if (!validationErrors.isEmpty()) {
                model.addAttribute("bookError", "Please fix the highlighted fields.");
                model.addAttribute("validationErrors", validationErrors);
                populateBookFormModel(model, bookDto);
                return "admin/books/add-book";
            }

            Book book = bookDto.toEntity();
            final String[] existingImageUrl = { null };
            if (book.getPublisher() != null) {
                book.setPublisher(book.getPublisher().trim());
            }

            if (book.getStock() == null) {
                book.setStock(0);
            }

            if (book.getDescription() == null) {
                book.setDescription("");
            }

            if (book.getStatus() == null || book.getStatus().isBlank()) {
                book.setStatus("Active");
            }

            book.setCategory(categoryOpt.get());

            if (book.getId() != null) {
                bookService.getBookById(book.getId()).ifPresent(existingBook -> {
                    existingImageUrl[0] = existingBook.getImageUrl();
                    if (book.getCreatedAt() == null) {
                        book.setCreatedAt(existingBook.getCreatedAt());
                    }
                });
            }

            String rawSubmittedImageUrl = book.getImageUrl() == null ? "" : book.getImageUrl().trim();
            boolean explicitClearImage = "none".equalsIgnoreCase(rawSubmittedImageUrl)
                    || "noimage".equalsIgnoreCase(rawSubmittedImageUrl);
            String submittedImageUrl = explicitClearImage ? "" : rawSubmittedImageUrl;
            String existingUrl = existingImageUrl[0] == null ? "" : existingImageUrl[0].trim();
            boolean hasUploadedFile = imageFile != null && !imageFile.isEmpty();
            boolean hasSubmittedUrl = !submittedImageUrl.isBlank();
            boolean isEditing = book.getId() != null;
            boolean submittedUrlChanged = !submittedImageUrl.equals(existingUrl);

            if (hasUploadedFile && hasSubmittedUrl) {
                // If both are submitted in one request:
                // - New URL + upload => URL wins
                // - URL is just unchanged prefilled value during edit => upload wins (newest input)
                if (isEditing && !submittedUrlChanged) {
                    try {
                        String uploadedImageUrl = storeBookImage(imageFile, existingImageUrl[0]);
                        book.setImageUrl(uploadedImageUrl);
                    } catch (IOException e) {
                        model.addAttribute("bookError", "Unable to upload image. Please try again.");
                        populateBookFormModel(model, bookDto);
                        return "admin/books/add-book";
                    }
                } else {
                    book.setImageUrl(submittedImageUrl);
                    if (existingImageUrl[0] != null && !existingImageUrl[0].equals(book.getImageUrl())) {
                        deleteOldBookImageIfLocal(existingImageUrl[0]);
                    }
                }
            } else if (hasUploadedFile) {
                try {
                    String uploadedImageUrl = storeBookImage(imageFile, existingImageUrl[0]);
                    book.setImageUrl(uploadedImageUrl);
                } catch (IOException e) {
                    model.addAttribute("bookError", "Unable to upload image. Please try again.");
                    populateBookFormModel(model, bookDto);
                    return "admin/books/add-book";
                }
            } else if (hasSubmittedUrl) {
                book.setImageUrl(submittedImageUrl);
                if (existingImageUrl[0] != null && !existingImageUrl[0].equals(book.getImageUrl())) {
                    deleteOldBookImageIfLocal(existingImageUrl[0]);
                }
            } else if (explicitClearImage) {
                if (existingImageUrl[0] != null && !existingImageUrl[0].isBlank()) {
                    deleteOldBookImageIfLocal(existingImageUrl[0]);
                }
                book.setImageUrl("Noimage");
            } else if (isEditing) {
                book.setImageUrl(existingImageUrl[0]);
            } else {
                book.setImageUrl("Noimage");
            }

            if (book.getImageUrl() == null || book.getImageUrl().isBlank()) {
                book.setImageUrl("Noimage");
            }

            try {
                bookService.saveBook(book);
            } catch (RuntimeException e) {
                model.addAttribute("bookError", "Unable to save book. Please check your input values.");
                populateBookFormModel(model, bookDto);
                return "admin/books/add-book";
            }

            return "redirect:/admin/books";
        } catch (Exception e) {
            model.addAttribute("bookError", "Unable to save book right now. Please try again.");
            populateBookFormModel(model, bookDto);
            return "admin/books/add-book";
        }
    }

    private void populateBookFormModel(Model model, BookDTO dto) {
        if (dto.getProductTypeId() == null) {
            dto.setProductTypeId(BOOK_PRODUCT_TYPE_ID);
        }
        model.addAttribute("book", dto);
        model.addAttribute("categories",
                categoryService.getCategoriesByProductType(BOOK_PRODUCT_TYPE_ID).stream().map(CategoryDTO::fromEntity)
                        .toList());
    }

    private void collectBindingErrors(BindingResult bindingResult, Map<String, String> validationErrors) {
        for (FieldError fieldError : bindingResult.getFieldErrors()) {
            String field = fieldError.getField();
            if (validationErrors.containsKey(field)) {
                continue;
            }

            String message = fieldError.getDefaultMessage();
            if (fieldError.getCode() != null && fieldError.getCode().contains("typeMismatch")) {
                message = switch (field) {
                    case "publishedYear" -> "Published year must be a positive integer from 1450 to current year.";
                    case "widthCm" -> "Width (cm) must be a valid number.";
                    case "heightCm" -> "Height (cm) must be a valid number.";
                    case "thicknessCm" -> "Thickness (cm) must be a valid number.";
                    case "pageCount" -> "Page count must be a valid integer.";
                    case "price" -> "Price must be a valid number.";
                    default -> "Invalid value.";
                };
            }

            validationErrors.put(field, message);
        }
    }

    private Map<String, String> validateBookFields(BookDTO bookDto) {
        Map<String, String> errors = new LinkedHashMap<>();
        int currentYear = LocalDate.now().getYear();

        Integer publishedYear = bookDto.getPublishedYear();
        if (publishedYear == null) {
            errors.put("publishedYear", "Published year is required.");
        } else if (publishedYear < 1450 || publishedYear > currentYear) {
            errors.put("publishedYear", "Published year must be between 1450 and " + currentYear + ".");
        }

        String publisher = bookDto.getPublisher();
        if (publisher == null || publisher.trim().isEmpty()) {
            errors.put("publisher", "Publisher is required.");
        } else if (publisher.trim().length() > 150) {
            errors.put("publisher", "Publisher cannot exceed 150 characters.");
        }

        validateDimension(bookDto.getWidthCm(), "widthCm", "Width", errors, true);
        validateDimension(bookDto.getHeightCm(), "heightCm", "Height", errors, true);
        validateDimension(bookDto.getThicknessCm(), "thicknessCm", "Thickness", errors, true);

        Integer pageCount = bookDto.getPageCount();
        if (pageCount == null) {
            errors.put("pageCount", "Page count is required.");
        } else if (pageCount <= 0) {
            errors.put("pageCount", "Page count must be greater than 0.");
        }

        return errors;
    }

    private void validateDimension(BigDecimal value, String field, String label, Map<String, String> errors,
            boolean required) {
        if (value == null) {
            if (required) {
                errors.put(field, label + " (cm) is required.");
            }
            return;
        }

        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            errors.put(field, label + " (cm) must be greater than 0.");
            return;
        }

        if (value.compareTo(new BigDecimal("999.99")) > 0) {
            errors.put(field, label + " (cm) cannot exceed 999.99.");
            return;
        }

        if (value.scale() > 2) {
            errors.put(field, label + " (cm) supports up to 2 decimal places.");
        }
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
    public String deleteBook(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        var bookOptional = bookService.getBookById(id);
        if (bookOptional.isEmpty()) {
            redirectAttributes.addFlashAttribute("bookError", "Book not found");
            return "redirect:/admin/books";
        }

        Book book = bookOptional.get();
        if (importService.hasOpenImportForProduct(id)) {
            redirectAttributes.addFlashAttribute("bookError",
                    "Cannot delete this product because it exists in an import that is not stock-in completed yet. Please complete or cancel that import first.");
            return "redirect:/admin/books";
        }

        boolean hasBlockingOrder = orderDetailRepository.findByBookId(id).stream()
                .anyMatch(detail -> detail.getOrder() != null && isBlockingDeleteOrderStatus(detail.getOrder().getStatus()));
        if (hasBlockingOrder) {
            redirectAttributes.addFlashAttribute("bookError",
                    "Cannot delete this product because it exists in active orders (Pending/Confirmed/Shipping).");
            return "redirect:/admin/books";
        }

        boolean inAnyCart = cartItemRepository.existsByBook_Id(id);
        if (!book.isDiscontinued()) {
            book.markDiscontinued();
            bookService.saveBook(book);

            if (inAnyCart) {
                redirectAttributes.addFlashAttribute("bookSuccess",
                        "Product status changed to Discontinued. It is hidden from users now. After 2 minutes, click Delete again to remove only this product from all carts.");
            } else {
                redirectAttributes.addFlashAttribute("bookSuccess",
                        "Product status changed to Discontinued. It is hidden from users and can only be restored by setting Status=Active in database.");
            }
            return "redirect:/admin/books";
        }

        if (!inAnyCart) {
            redirectAttributes.addFlashAttribute("bookSuccess",
                    "Product is already Discontinued and hidden. No hard delete is performed.");
            return "redirect:/admin/books";
        }

        if (book.isCartCleanupWindowActive()) {
            redirectAttributes.addFlashAttribute("bookError",
                    "Product is in carts. Wait until the 2-minute window ends, then click Delete again to remove only this product from carts.");
            return "redirect:/admin/books";
        }

        long removedRows = cartItemRepository.deleteByBook_Id(id);
        redirectAttributes.addFlashAttribute("bookSuccess",
                "Product remains Discontinued. Removed from " + removedRows
                        + " cart line(s). Order history is preserved.");
        return "redirect:/admin/books";
    }

    private boolean isBlockingDeleteOrderStatus(String status) {
        String normalized = normalizeOrderStatus(status);
        return "pending".equals(normalized) || "confirmed".equals(normalized) || "shipping".equals(normalized);
    }

    @GetMapping("/books/search")
    public String searchBooks(@RequestParam(required = false) String title,
            @RequestParam(required = false) String author,
            Model model) {
        var books = bookRepository.findByProductType(BOOK_PRODUCT_TYPE_ID).stream()
                .filter(book -> book != null && !book.isDiscontinued() && "Active".equalsIgnoreCase(book.getStatus()))
                .toList();

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

        model.addAttribute("books", books.stream().map(BookDTO::fromEntity).toList());
        return "admin/books";
    }

    @GetMapping("/books/{id}")
    public String viewBook(@PathVariable Long id, Model model) {
        var bookOpt = bookService.getBookById(id);
        if (bookOpt.isEmpty()) {
            return "redirect:/admin/books";
        }
        Book book = bookOpt.get();
        if (book.isDiscontinued() || !"Active".equalsIgnoreCase(book.getStatus())) {
            return "redirect:/admin/books";
        }
        model.addAttribute("book", BookDTO.fromEntity(book));
        return "admin/books/view";
    }

    @GetMapping("/categories")
    public String listCategories(Model model) {
        model.addAttribute("categories", categoryService.getAllCategories().stream()
                .sorted((left, right) -> {
                    if (left.getId() == null && right.getId() == null) {
                        return 0;
                    }
                    if (left.getId() == null) {
                        return 1;
                    }
                    if (right.getId() == null) {
                        return -1;
                    }
                    return left.getId().compareTo(right.getId());
                })
                .map(category -> {
                    CategoryDTO dto = CategoryDTO.fromEntity(category);
                    dto.setBookCount(bookRepository.countByCategory_Id(category.getId()));
                    return dto;
                })
                .toList());
        return "admin/categories";
    }

    @GetMapping("/categories/new")
    public String showCreateCategoryForm(Model model) {
        model.addAttribute("category", new CategoryDTO());
        model.addAttribute("productTypes", productTypeRepository.findAll());
        return "admin/categories/form";
    }

    @GetMapping("/categories/edit/{id}")
    public String showEditCategoryForm(@PathVariable Long id, Model model) {
        categoryService.getCategoryById(id)
                .ifPresent(category -> model.addAttribute("category", CategoryDTO.fromEntity(category)));
        model.addAttribute("productTypes", productTypeRepository.findAll());
        return "admin/categories/form";
    }

    @PostMapping("/categories/save")
    public String saveCategory(@ModelAttribute("category") CategoryDTO categoryDto) {
        var category = categoryDto.toEntity();
        if (categoryDto.getProductTypeId() != null) {
            productTypeRepository.findById(categoryDto.getProductTypeId())
                    .ifPresent(category::setProductType);
        }
        categoryService.saveCategory(category);
        return "redirect:/admin/categories";
    }

    @GetMapping("/categories/delete/{id}")
    public String deleteCategory(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        long bookCount = bookRepository.countByCategory_Id(id);
        if (bookCount > 0) {
            redirectAttributes.addFlashAttribute("categoryError",
                    "Cannot delete category because it still contains products");
            return "redirect:/admin/categories";
        }

        categoryService.deleteCategory(id);
        redirectAttributes.addFlashAttribute("categorySuccess", "Category deleted successfully");
        return "redirect:/admin/categories";
    }

    @GetMapping("/categories/{id}")
    public String viewCategory(@PathVariable Long id, Model model) {
        categoryService.getCategoryById(id)
                .ifPresent(category -> model.addAttribute("category", category));
        return "admin/categories/view";
    }

    @GetMapping("/users")
    public String listUsers(Model model) {
        model.addAttribute("users", userService.getAllUsers().stream().map(UserDTO::fromEntity).toList());
        model.addAttribute("roles", roleService.getAllRoles());
        return "admin/users";
    }

    @GetMapping("/users/{id}")
    public String viewUser(@PathVariable Long id, Model model) {
        userService.getUserById(id).ifPresent(user -> model.addAttribute("user", UserDTO.fromEntity(user)));
        model.addAttribute("orders", orderService.getOrdersByUserId(id));
        return "admin/users/view";
    }

    @GetMapping("/users/new")
    public String showCreateUserForm(Model model) {
        model.addAttribute("user", new UserFormDTO());
        model.addAttribute("roles", roleService.getAllRoles());
        return "admin/users/form";
    }

    @GetMapping("/users/edit/{id}")
    public String showEditUserForm(@PathVariable Long id, Model model) {
        userService.getUserById(id).ifPresent(user -> {
            model.addAttribute("user", UserFormDTO.fromEntity(user));
            model.addAttribute("roles", roleService.getAllRoles());
        });
        return "admin/users/form";
    }

    @PostMapping("/users/save")
    public String saveUser(@ModelAttribute("user") UserFormDTO userForm) {
        String rawPassword = userForm.getPassword();
        String encodedPassword = rawPassword;

        if (userForm.getId() != null) {
            userService.getUserById(userForm.getId()).ifPresent(existingUser -> {
                if (userForm.getCreatedAt() == null) {
                    userForm.setCreatedAt(existingUser.getCreatedAt());
                }
            });
            if (rawPassword == null || rawPassword.isBlank()) {
                encodedPassword = userService.getUserById(userForm.getId())
                        .map(User::getPassword)
                        .orElse(null);
            } else {
                encodedPassword = passwordEncoder.encode(rawPassword);
            }
        } else if (rawPassword != null && !rawPassword.isBlank()) {
            encodedPassword = passwordEncoder.encode(rawPassword);
        }

        User user = userForm.toEntity(encodedPassword);
        if (userForm.getRole() != null && userForm.getRole().getId() != null) {
            roleService.getRoleById(userForm.getRole().getId()).ifPresent(user::setRole);
        }
        userService.saveUser(user);
        return "redirect:/admin/users";
    }

    @GetMapping("/users/toggle-status/{id}")
    public String toggleUserStatus(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        User currentUser = session == null ? null : (User) session.getAttribute("loggedInUser");

        if (currentUser == null || currentUser.getId() == null) {
            redirectAttributes.addFlashAttribute("userError", "You must be logged in to change user status.");
            return "redirect:/admin/users";
        }

        userService.getUserById(id).ifPresentOrElse(user -> {
            boolean isSelf = currentUser.getId().equals(user.getId());
            boolean isTargetAdmin = user.getRole() != null
                    && user.getRole().getRoleName() != null
                    && "admin".equalsIgnoreCase(user.getRole().getRoleName());

            if (isSelf) {
                redirectAttributes.addFlashAttribute("userError", "You cannot lock or unlock your own admin account.");
                return;
            }

            if (isTargetAdmin) {
                redirectAttributes.addFlashAttribute("userError",
                        "Admin accounts cannot be locked or unlocked from here.");
                return;
            }

            if ("Active".equals(user.getStatus())) {
                user.setStatus("Inactive");
            } else {
                user.setStatus("Active");
            }
            userService.saveUser(user);
        }, () -> redirectAttributes.addFlashAttribute("userError", "User not found."));

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

        if (status != null && !status.isEmpty() && !status.equals("All Status")) {
            users = users.stream()
                    .filter(user -> user.getStatus().equals(status))
                    .toList();
        }

        if (roleId != null && !roleId.isEmpty() && !roleId.equals("All Roles")) {
            try {
                Long rid = Long.parseLong(roleId);
                users = users.stream()
                        .filter(user -> user.getRole() != null && user.getRole().getId().equals(rid))
                        .toList();
            } catch (NumberFormatException e) {
                // Ignore invalid role ID
            }
        }

        model.addAttribute("users", users.stream().map(UserDTO::fromEntity).toList());
        model.addAttribute("roles", roleService.getAllRoles());
        return "admin/users";
    }

    @GetMapping("/orders")
    public String listOrders(Model model) {
        var orders = orderService.getAllOrders();
        model.addAttribute("orders", orders.stream().map(OrderDTO::fromEntity).toList());
        model.addAttribute("orderStatusOptions", buildOrderStatusOptions(orders));
        return "admin/orders";
    }

    @GetMapping("/orders/{id}")
    public String viewOrder(@PathVariable Long id, Model model) {
        var orderOptional = orderService.getOrderById(id);
        if (orderOptional.isEmpty()) {
            return "redirect:/admin/orders";
        }
        model.addAttribute("order", OrderDTO.fromEntity(orderOptional.get()));
        return "admin/orders/view";
    }

    @GetMapping("/orders/edit/{id}")
    public String showEditOrderForm(@PathVariable Long id, Model model) {
        var orderOptional = orderService.getOrderById(id);
        if (orderOptional.isEmpty()) {
            return "redirect:/admin/orders";
        }
        Order order = orderOptional.get();
        model.addAttribute("order", OrderDTO.fromEntity(order));
        model.addAttribute("orderStatusChoices", getAllowedStatusChoices(order.getStatus()));
        return "admin/orders/form";
    }

    @PostMapping("/orders/save")
    public String saveOrder(@ModelAttribute("order") OrderDTO orderDto, RedirectAttributes redirectAttributes) {
        try {
            var existingOrder = orderService.getOrderById(orderDto.getId());
            if (existingOrder.isEmpty()) {
                redirectAttributes.addFlashAttribute("orderError", "Order not found");
                return "redirect:/admin/orders";
            }

            // Build an update request object to avoid mutating the managed entity in the
            // current request context. This keeps old/new status comparison reliable in
            // service.
            Order updateRequest = new Order();
            updateRequest.setId(orderDto.getId());
            updateRequest.setStatus(orderDto.getStatus());

            String shippingAddress = existingOrder.get().getShippingAddress();
            if (orderDto.getShippingAddress() != null && !orderDto.getShippingAddress().isBlank()) {
                shippingAddress = orderDto.getShippingAddress();
            }
            updateRequest.setShippingAddress(shippingAddress);

            orderService.saveOrder(updateRequest);
            redirectAttributes.addFlashAttribute("orderSuccess", "Order status updated successfully");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("orderError", e.getMessage());
            return "redirect:/admin/orders";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("orderError", e.getMessage());
            return "redirect:/admin/orders";
        }
        return "redirect:/admin/orders";
    }

    @GetMapping("/orders/search")
    public String searchOrders(@RequestParam(required = false) String status,
            @RequestParam(required = false) String orderId,
            Model model) {
        var orders = orderService.getAllOrders();

        if (status != null && !status.isEmpty() && !status.equals("All Status")) {
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

        model.addAttribute("orders", orders.stream().map(OrderDTO::fromEntity).toList());
        model.addAttribute("orderStatusOptions", buildOrderStatusOptions(orders));
        return "admin/orders";
    }

    private Map<Long, List<String>> buildOrderStatusOptions(List<Order> orders) {
        Map<Long, List<String>> result = new LinkedHashMap<>();
        for (Order order : orders) {
            if (order != null && order.getId() != null) {
                result.put(order.getId(), getAllowedStatusChoices(order.getStatus()));
            }
        }
        return result;
    }

    private List<String> getAllowedStatusChoices(String currentStatus) {
        String normalized = normalizeOrderStatus(currentStatus);
        List<String> choices = new ArrayList<>();

        switch (normalized) {
            case "pending":
                choices.add("Pending");
                choices.add("Confirmed");
                choices.add("Cancelled");
                break;
            case "confirmed":
                choices.add("Confirmed");
                choices.add("Shipping");
                choices.add("Cancelled");
                break;
            case "shipping":
                choices.add("Shipping");
                choices.add("Completed");
                choices.add("Cancelled");
                break;
            case "completed":
                choices.add("Completed");
                break;
            case "cancelled":
                choices.add("Cancelled");
                break;
            default:
                choices.add("Pending");
                choices.add("Confirmed");
                choices.add("Shipping");
                choices.add("Completed");
                choices.add("Cancelled");
                break;
        }

        return choices;
    }

    private String normalizeOrderStatus(String status) {
        if (status == null) {
            return "";
        }

        String normalized = Normalizer.normalize(status, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT)
                .trim();

        if (normalized.contains("cho xu ly") || normalized.contains("pending")) {
            return "pending";
        }
        if (normalized.contains("xac nhan") || normalized.contains("confirm")) {
            return "confirmed";
        }
        if (normalized.contains("dang giao") || normalized.contains("ship")) {
            return "shipping";
        }
        if (normalized.contains("hoan thanh") || normalized.contains("complete") || normalized.contains("deliver")
                || normalized.contains("da giao")) {
            return "completed";
        }
        if (normalized.contains("da huy") || normalized.contains("cancel")) {
            return "cancelled";
        }
        return normalized;
    }

    @GetMapping("/vouchers")
    public String listVouchers(Model model) {
        model.addAttribute("vouchers", voucherService.getAllVouchers().stream().map(VoucherDTO::fromEntity).toList());
        return "admin/vouchers";
    }

    @GetMapping("/vouchers/{id}")
    public String viewVoucher(@PathVariable Long id, Model model) {
        voucherService.getVoucherById(id)
                .ifPresent(voucher -> {
                    model.addAttribute("voucher", VoucherDTO.fromEntity(voucher));
                    model.addAttribute("voucherOrders", voucher.getOrders() == null
                            ? List.of()
                            : voucher.getOrders().stream().map(OrderDTO::fromEntity).toList());
                });
        return "admin/vouchers/view";
    }

    @GetMapping("/vouchers/new")
    public String showCreateVoucherForm(Model model) {
        model.addAttribute("voucher", new VoucherDTO());
        return "admin/vouchers/form";
    }

    @GetMapping("/vouchers/edit/{id}")
    public String showEditVoucherForm(@PathVariable Long id, Model model) {
        voucherService.getVoucherById(id)
                .ifPresent(voucher -> model.addAttribute("voucher", VoucherDTO.fromEntity(voucher)));
        return "admin/vouchers/form";
    }

    @PostMapping("/vouchers/save")
    public String saveVoucher(@ModelAttribute("voucher") VoucherDTO voucherDto) {
        voucherService.saveVoucher(voucherDto.toEntity());
        return "redirect:/admin/vouchers";
    }

    @GetMapping("/vouchers/delete/{id}")
    public String deleteVoucher(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        var voucherOptional = voucherService.getVoucherById(id);
        if (voucherOptional.isEmpty()) {
            redirectAttributes.addFlashAttribute("voucherError", "Voucher not found");
            return "redirect:/admin/vouchers";
        }

        boolean voucherUsed = orderService.getAllOrders().stream()
                .anyMatch(order -> order.getVoucher() != null && id.equals(order.getVoucher().getId()));
        if (voucherUsed) {
            Voucher voucher = voucherOptional.get();
            voucher.setStatus("Inactive");
            voucher.setQuantity(0);
            voucherService.saveVoucher(voucher);
            redirectAttributes.addFlashAttribute("voucherSuccess",
                    "Voucher has been used in orders, so it was set to Inactive instead of being deleted");
            return "redirect:/admin/vouchers";
        }

        voucherService.deleteVoucher(id);
        redirectAttributes.addFlashAttribute("voucherSuccess", "Voucher deleted successfully");
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

        if (status != null && !status.isEmpty() && !status.equals("All Status")) {
            vouchers = vouchers.stream()
                    .filter(voucher -> voucher.getStatus().equals(status))
                    .toList();
        }

        model.addAttribute("vouchers", vouchers.stream().map(VoucherDTO::fromEntity).toList());
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
