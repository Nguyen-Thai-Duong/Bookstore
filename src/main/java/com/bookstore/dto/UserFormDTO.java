package com.bookstore.dto;

import com.bookstore.model.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserFormDTO {

    private Long id;
    private String fullName;
    private String email;
    private String password;
    private String phone;
    private String address;
    private String gender;
    private LocalDate dateOfBirth;
    private String status;
    private LocalDateTime createdAt;
    private RoleDTO role;

    public static UserFormDTO fromEntity(User user) {
        if (user == null) {
            return null;
        }

        return new UserFormDTO(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                null,
                user.getPhone(),
                user.getAddress(),
                user.getGender(),
                user.getDateOfBirth(),
                user.getStatus(),
                user.getCreatedAt(),
                RoleDTO.fromEntity(user.getRole()));
    }

    public User toEntity(String encodedPassword) {
        return new User(
                id,
                fullName,
                email,
                encodedPassword,
                phone,
                address,
                gender,
                dateOfBirth,
                status,
                createdAt,
                role == null ? null : role.toEntity(),
                null);
    }
}