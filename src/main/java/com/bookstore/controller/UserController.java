package com.bookstore.controller;

import com.bookstore.dto.UserDTO;
import com.bookstore.dto.UserFormDTO;
import com.bookstore.model.Role;
import com.bookstore.model.User;
import com.bookstore.service.RoleService;
import com.bookstore.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final RoleService roleService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @GetMapping
    public String listUsers(Model model) {
        model.addAttribute("users", userService.getAllUsers().stream().map(UserDTO::fromEntity).toList());
        return "users/list";
    }

    @GetMapping("/{id}")
    public String viewUser(@PathVariable Long id, Model model) {
        userService.getUserById(id).ifPresent(user -> model.addAttribute("user", UserDTO.fromEntity(user)));
        return "users/view";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("user", new UserFormDTO());
        model.addAttribute("roles", roleService.getAllRoles());
        return "users/form";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        userService.getUserById(id).ifPresent(user -> model.addAttribute("user", UserFormDTO.fromEntity(user)));
        model.addAttribute("roles", roleService.getAllRoles());
        return "users/form";
    }

    @PostMapping
    public String saveUser(@ModelAttribute("user") UserFormDTO userForm) {
        String rawPassword = userForm.getPassword();
        String encodedPassword;

        if (userForm.getId() != null) {
            encodedPassword = userService.getUserById(userForm.getId())
                    .map(existingUser -> {
                        if (userForm.getCreatedAt() == null) {
                            userForm.setCreatedAt(existingUser.getCreatedAt());
                        }
                        if (rawPassword == null || rawPassword.isBlank()) {
                            return existingUser.getPassword();
                        }
                        return passwordEncoder.encode(rawPassword);
                    })
                    .orElseGet(() -> rawPassword == null || rawPassword.isBlank() ? null
                            : passwordEncoder.encode(rawPassword));
        } else {
            encodedPassword = rawPassword == null || rawPassword.isBlank() ? null : passwordEncoder.encode(rawPassword);
        }

        User user = userForm.toEntity(encodedPassword);
        if (userForm.getRole() != null && userForm.getRole().getId() != null) {
            roleService.getRoleById(userForm.getRole().getId()).ifPresent(user::setRole);
        }
        userService.saveUser(user);
        return "redirect:/users";
    }

    @GetMapping("/delete/{id}")
    public String deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return "redirect:/users";
    }
}
