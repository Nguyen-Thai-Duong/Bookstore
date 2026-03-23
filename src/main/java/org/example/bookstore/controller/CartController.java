package org.example.bookstore.controller;

import jakarta.servlet.http.HttpSession;
import org.example.bookstore.model.Book;
import org.example.bookstore.model.Cart;
import org.example.bookstore.model.CartItem;
import org.example.bookstore.model.Order;
import org.example.bookstore.model.OrderItem;
import org.example.bookstore.model.User;
import org.example.bookstore.model.Voucher;
import org.example.bookstore.repository.CartItemRepository;
import org.example.bookstore.repository.CartRepository;
import org.example.bookstore.repository.OrderItemRepository;
import org.example.bookstore.repository.OrderRepository;
import org.example.bookstore.repository.VoucherRepository;
import org.example.bookstore.service.BookService;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Controller
public class CartController {

    private final BookService bookService;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final VoucherRepository voucherRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public CartController(BookService bookService,
                          CartRepository cartRepository,
                          CartItemRepository cartItemRepository,
                          VoucherRepository voucherRepository,
                          OrderRepository orderRepository,
                          OrderItemRepository orderItemRepository) {
        this.bookService = bookService;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.voucherRepository = voucherRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @GetMapping("/cart")
    public String viewCart(HttpSession session, Model model) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) {
            return "redirect:/login";
        }
        Cart cart = getOrCreateCart(user);
        List<CartItem> items = cartItemRepository.findByCartCartID(cart.getCartID());
        BigDecimal subtotal = calculateSubtotal(items);
        Voucher voucher = getVoucherFromSession(session);
        BigDecimal discount = calculateDiscount(subtotal, voucher);
        BigDecimal total = subtotal.subtract(discount).max(BigDecimal.ZERO);

