package com.bookstore.controller;

import com.bookstore.model.Book;
import com.bookstore.service.BookService;
import com.bookstore.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/stationery")
public class StationeryController {

    @Autowired
    private BookService bookService;

    @Autowired
    private CategoryService categoryService;

    @GetMapping
    public String listStationery(@RequestParam(required = false) String name,
                                 @RequestParam(required = false) Long categoryId,
                                 @RequestParam(required = false) Double minPrice,
                                 @RequestParam(required = false) Double maxPrice,
                                 @RequestParam(required = false) String sort,
                                 Model model) {

        // Lấy tất cả sản phẩm Stationery (ProductTypeID = 2)
        List<Book> items = bookService.getProductsByProductType(2L);

        // Lọc theo tên hoặc hãng (author đang dùng để lưu brand cho Stationery)
        if (name != null && !name.isEmpty()) {
            String lowerName = name.toLowerCase();
            items = items.stream()
                    .filter(i -> (i.getTitle() != null && i.getTitle().toLowerCase().contains(lowerName)) ||
                                 (i.getAuthor() != null && i.getAuthor().toLowerCase().contains(lowerName)))
                    .collect(Collectors.toList());
        }

        // Lọc theo danh mục
        if (categoryId != null) {
            items = items.stream()
                    .filter(i -> i.getCategory() != null && i.getCategory().getId().equals(categoryId))
                    .collect(Collectors.toList());
        }

        // Lọc theo giá tối thiểu
        if (minPrice != null) {
            items = items.stream()
                    .filter(i -> i.getPrice() != null && i.getPrice().doubleValue() >= minPrice)
                    .collect(Collectors.toList());
        }

        // Lọc theo giá tối đa
        if (maxPrice != null) {
            items = items.stream()
                    .filter(i -> i.getPrice() != null && i.getPrice().doubleValue() <= maxPrice)
                    .collect(Collectors.toList());
        }

        // Xử lý sắp xếp
        if (sort != null) {
            switch (sort) {
                case "name_asc":
                    items.sort(Comparator.comparing(Book::getTitle, Comparator.nullsLast(String::compareToIgnoreCase)));
                    break;
                case "price_asc":
                    items.sort(Comparator.comparing(Book::getPrice, Comparator.nullsLast(Comparator.naturalOrder())));
                    break;
                case "price_desc":
                    items.sort(Comparator.comparing(Book::getPrice, Comparator.nullsLast(Comparator.reverseOrder())));
                    break;
            }
        }

        model.addAttribute("items", items);
        // Lấy danh sách category dành riêng cho văn phòng phẩm để hiển thị trong filter
        model.addAttribute("categories", categoryService.getCategoriesByProductType(2L));

        return "stationery-list";
    }
}
