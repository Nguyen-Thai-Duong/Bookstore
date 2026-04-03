package com.bookstore.controller;

import com.bookstore.dto.UserDTO;
import com.bookstore.model.User;
import com.bookstore.service.AuthService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@Controller
@RequiredArgsConstructor
public class AccountController {

    private final AuthService authService;
    private static final long MAX_AVATAR_SIZE_BYTES = 2 * 1024 * 1024;
    private static final Set<String> ALLOWED_AVATAR_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    @GetMapping("/account")
    public String account(HttpSession session, Model model) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) {
            return "redirect:/login";
        }

        model.addAttribute("user", UserDTO.fromEntity(user));
        model.addAttribute("avatarUrl", resolveAvatarUrl(user.getId()));
        return "account";
    }

    @PostMapping("/account/avatar")
    public String updateAvatar(@RequestParam("avatar") MultipartFile avatar,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) {
            return "redirect:/login";
        }

        if (avatar == null || avatar.isEmpty()) {
            redirectAttributes.addFlashAttribute("avatarError", "Vui lòng chọn ảnh đại diện trước khi tải lên");
            return "redirect:/account";
        }

        if (avatar.getSize() > MAX_AVATAR_SIZE_BYTES) {
            redirectAttributes.addFlashAttribute("avatarError", "Kích thước ảnh tối đa là 2MB");
            return "redirect:/account";
        }

        String extension = extractExtension(avatar.getOriginalFilename());
        if (extension.isEmpty() || !ALLOWED_AVATAR_EXTENSIONS.contains(extension)) {
            redirectAttributes.addFlashAttribute("avatarError",
                    "Định dạng ảnh không hợp lệ. Chỉ hỗ trợ JPG, PNG, WEBP, GIF");
            return "redirect:/account";
        }

        Path avatarDir = Paths.get("images", "avatars").toAbsolutePath().normalize();
        try {
            Files.createDirectories(avatarDir);
            removeExistingAvatarFiles(user.getId(), avatarDir);

            String fileName = "user-" + user.getId() + "." + extension;
            Path targetPath = avatarDir.resolve(fileName).normalize();
            Files.copy(avatar.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            redirectAttributes.addFlashAttribute("avatarSuccess", "Cập nhật avatar thành công");
        } catch (IOException ex) {
            redirectAttributes.addFlashAttribute("avatarError", "Không thể tải avatar lúc này. Vui lòng thử lại");
        }

        return "redirect:/account";
    }

    @PostMapping("/account/profile")
    public String updateProfile(@RequestParam String fullName,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String address,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) {
            return "redirect:/login";
        }

        String normalizedName = fullName == null ? "" : fullName.trim();
        String normalizedPhone = phone == null ? "" : phone.trim();
        String normalizedAddress = address == null ? "" : address.trim();

        if (normalizedName.length() > 100) {
            redirectAttributes.addFlashAttribute("profileError", "Họ và tên quá dài");
            return "redirect:/account";
        }
        if (!normalizedAddress.isEmpty() && normalizedAddress.length() > 255) {
            redirectAttributes.addFlashAttribute("profileError", "Địa chỉ quá dài");
            return "redirect:/account";
        }
        if (normalizedName.isEmpty()) {
            redirectAttributes.addFlashAttribute("profileError", "Họ và tên không được để trống");
            return "redirect:/account";
        }
        if (!normalizedPhone.isEmpty() && !normalizedPhone.matches("\\d{8,15}")) {
            redirectAttributes.addFlashAttribute("profileError", "Số điện thoại phải từ 10-11 chữ số");
            return "redirect:/account";
        }

        User updated = authService.updateProfile(
                user.getId(),
                normalizedName,
                normalizedPhone.isEmpty() ? null : normalizedPhone,
                normalizedAddress.isEmpty() ? null : normalizedAddress);

        if (updated == null) {
            redirectAttributes.addFlashAttribute("profileError", "Không thể cập nhật thông tin");
            return "redirect:/account";
        }

        session.setAttribute("loggedInUser", updated);
        redirectAttributes.addFlashAttribute("profileSuccess", "Cập nhật thông tin thành công");
        return "redirect:/account";
    }

    @PostMapping("/account/password")
    public String changePassword(@RequestParam String currentPassword,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) {
            return "redirect:/login";
        }

        if (currentPassword == null || currentPassword.isBlank()) {
            redirectAttributes.addFlashAttribute("passwordError", "Vui lòng nhập mật khẩu hiện tại");
            return "redirect:/account";
        }
        if (newPassword == null || newPassword.length() < 6) {
            redirectAttributes.addFlashAttribute("passwordError", "Mật khẩu mới phải có ít nhất 6 ký tự");
            return "redirect:/account";
        }
        if (newPassword.length() > 72) {
            redirectAttributes.addFlashAttribute("passwordError", "Mật khẩu quá dài");
            return "redirect:/account";
        }
        if (newPassword.equals(currentPassword)) {
            redirectAttributes.addFlashAttribute("passwordError", "Mật khẩu mới phải khác mật khẩu hiện tại");
            return "redirect:/account";
        }
        if (confirmPassword == null || !newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("passwordError", "Xác nhận mật khẩu không khớp");
            return "redirect:/account";
        }

        boolean changed = authService.changePassword(user.getId(), currentPassword, newPassword);
        if (!changed) {
            redirectAttributes.addFlashAttribute("passwordError", "Mật khẩu hiện tại không đúng");
            return "redirect:/account";
        }

        redirectAttributes.addFlashAttribute("passwordSuccess", "Đổi mật khẩu thành công");
        return "redirect:/account";
    }

    private String extractExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }

        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }

        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private void removeExistingAvatarFiles(Long userId, Path avatarDir) throws IOException {
        String prefix = "user-" + userId + ".";
        if (!Files.exists(avatarDir)) {
            return;
        }

        try (Stream<Path> stream = Files.list(avatarDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(prefix))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }

    private String resolveAvatarUrl(Long userId) {
        Path avatarDir = Paths.get("images", "avatars").toAbsolutePath().normalize();
        String prefix = "user-" + userId + ".";
        if (!Files.exists(avatarDir)) {
            return null;
        }

        try (Stream<Path> stream = Files.list(avatarDir)) {
            Optional<Path> avatarFile = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(prefix))
                    .sorted(Comparator.comparing(Path::toString).reversed())
                    .findFirst();

            if (avatarFile.isEmpty()) {
                return null;
            }

            Path file = avatarFile.get();
            long lastModified = Files.getLastModifiedTime(file).toMillis();
            return "/images/avatars/" + file.getFileName() + "?v=" + lastModified;
        } catch (IOException ex) {
            return null;
        }
    }
}
