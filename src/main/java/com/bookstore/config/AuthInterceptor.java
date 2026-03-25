package com.bookstore.config;

import com.bookstore.model.User;
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

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        HttpSession session = request.getSession(false);
        User user = session == null ? null : (User) session.getAttribute("loggedInUser");

        if (path.startsWith("/admin")) {
            if (user == null) {
                response.sendRedirect("/login?error=Vui%20long%20dang%20nhap");
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
                response.sendRedirect("/login?error=Vui%20long%20dang%20nhap");
                return false;
            }
        }

        return true;
    }
}