        model.addAttribute("cart", items);
        model.addAttribute("subtotal", subtotal);
        model.addAttribute("discount", discount);
        model.addAttribute("total", total);
        model.addAttribute("voucher", voucher);
        return "cart";
    }

    @PostMapping("/cart/add")
    public String addToCart(@RequestParam int bookId,
                            @RequestParam(defaultValue = "1") int quantity,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) {
            return "redirect:/login";
        }
        if (bookId <= 0 || quantity <= 0) {
            redirectAttributes.addFlashAttribute("cartError", "Số lượng không hợp lệ");
            return "redirect:/books";
        }
        if (quantity > 999) {
            redirectAttributes.addFlashAttribute("cartError", "So luong qua lon");
            return "redirect:/books";
        }
        Book book = bookService.getBookById(bookId);
        if (book == null) {
            redirectAttributes.addFlashAttribute("cartError", "Không tìm thấy sách");
            return "redirect:/books";
        }
        if (book.getStockQuantity() > 0 && quantity > book.getStockQuantity()) {
            redirectAttributes.addFlashAttribute("cartError", "Không đủ tồn kho");
            return "redirect:/books/" + bookId;
        }

        Cart cart = getOrCreateCart(user);
        Optional<CartItem> existing = cartItemRepository.findByCartCartIDAndBookBookID(cart.getCartID(), bookId);
        if (existing.isPresent()) {
            CartItem item = existing.get();
            int newQty = item.getQuantity() + quantity;
            if (book.getStockQuantity() > 0 && newQty > book.getStockQuantity()) {
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

        redirectAttributes.addFlashAttribute("cartSuccess", "Đã thêm vào giỏ hàng");
        return "redirect:/cart";
    }

    @PostMapping("/cart/update")
    public String updateCart(@RequestParam int bookId,
                             @RequestParam int quantity,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) {
            return "redirect:/login";
        }
        if (quantity > 999) {
            redirectAttributes.addFlashAttribute("cartError", "So luong qua lon");
            return "redirect:/cart";
        }
        if (bookId <= 0 || quantity < 0) {
            redirectAttributes.addFlashAttribute("cartError", "Số lượng không hợp lệ");
            return "redirect:/cart";
        }
        Cart cart = getOrCreateCart(user);
        Optional<CartItem> existing = cartItemRepository.findByCartCartIDAndBookBookID(cart.getCartID(), bookId);
        if (existing.isEmpty()) {
            return "redirect:/cart";
        }
        if (quantity == 0) {
            cartItemRepository.delete(existing.get());
            return "redirect:/cart";
        }
        Book book = bookService.getBookById(bookId);
        if (book != null && book.getStockQuantity() > 0 && quantity > book.getStockQuantity()) {
            redirectAttributes.addFlashAttribute("cartError", "Không đủ tồn kho");
            return "redirect:/cart";
        }
        CartItem item = existing.get();
        item.setQuantity(quantity);
        cartItemRepository.save(item);
        return "redirect:/cart";
    }

    @PostMapping("/cart/remove")
    public String removeFromCart(@RequestParam int bookId, HttpSession session) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) {
            return "redirect:/login";
        }
        Cart cart = getOrCreateCart(user);
        cartItemRepository.findByCartCartIDAndBookBookID(cart.getCartID(), bookId)
                .ifPresent(cartItemRepository::delete);
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
            redirectAttributes.addFlashAttribute("voucherError", "Ma voucher qua dai");
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
        List<CartItem> items = cartItemRepository.findByCartCartID(cart.getCartID());
        BigDecimal subtotal = calculateSubtotal(items);
        Voucher voucher = getVoucherFromSession(session);
        BigDecimal discount = calculateDiscount(subtotal, voucher);
        BigDecimal total = subtotal.subtract(discount).max(BigDecimal.ZERO);

        model.addAttribute("cart", items);
        model.addAttribute("subtotal", subtotal);
        model.addAttribute("discount", discount);
        model.addAttribute("total", total);
        model.addAttribute("voucher", voucher);
        model.addAttribute("user", user);
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
        List<CartItem> items = cartItemRepository.findByCartCartID(cart.getCartID());
        if (items.isEmpty()) {
            redirectAttributes.addFlashAttribute("cartError", "Giỏ hàng trống");
            return "redirect:/cart";
        }
        String normalizedName = fullName == null ? "" : fullName.trim();
        String normalizedPhone = phone == null ? "" : phone.trim();
        String normalizedAddress = address == null ? "" : address.trim();
        if (normalizedName.length() > 100) {
            redirectAttributes.addFlashAttribute("checkoutError", "Ho va ten qua dai");
            return "redirect:/checkout";
        }
        if (normalizedAddress.length() > 255) {
            redirectAttributes.addFlashAttribute("checkoutError", "Dia chi qua dai");
            return "redirect:/checkout";
        }
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

        Order order = new Order();
        order.setUser(user);
        order.setVoucher(voucher);
        order.setTotalAmount(total);
        order.setOrderStatus("Pending");
        order.setOrderDate(LocalDateTime.now());
        order.setShippingAddress(normalizedAddress);
        Order savedOrder = orderRepository.save(order);

        if (voucher != null && voucher.getQuantity() != null && voucher.getQuantity() > 0) {
            voucher.setQuantity(voucher.getQuantity() - 1);
            voucherRepository.save(voucher);
        }

        for (CartItem item : items) {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(savedOrder);
            orderItem.setBook(item.getBook());
            orderItem.setQuantity(item.getQuantity());
            orderItem.setUnitPrice(BigDecimal.valueOf(item.getBook().getPrice()));
            orderItemRepository.save(orderItem);
        }

        cartItemRepository.deleteByCartCartID(cart.getCartID());
        session.removeAttribute("voucherCode");

        return "redirect:/orders/" + savedOrder.getOrderID();
    }

    private Cart getOrCreateCart(User user) {
        return cartRepository.findByUserUserID(user.getUserID())
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
                .map(item -> BigDecimal.valueOf(item.getBook().getPrice())
                        .multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateDiscount(BigDecimal subtotal, Voucher voucher) {
        if (voucher == null || voucher.getDiscountPercent() == null) {
            return BigDecimal.ZERO;
        }
        return subtotal.multiply(BigDecimal.valueOf(voucher.getDiscountPercent()))
                .divide(BigDecimal.valueOf(100));
    }
}
