package com.bookstore.dto;

import com.bookstore.model.Category;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryDTO {

    private Long id;
    private String name;
    private String description;
    private Long bookCount;

    public static CategoryDTO fromEntity(Category category) {
        if (category == null) {
            return null;
        }

        return new CategoryDTO(category.getId(), category.getName(), category.getDescription(), 0L);
    }

    public Category toEntity() {
        return new Category(id, name, description, null);
    }
}