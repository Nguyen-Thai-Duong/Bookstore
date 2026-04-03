package com.bookstore.controller;

import com.bookstore.dto.CartItemDTO;
import com.bookstore.dto.UserDTO;
import com.bookstore.dto.VoucherDTO;
import com.bookstore.model.Book;
import com.bookstore.model.Cart;
import com.bookstore.model.CartItem;
import com.bookstore.model.Order;
import com.bookstore.model.OrderDetail;
import com.bookstore.model.User;
import com.bookstore.model.Voucher;
import com.bookstore.repository.BookRepository;
import com.bookstore.repository.CartItemRepository;
import com.bookstore.repository.CartRepository;
import com.bookstore.repository.OrderDetailRepository;
import com.bookstore.repository.OrderRepository;
import com.bookstore.repository.VoucherRepository;
import com.bookstore.service.BookService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class CartController {

    private final BookService bookService;
    private final BookRepository bookRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final VoucherRepository voucherRepository;
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;

    @GetMapping("/cart")
    public String viewCart(HttpSession session, Model model) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) {
            return "redirect:/login";
        }

        Cart cart = getOrCreateCart(user);
        List<CartItem> items = cartItemRepository.findByCart_Id(cart.getId());
        updateCartCount(session, items);
        BigDecimal subtotal = calculateSubtotal(items);
        Voucher voucher = getVoucherFromSession(session);
        BigDecimal discount = calculateDiscount(subtotal, voucher);
        BigDecimal total = subtotal.subtract(discount).max(BigDecimal.ZERO);

        model.addAttribute("cart", items.stream().map(CartItemDTO::fromEntity).toList());
        model.addAttribute("subtotal", subtotal);
        model.addAttribute("discount", discount);
        model.addAttribute("total", total);
        model.addAttribute("voucher", VoucherDTO.fromEntity(voucher));
        return "cart";
    }

    @PostMapping("/cart/add")
    public String addToCart(@RequestParam Long bookId,
            @RequestParam(defaultValue = "1") int quantity,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) {
            return "redirect:/login";
        }
        if (bookId == null || bookId <= 0 || quantity <= 0) {
            redirectAttributes.addFlashAttribute("cartError", "Số lượng không hợp lệ");
            return "redirect:/books";
        }
        if (quantity > 999) {
            redirectAttributes.addFlashAttribute("cartError", "Số lượng quá lớn");
            return "redirect:/books";
        }

        Optional<Book> bookOpt = bookService.getBookById(bookId);
        if (bookOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("cartError", "Không tìm thấy sách");
            return "redirect:/books";
        }

        Book book = bookOpt.get();
        int stock = normalizeStock(book.getStock());
        if (stock <= 0) {
            redirectAttributes.addFlashAttribute("cartError", "Sách đã hết hàng");
            return "redirect:/books/" + bookId;
        }
        if (quantity > stock) {
            redirectAttributes.addFlashAttribute("cartError", "Không đủ tồn kho");
            return "redirect:/books/" + bookId;
        }

        Cart cart = getOrCreateCart(user);
        Optional<CartItem> existing = cartItemRepository.findByCart_IdAndBook_Id(cart.getId(), bookId);
        if (existing.isPresent()) {
            CartItem item = existing.get();
            int newQty = item.getQuantity() + quantity;
            if (newQty > stock) {
                redirectAttributes.addFlashAttribute("cartError", "Không đủ tồn kho");
                return "redirect:/books/" + bookId;
            }
            item.setQuantity(newQty);
            cartItemRepository.save(item);
        } else {
            CartItem item = new CartItem();
            item.setCart(cart);
            item.setBook(book);
            item.setQuantity(quantity);
            cartItemRepository.save(item);
        }

        updateCartCount(session, cartItemRepository.findByCart_Id(cart.getId()));

        redirectAttributes.addFlashAttribute("cartSuccess", "Đã thêm vào giỏ hàng");
        return "redirect:/cart";
    }

    @PostMapping("/cart/update")
    public String updateCart(@RequestParam Long bookId,
            @RequestParam int quantity,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) {
            return "redirect:/login";
        }
        if (bookId == null || bookId <= 0 || quantity < 0) {
            redirectAttributes.addFlashAttribute("cartError", "Số lượng không hợp lệ");
            return "redirect:/cart";
        }
        if (quantity > 999) {
            redirectAttributes.addFlashAttribute("cartError", "Số lượng quá lớn");
            return "redirect:/cart";
        }

        Cart cart = getOrCreateCart(user);
        Optional<CartItem> existing = cartItemRepository.findByCart_IdAndBook_Id(cart.getId(), bookId);
        if (existing.isEmpty()) {
            return "redirect:/cart";
        }

        if (quantity == 0) {
            cartItemRepository.delete(existing.get());
            updateCartCount(session, cartItemRepository.findByCart_Id(cart.getId()));
            return "redirect:/cart";
        }

        Optional<Book> bookOpt = bookService.getBookById(bookId);
        if (bookOpt.isPresent()) {
            int stock = normalizeStock(bookOpt.get().getStock());
            if (stock <= 0) {
                redirectAttributes.addFlashAttribute("cartError", "Sách đã hết hàng");
                return "redirect:/cart";
            }
            if (quantity > stock) {
                redirectAttributes.addFlashAttribute("cartError", "Không đủ tồn kho");
                return "redirect:/cart";
            }
        }

        CartItem item = existing.get();
        item.setQuantity(quantity);
        cartItemRepository.save(item);
        updateCartCount(session, cartItemRepository.findByCart_Id(cart.getId()));
        return "redirect:/cart";
    }

    @PostMapping("/cart/remove")
    public String removeFromCart(@RequestParam Long bookId, HttpSession session) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) {
            return "redirect:/login";
        }

        Cart cart = getOrCreateCart(user);
        cartItemRepository.findByCart_IdAndBook_Id(cart.getId(), bookId)
                .ifPresent(cartItem -> {
                    cartItemRepository.delete(cartItem);
                    updateCartCount(session, cartItemRepository.findByCart_Id(cart.getId()));
                });
        return "redirect:/cart";
    }

    @PostMapping("/cart/voucher")
    public String applyVoucher(@RequestParam String code,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) {
            return "redirect:/login";
        }

        if (code == null || code.isBlank()) {
            session.removeAttribute("voucherCode");
            return "redirect:/cart";
        }

        String normalized = code.trim();
        if (normalized.length() > 50) {
            redirectAttributes.addFlashAttribute("voucherError", "Mã voucher quá dài");
            return "redirect:/cart";
        }

        Optional<Voucher> voucherOpt = voucherRepository.findByCode(normalized);
        if (voucherOpt.isEmpty()) {
            session.removeAttribute("voucherCode");
            redirectAttributes.addFlashAttribute("voucherError", "Voucher không hợp lệ");
            return "redirect:/cart";
        }

        Voucher voucher = voucherOpt.get();
        if (!isVoucherActive(voucher)) {
            session.removeAttribute("voucherCode");
            redirectAttributes.addFlashAttribute("voucherError", "Voucher không còn hiệu lực");
            return "redirect:/cart";
        }

        session.setAttribute("voucherCode", voucher.getCode());
        redirectAttributes.addFlashAttribute("voucherSuccess", "Áp dụng voucher thành công");
        return "redirect:/cart";
    }

    @GetMapping("/checkout")
    public String checkout(HttpSession session, Model model) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) {
            return "redirect:/login";
        }

        Cart cart = getOrCreateCart(user);
        List<CartItem> items = cartItemRepository.findByCart_Id(cart.getId());
        updateCartCount(session, items);
        BigDecimal subtotal = calculateSubtotal(items);
        Voucher voucher = getVoucherFromSession(session);
        BigDecimal discount = calculateDiscount(subtotal, voucher);
        BigDecimal total = subtotal.subtract(discount).max(BigDecimal.ZERO);

        model.addAttribute("cart", items.stream().map(CartItemDTO::fromEntity).toList());
        model.addAttribute("subtotal", subtotal);
        model.addAttribute("discount", discount);
        model.addAttribute("total", total);
        model.addAttribute("voucher", VoucherDTO.fromEntity(voucher));
        model.addAttribute("user", UserDTO.fromEntity(user));
        return "checkout";
    }

    @PostMapping("/checkout")
    @Transactional
    public String placeOrder(@RequestParam String fullName,
            @RequestParam String phone,
            @RequestParam String address,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) {
            return "redirect:/login";
        }

        Cart cart = getOrCreateCart(user);
        List<CartItem> items = cartItemRepository.findByCart_Id(cart.getId());
        if (items.isEmpty()) {
            redirectAttributes.addFlashAttribute("cartError", "Giỏ hàng trống");
            return "redirect:/cart";
        }

        List<Long> bookIds = items.stream()
                .map(item -> item.getBook() != null ? item.getBook().getId() : null)
                .filter(id -> id != null)
                .distinct()
                .toList();

        if (bookIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("cartError", "Sách trong giỏ hàng không hợp lệ");
            return "redirect:/cart";
        }

        var lockedBooks = bookRepository.findAllByIdForUpdate(bookIds);
        if (lockedBooks.size() != bookIds.size()) {
            redirectAttributes.addFlashAttribute("cartError", "Sách trong giỏ hàng không hợp lệ");
            return "redirect:/cart";
        }

        var bookMap = lockedBooks.stream().collect(java.util.stream.Collectors.toMap(Book::getId, book -> book));

        for (CartItem item : items) {
            if (item.getBook() == null || item.getBook().getId() == null) {
                redirectAttributes.addFlashAttribute("cartError", "Sách trong giỏ hàng không hợp lệ");
                return "redirect:/cart";
            }

            Book currentBook = bookMap.get(item.getBook().getId());
            if (currentBook == null) {
                redirectAttributes.addFlashAttribute("cartError", "Không tìm thấy sách: " + item.getBook().getTitle());
                return "redirect:/cart";
            }

            int stock = normalizeStock(currentBook.getStock());
            int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
            if (quantity <= 0) {
                redirectAttributes.addFlashAttribute("cartError", "Số lượng trong giỏ hàng không hợp lệ");
                return "redirect:/cart";
            }
            if (stock <= 0) {
                redirectAttributes.addFlashAttribute("cartError", "Sách đã hết hàng: " + currentBook.getTitle());
                return "redirect:/cart";
            }
            if (quantity > stock) {
                redirectAttributes.addFlashAttribute("cartError",
                        "Không đủ tồn kho cho sách: " + currentBook.getTitle() + " (còn " + stock + ", bạn đặt "
                                + quantity + ")");
                return "redirect:/cart";
            }
        }

        String normalizedName = fullName == null ? "" : fullName.trim();
        String normalizedPhone = phone == null ? "" : phone.trim();
        String normalizedAddress = address == null ? "" : address.trim();

        if (normalizedName.isEmpty()) {
            redirectAttributes.addFlashAttribute("checkoutError", "Họ và tên không được để trống");
            return "redirect:/checkout";
        }
        if (normalizedPhone.isEmpty() || !normalizedPhone.matches("\\d{8,15}")) {
            redirectAttributes.addFlashAttribute("checkoutError", "Số điện thoại phải từ 8-15 chữ số");
            return "redirect:/checkout";
        }
        if (normalizedAddress.isEmpty()) {
            redirectAttributes.addFlashAttribute("checkoutError", "Địa chỉ không được để trống");
            return "redirect:/checkout";
        }

        Voucher voucher = getVoucherFromSession(session);
        if (voucher != null && !isVoucherActive(voucher)) {
            voucher = null;
            session.removeAttribute("voucherCode");
        }

        BigDecimal subtotal = calculateSubtotal(items);
        BigDecimal discount = calculateDiscount(subtotal, voucher);
        BigDecimal total = subtotal.subtract(discount).max(BigDecimal.ZERO);

        updateCartCount(session, items);

        Order order = new Order();
        order.setUser(user);
        order.setVoucher(voucher);
        order.setTotalAmount(total);
        order.setStatus("Pending");
        order.setOrderDate(LocalDateTime.now());
        order.setShippingAddress(normalizedAddress);
        Order savedOrder = orderRepository.save(order);

        if (voucher != null && voucher.getQuantity() != null && voucher.getQuantity() > 0) {
            voucher.setQuantity(voucher.getQuantity() - 1);
            voucherRepository.save(voucher);
        }

        for (CartItem item : items) {
            Book currentBook = bookMap.get(item.getBook().getId());
            int quantity = item.getQuantity() == null ? 0 : item.getQuantity();

            currentBook.setStock(normalizeStock(currentBook.getStock()) - quantity);
            bookService.saveBook(currentBook);

            OrderDetail orderDetail = new OrderDetail();
            orderDetail.setOrder(savedOrder);
            orderDetail.setBook(currentBook);
            orderDetail.setQuantity(quantity);
            orderDetail.setUnitPrice(currentBook.getPrice());
            orderDetailRepository.save(orderDetail);
        }

        cartItemRepository.deleteByCart_Id(cart.getId());
        session.setAttribute("cartCount", 0);
        session.removeAttribute("voucherCode");

        redirectAttributes.addFlashAttribute("orderMessage", "Đặt hàng thành công");
        return "redirect:/orders";
    }

    private Cart getOrCreateCart(User user) {
        return cartRepository.findByUser_Id(user.getId())
                .orElseGet(() -> {
                    Cart cart = new Cart();
                    cart.setUser(user);
                    cart.setCreatedAt(LocalDateTime.now());
                    return cartRepository.save(cart);
                });
    }

    private Voucher getVoucherFromSession(HttpSession session) {
        Object value = session.getAttribute("voucherCode");
        if (value instanceof String code) {
            return voucherRepository.findByCode(code).orElse(null);
        }
        return null;
    }

    private boolean isVoucherActive(Voucher voucher) {
        if (voucher == null) {
            return false;
        }
        if (voucher.getStatus() != null && !"Active".equalsIgnoreCase(voucher.getStatus())) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        if (voucher.getStartDate() != null && now.isBefore(voucher.getStartDate())) {
            return false;
        }
        if (voucher.getEndDate() != null && now.isAfter(voucher.getEndDate())) {
            return false;
        }
        if (voucher.getQuantity() != null && voucher.getQuantity() <= 0) {
            return false;
        }

        return true;
    }

    private BigDecimal calculateSubtotal(List<CartItem> cart) {
        return cart.stream()
                .map(item -> item.getBook().getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateDiscount(BigDecimal subtotal, Voucher voucher) {
        if (voucher == null || voucher.getDiscountPercent() == null) {
            return BigDecimal.ZERO;
        }

        return subtotal.multiply(BigDecimal.valueOf(voucher.getDiscountPercent()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private int normalizeStock(Integer stock) {
        return stock == null ? 0 : Math.max(stock, 0);
    }

    private void updateCartCount(HttpSession session, List<CartItem> items) {
        int count = items == null ? 0
                : items.stream()
                        .mapToInt(item -> item.getQuantity() != null ? item.getQuantity() : 0)
                        .sum();
        session.setAttribute("cartCount", count);
    }
}
