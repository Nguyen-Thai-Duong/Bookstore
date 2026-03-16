package com.bookstore.service;

import com.bookstore.model.Role;
import java.util.List;
import java.util.Optional;

public interface RoleService {
    List<Role> getAllRoles();

    Optional<Role> getRoleById(Long id);

    Optional<Role> getRoleByName(String roleName);

    Role saveRole(Role role);

    void deleteRole(Long id);
}
