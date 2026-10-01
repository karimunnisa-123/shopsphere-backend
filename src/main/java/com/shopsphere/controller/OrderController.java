package com.shopsphere.controller;

import com.shopsphere.entity.Order;
import com.shopsphere.entity.OrderItem;
import com.shopsphere.entity.Product;
import com.shopsphere.entity.User;
import com.shopsphere.repository.OrderRepository;
import com.shopsphere.repository.ProductRepository;
import com.shopsphere.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public OrderController(OrderRepository orderRepository,
                           ProductRepository productRepository,
                           UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @PostMapping
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> placeOrder(@RequestBody Map<String, Object> body, Authentication auth) {
        try {
            String username = auth.getName();
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String address = (String) body.get("shippingAddress");
            String phone = (String) body.get("phone");

            List<Map<String, Object>> itemMaps = new ArrayList<>();
            Object rawItems = body.get("items");
            if (rawItems instanceof List<?> rawList) {
                for (Object o : rawList) {
                    if (o instanceof Map<?, ?> m) {
                        itemMaps.add((Map<String, Object>) m);
                    }
                }
            }

            if (itemMaps.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Cart is empty"));
            }

            Order order = new Order();
            order.setUser(user);
            order.setShippingAddress(address);
            order.setPhone(phone);

            BigDecimal total = BigDecimal.ZERO;

            for (Map<String, Object> itemMap : itemMaps) {
                Long productId = Long.parseLong(itemMap.get("productId").toString());
                Integer qty = Integer.parseInt(itemMap.get("quantity").toString());

                Product product = productRepository.findById(productId)
                        .orElseThrow(() -> new RuntimeException("Product not found: " + productId));

                if (product.getStock() < qty) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Not enough stock for: " + product.getName()));
                }

                product.setStock(product.getStock() - qty);
                productRepository.save(product);

                OrderItem orderItem = new OrderItem(
                        order, product, product.getName(), product.getPrice(), qty
                );
                order.getItems().add(orderItem);

                total = total.add(product.getPrice().multiply(BigDecimal.valueOf(qty)));
            }

            order.setTotalAmount(total);
            Order saved = orderRepository.save(order);
            return ResponseEntity.ok(saved);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/my")
    public ResponseEntity<?> myOrders(Authentication auth) {
        User user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(orderRepository.findByUserOrderByCreatedAtDesc(user));
    }

    @GetMapping
    public ResponseEntity<?> allOrders() {
        return ResponseEntity.ok(orderRepository.findAllByOrderByCreatedAtDesc());
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        order.setStatus(Order.Status.valueOf(body.get("status")));
        return ResponseEntity.ok(orderRepository.save(order));
    }
}