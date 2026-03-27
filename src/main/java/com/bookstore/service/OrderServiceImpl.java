package com.bookstore.service;

import com.bookstore.model.Book;
import com.bookstore.model.OrderDetail;
import com.bookstore.model.Order;
import com.bookstore.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final BookService bookService;

    @Override
    public List<Order> getAllOrders() {
        return orderRepository.findAll(Sort.by(
                Sort.Order.desc("orderDate"),
                Sort.Order.desc("id")));
    }

    @Override
    public Optional<Order> getOrderById(Long id) {
        return orderRepository.findById(id);
    }

    @Override
    public List<Order> getOrdersByUserId(Long userId) {
        return orderRepository.findByUserId(userId);
    }

    @Override
    public List<Order> getOrdersByStatus(String status) {
        return orderRepository.findByStatus(status);
    }

    @Override
    public List<Order> getOrdersByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return orderRepository.findByOrderDateBetween(startDate, endDate);
    }

    @Override
    public Order saveOrder(Order order) {
        if (order.getId() == null) {
            return orderRepository.save(order);
        }

        Order existingOrder = orderRepository.findById(order.getId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng"));

        String oldStatus = normalizeStatus(existingOrder.getStatus());
        String newStatus = normalizeStatus(order.getStatus());

        if (shouldDeductStock(oldStatus, newStatus)) {
            deductStockForOrder(existingOrder);
        }

        if (shouldRestock(oldStatus, newStatus)) {
            restockForOrder(existingOrder);
        }

        existingOrder.setStatus(normalizeDisplayStatus(order.getStatus()));
        existingOrder.setShippingAddress(order.getShippingAddress());

        return orderRepository.save(existingOrder);
    }

    @Override
    public void deleteOrder(Long id) {
        orderRepository.deleteById(id);
    }

    @Override
    public Map<String, Object> getRevenueStatistics(String period, LocalDateTime startDate, LocalDateTime endDate) {
        String normalizedPeriod = normalizePeriod(period);
        LocalDateTime safeStart = alignStartForPeriod(startDate, normalizedPeriod);
        LocalDateTime safeEnd = endDate != null ? endDate : LocalDateTime.now();

        if (safeEnd.isBefore(safeStart)) {
            LocalDateTime temp = safeStart;
            safeStart = safeEnd;
            safeEnd = temp;
        }

        LinkedHashMap<String, BigDecimal> revenueByPeriod = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> booksSoldByPeriod = new LinkedHashMap<>();

        LocalDateTime cursor = safeStart;
        while (!cursor.isAfter(safeEnd)) {
            String label = formatLabel(cursor, normalizedPeriod);
            revenueByPeriod.putIfAbsent(label, BigDecimal.ZERO);
            booksSoldByPeriod.putIfAbsent(label, 0);
            cursor = incrementPeriod(cursor, normalizedPeriod);
        }

        List<Order> orders = orderRepository.findByOrderDateBetween(safeStart, safeEnd);
        for (Order order : orders) {
            if (!isCompletedStatus(order.getStatus())) {
                continue;
            }

            LocalDateTime orderDate = order.getOrderDate();
            if (orderDate == null) {
                continue;
            }

            String label = formatLabel(orderDate, normalizedPeriod);
            if (!revenueByPeriod.containsKey(label)) {
                continue;
            }

            BigDecimal currentRevenue = revenueByPeriod.get(label);
            BigDecimal orderRevenue = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
            revenueByPeriod.put(label, currentRevenue.add(orderRevenue));

            int currentBooksSold = booksSoldByPeriod.get(label);
            int booksFromOrder = order.getOrderDetails() == null
                    ? 0
                    : order.getOrderDetails().stream()
                            .mapToInt(detail -> detail.getQuantity() != null ? detail.getQuantity() : 0)
                            .sum();
            booksSoldByPeriod.put(label, currentBooksSold + booksFromOrder);
        }

        List<String> labels = new ArrayList<>(revenueByPeriod.keySet());
        List<Double> revenues = revenueByPeriod.values().stream()
                .map(value -> value.setScale(2, RoundingMode.HALF_UP).doubleValue())
                .toList();
        List<Integer> booksSold = labels.stream()
                .map(label -> booksSoldByPeriod.getOrDefault(label, 0))
                .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("labels", labels);
        result.put("revenue", revenues);
        result.put("booksSold", booksSold);
        return result;
    }

    private boolean isCompletedStatus(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }

        String normalized = Normalizer.normalize(status, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();

        return normalized.equals("completed")
                || normalized.equals("hoan thanh")
                || normalized.equals("da giao");
    }

    private String normalizeDisplayStatus(String status) {
        String normalized = normalizeStatus(status);
        if (normalized.equals("pending")) {
            return "Pending";
        }
        if (normalized.equals("xac nhan don") || normalized.equals("confirmed")) {
            return "Xác nhận đơn";
        }
        if (normalized.equals("dang giao") || normalized.equals("shipping")) {
            return "Đang giao";
        }
        if (normalized.equals("hoan thanh") || normalized.equals("completed") || normalized.equals("da giao")
                || normalized.equals("delivered")) {
            return "Hoàn thành";
        }
        if (normalized.equals("da huy") || normalized.equals("cancelled") || normalized.equals("canceled")) {
            return "Đã hủy";
        }

        return status;
    }

    private String normalizeStatus(String status) {
        if (status == null) {
            return "";
        }

        return Normalizer.normalize(status, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private boolean isDeductedStatus(String normalizedStatus) {
        return normalizedStatus.equals("xac nhan don")
                || normalizedStatus.equals("confirmed")
                || normalizedStatus.equals("dang giao")
                || normalizedStatus.equals("shipping")
                || normalizedStatus.equals("hoan thanh")
                || normalizedStatus.equals("completed")
                || normalizedStatus.equals("da giao")
                || normalizedStatus.equals("delivered");
    }

    private boolean isCancelledStatus(String normalizedStatus) {
        return normalizedStatus.equals("da huy")
                || normalizedStatus.equals("cancelled")
                || normalizedStatus.equals("canceled");
    }

    private boolean shouldDeductStock(String oldStatus, String newStatus) {
        return !isDeductedStatus(oldStatus) && isDeductedStatus(newStatus);
    }

    private boolean shouldRestock(String oldStatus, String newStatus) {
        return isDeductedStatus(oldStatus) && !isCancelledStatus(oldStatus) && isCancelledStatus(newStatus);
    }

    private void deductStockForOrder(Order order) {
        if (order.getOrderDetails() == null || order.getOrderDetails().isEmpty()) {
            return;
        }

        for (OrderDetail detail : order.getOrderDetails()) {
            if (detail.getBook() == null || detail.getBook().getId() == null) {
                continue;
            }

            Book book = detail.getBook();
            int quantity = detail.getQuantity() == null ? 0 : detail.getQuantity();
            int currentStock = book.getStock() == null ? 0 : Math.max(book.getStock(), 0);

            if (quantity > currentStock) {
                throw new IllegalStateException("Không đủ tồn kho để xác nhận đơn");
            }

            book.setStock(currentStock - quantity);
            bookService.saveBook(book);
        }
    }

    private void restockForOrder(Order order) {
        if (order.getOrderDetails() == null || order.getOrderDetails().isEmpty()) {
            return;
        }

        for (OrderDetail detail : order.getOrderDetails()) {
            if (detail.getBook() == null || detail.getBook().getId() == null) {
                continue;
            }

            Book book = detail.getBook();
            int quantity = detail.getQuantity() == null ? 0 : detail.getQuantity();
            int currentStock = book.getStock() == null ? 0 : Math.max(book.getStock(), 0);

            book.setStock(currentStock + Math.max(quantity, 0));
            bookService.saveBook(book);
        }
    }

    private String normalizePeriod(String period) {
        if (period == null) {
            return "day";
        }
        String normalized = period.toLowerCase(Locale.ROOT).trim();
        if (!normalized.equals("day") && !normalized.equals("month") && !normalized.equals("year")) {
            return "day";
        }
        return normalized;
    }

    private LocalDateTime alignStartForPeriod(LocalDateTime input, String period) {
        LocalDateTime source = input != null ? input : LocalDateTime.now();
        switch (period) {
            case "month":
                YearMonth ym = YearMonth.of(source.getYear(), source.getMonth());
                return ym.atDay(1).atStartOfDay();
            case "year":
                return LocalDate.of(source.getYear(), 1, 1).atStartOfDay();
            case "day":
            default:
                return source.toLocalDate().atStartOfDay();
        }
    }

    private LocalDateTime incrementPeriod(LocalDateTime input, String period) {
        switch (period) {
            case "month":
                return input.plusMonths(1);
            case "year":
                return input.plusYears(1);
            case "day":
            default:
                return input.plusDays(1);
        }
    }

    private String formatLabel(LocalDateTime input, String period) {
        DateTimeFormatter formatter;
        switch (period) {
            case "month":
                formatter = DateTimeFormatter.ofPattern("MM/yyyy");
                break;
            case "year":
                formatter = DateTimeFormatter.ofPattern("yyyy");
                break;
            case "day":
            default:
                formatter = DateTimeFormatter.ofPattern("dd/MM");
                break;
        }
        return input.format(formatter);
    }
}
