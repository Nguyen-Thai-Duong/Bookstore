package com.bookstore.dto;

import com.bookstore.model.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleDTO {

    private Long id;
    private String roleName;

    public static RoleDTO fromEntity(Role role) {
        if (role == null) {
            return null;
        }

        return new RoleDTO(role.getId(), role.getRoleName());
    }

    public Role toEntity() {
        return new Role(id, roleName, null);
    }
}