package com.bookstore.config;

import com.bookstore.model.User;
import com.bookstore.repository.CartItemRepository;
import com.bookstore.repository.CartRepository;
import com.bookstore.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthService authService;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String path = request.getRequestURI();
        HttpSession session = request.getSession(false);
        User user = session == null ? null : (User) session.getAttribute("loggedInUser");
        boolean isAdminPath = path.startsWith("/admin");

        // Keep admin and user areas separated: if admin leaves /admin area,
        // force logout to guest before allowing access.
        if (user != null && authService.isAdmin(user) && !isAdminPath) {
            session.removeAttribute("loggedInUser");
            session.removeAttribute("cart");
            session.removeAttribute("cartCount");
            user = null;
        }

        if (isAdminPath) {
            if (user == null) {
                response.sendRedirect("/login?error=Vui%20l%C3%B2ng%20%C4%91%C4%83ng%20nh%E1%BA%ADp");
                return false;
            }
            if (!authService.isAdmin(user)) {
                response.sendRedirect("/");
                return false;
            }
        }

        if (path.startsWith("/account")
                || path.startsWith("/cart")
                || path.startsWith("/checkout")
                || path.startsWith("/orders")
                || path.startsWith("/reviews")) {
            if (user == null) {
                response.sendRedirect("/login?error=Vui%20l%C3%B2ng%20%C4%91%C4%83ng%20nh%E1%BA%ADp");
                return false;
            }
        }

        if (session != null && user != null && !authService.isAdmin(user)) {
            refreshCartCount(session, user.getId());
        }

        return true;
    }

    private void refreshCartCount(HttpSession session, Long userId) {
        int cartCount = cartRepository.findByUser_Id(userId)
                .map(cart -> cartItemRepository.findByCart_Id(cart.getId()).stream()
                        .mapToInt(item -> item.getQuantity() != null ? item.getQuantity() : 0)
                        .sum())
                .orElse(0);
        session.setAttribute("cartCount", cartCount);
    }
}
