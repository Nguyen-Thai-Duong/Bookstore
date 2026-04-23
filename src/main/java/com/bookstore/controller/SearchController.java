package com.bookstore.controller;

import com.bookstore.model.Book;
import com.bookstore.service.BookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Controller
@RequiredArgsConstructor
@Slf4j
public class SearchController {

    private static final long BOOK_PRODUCT_TYPE_ID = 1L;
    private static final long STATIONERY_PRODUCT_TYPE_ID = 2L;

    private final BookService bookService;

    @GetMapping("/search")
    public String search(@RequestParam(name = "q", required = false) String q,
            Model model) {
        String keyword = normalize(q);
        List<SearchResultView> results = keyword.isEmpty() ? List.of() : searchAcrossProducts(keyword);

        model.addAttribute("query", q == null ? "" : q.trim());
        model.addAttribute("results", results);
        model.addAttribute("totalResults", results.size());
        return "search-results";
    }

    @GetMapping("/search/suggest")
    @ResponseBody
    public List<Map<String, Object>> suggest(@RequestParam(name = "q", required = false) String q) {
        String keyword = normalize(q);
        log.info("=== SEARCH SUGGEST: keyword='{}' ===", keyword);
        if (keyword.isEmpty()) {
            return List.of();
        }

        List<SearchResultView> results = searchAcrossProducts(keyword);
        log.info("=== SEARCH SUGGEST: found {} results ===", results.size());

        return results.stream()
                .limit(8)
                .map(result -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", result.getId());
                    item.put("title", result.getTitle());
                    item.put("author", result.getAuthor());
                    item.put("typeName", result.getTypeName());
                    item.put("url", result.getUrl());
                    return item;
                })
                .toList();
    }

    private List<SearchResultView> searchAcrossProducts(String keyword) {
        String normalized = normalize(keyword);

        List<Book> books = bookService.getProductsByProductType(BOOK_PRODUCT_TYPE_ID);
        List<Book> stationery = bookService.getProductsByProductType(STATIONERY_PRODUCT_TYPE_ID);
        log.info("=== SEARCH: books={}, stationery={} ===", books.size(), stationery.size());

        return Stream.concat(books.stream(), stationery.stream())
                .filter(this::isVisibleProduct)
                .filter(book -> matchesKeyword(book, normalized))
                .sorted(Comparator.comparing(Book::getTitle, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(this::toView)
                .collect(Collectors.toList());
    }

    private boolean matchesKeyword(Book book, String keyword) {
        if (keyword.isEmpty()) {
            return true;
        }

        return (book.getTitle() != null && book.getTitle().toLowerCase().contains(keyword))
                || (book.getAuthor() != null && book.getAuthor().toLowerCase().contains(keyword));
    }

    private boolean isVisibleProduct(Book book) {
        boolean visible = book != null
                && !book.isDiscontinued()
                && "Active".equalsIgnoreCase(book.getStatus())
                && book.getCategory() != null
                && book.getCategory().getProductType() != null
                && book.getCategory().getProductType().getId() != null;
        if (!visible && book != null) {
            log.debug("Product {} filtered out: discontinued={}, status={}",
                    book.getId(), book.isDiscontinued(), book.getStatus());
        }
        return visible;
    }

    private SearchResultView toView(Book book) {
        Long productTypeId = book.getCategory() != null && book.getCategory().getProductType() != null
                ? book.getCategory().getProductType().getId()
                : null;
        String typeName = productTypeId != null && productTypeId.equals(STATIONERY_PRODUCT_TYPE_ID)
                ? "Stationery"
                : "Book";
        String url = productTypeId != null && productTypeId.equals(STATIONERY_PRODUCT_TYPE_ID)
                ? "/stationery/" + book.getId()
                : "/books/" + book.getId();

        return new SearchResultView(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getImageUrl(),
                book.getPrice(),
                book.getStock(),
                typeName,
                url);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    public static class SearchResultView {

        private final Long id;
        private final String title;
        private final String author;
        private final String imageUrl;
        private final BigDecimal price;
        private final Integer stock;
        private final String typeName;
        private final String url;

        public SearchResultView(Long id, String title, String author, String imageUrl,
                BigDecimal price, Integer stock, String typeName, String url) {
            this.id = id;
            this.title = title;
            this.author = author;
            this.imageUrl = imageUrl;
            this.price = price;
            this.stock = stock;
            this.typeName = typeName;
            this.url = url;
        }

        public Long getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public String getAuthor() {
            return author;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public Integer getStock() {
            return stock;
        }

        public String getTypeName() {
            return typeName;
        }

        public String getUrl() {
            return url;
        }
    }
}