package com.pvr.primenaturals.service;

import com.pvr.primenaturals.dto.response.OrderDTO;
import com.pvr.primenaturals.dto.response.OrderItemDTO;
import com.pvr.primenaturals.entity.*;
import com.pvr.primenaturals.exception.ResourceNotFoundException;
import com.pvr.primenaturals.repository.CartRepository;
import com.pvr.primenaturals.repository.OrderRepository;
import com.pvr.primenaturals.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @org.springframework.transaction.annotation.Transactional
    public OrderDTO placeOrder(String email, String paymentId, String paymentMethod) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Cart cart = cartRepository.findByUserId(user.getId()).orElseThrow(() -> new ResourceNotFoundException("Cart not found"));

        if (cart.getCartItems().isEmpty()) {
            throw new RuntimeException("Cart is empty");
        }

        Order order = new Order();
        order.setUser(user);
        order.setStatus(OrderStatus.PLACED);
        order.setPaymentId(paymentId);
        order.setPaymentMethod(paymentMethod);

        BigDecimal total = BigDecimal.ZERO;
        for (CartItem item : cart.getCartItems()) {
            Product product = item.getProduct();
            
            // 1. Stock Validation
            if (product.getStockQuantity() < item.getQuantity()) {
                throw new com.pvr.primenaturals.exception.InsufficientStockException("Insufficient stock for product: " + product.getName() 
                                           + ". Available: " + product.getStockQuantity());
            }
            
            // 2. Decrement Stock
            product.setStockQuantity(product.getStockQuantity() - item.getQuantity());
            // Implicitly saved by @Transactional if active, but we'll be safer with explicit save if needed 
            // though JPA usually handles this on flush.
            
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(item.getQuantity());
            orderItem.setPriceAtPurchase(product.getPrice());
            order.getOrderItems().add(orderItem);

            BigDecimal itemTotal = product.getPrice().multiply(new BigDecimal(item.getQuantity()));
            total = total.add(itemTotal);

            // Broadcast stock update
            try {
                messagingTemplate.convertAndSend("/topic/products/stock", 
                    new com.pvr.primenaturals.dto.response.StockUpdateDTO(product.getId(), product.getStockQuantity()));
            } catch (Exception e) {
                System.err.println("Failed to send stock update via WebSocket: " + e.getMessage());
            }
        }
        order.setTotalAmount(total);

        Order savedOrder = orderRepository.save(order);

        // 3. Clear the cart
        cart.getCartItems().clear();
        cartRepository.save(cart);

        OrderDTO dto = mapToDTO(savedOrder);
        // Notify admin about new order
        try {
            messagingTemplate.convertAndSend("/topic/orders/admin", dto);
        } catch (Exception e) {
            System.err.println("Failed to send admin order notification via WebSocket: " + e.getMessage());
        }

        // 4. Send Confirmation Email
        emailService.sendOrderConfirmation(dto, email);

        return dto;
    }

    public List<OrderDTO> getUserOrders(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return orderRepository.findByUserId(user.getId()).stream()
                .map(this::mapToDTO).collect(Collectors.toList());
    }

    public List<OrderDTO> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(this::mapToDTO).collect(Collectors.toList());
    }

    @org.springframework.transaction.annotation.Transactional
    public void deleteOrder(Long orderId, String email) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (!order.getUser().getEmail().equals(email)) {
            throw new RuntimeException("Unauthorized: You can only delete your own orders.");
        }
        orderRepository.delete(order);
    }

    @Autowired
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    public OrderDTO updateOrderStatus(Long orderId, String status) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        order.setStatus(OrderStatus.valueOf(status.toUpperCase()));
        Order savedOrder = orderRepository.save(order);
        
        OrderDTO dto = mapToDTO(savedOrder);
        // Notify user about status change via WebSockets
        messagingTemplate.convertAndSend("/topic/orders/" + dto.getUserId(), dto);
        
        // Notify user about status change via Email
        emailService.sendOrderStatusUpdate(dto, savedOrder.getUser().getEmail());
        
        return dto;
    }

    @org.springframework.transaction.annotation.Transactional
    public OrderDTO cancelOrder(Long orderId, String email) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        
        if (!order.getUser().getEmail().equals(email)) {
            throw new RuntimeException("Unauthorized: You can only cancel your own orders.");
        }

        if (order.getStatus() != OrderStatus.PLACED) {
            throw new RuntimeException("Protocol Violation: Only orders in PLACED status can be aborted.");
        }

        // Restore Stock
        for (OrderItem item : order.getOrderItems()) {
            Product product = item.getProduct();
            if (product != null) {
                product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
                // Broadcast stock restoration
                try {
                    messagingTemplate.convertAndSend("/topic/products/stock", 
                        new com.pvr.primenaturals.dto.response.StockUpdateDTO(product.getId(), product.getStockQuantity()));
                } catch (Exception e) {}
            }
        }

        order.setStatus(OrderStatus.CANCELLED);
        Order savedOrder = orderRepository.save(order);

        OrderDTO dto = mapToDTO(savedOrder);
        // Notify Admin & User
        messagingTemplate.convertAndSend("/topic/orders/admin", dto);
        messagingTemplate.convertAndSend("/topic/orders/" + dto.getUserId(), dto);
        emailService.sendOrderStatusUpdate(dto, email);

        return dto;
    }

    private OrderDTO mapToDTO(Order order) {
        OrderDTO dto = new OrderDTO();
        dto.setId(order.getId());
        dto.setUserId(order.getUser().getId());
        dto.setUserName(order.getUser().getName());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setStatus(order.getStatus().name());
        dto.setPaymentId(order.getPaymentId());
        dto.setPaymentMethod(order.getPaymentMethod());
        dto.setCreatedAt(order.getCreatedAt());

        List<OrderItemDTO> items = order.getOrderItems().stream().map(item -> {
            OrderItemDTO iDto = new OrderItemDTO();
            iDto.setId(item.getId());
            if (item.getProduct() != null) {
                iDto.setProductId(item.getProduct().getId());
                iDto.setProductName(item.getProduct().getName());
                iDto.setProductImageUrl(item.getProduct().getImageUrl());
            } else {
                iDto.setProductId(null);
                iDto.setProductName("Deleted Product");
                iDto.setProductImageUrl(null);
            }
            iDto.setQuantity(item.getQuantity());
            iDto.setPriceAtPurchase(item.getPriceAtPurchase());
            iDto.setSubTotal(item.getPriceAtPurchase().multiply(new BigDecimal(item.getQuantity())));
            return iDto;
        }).collect(Collectors.toList());

        dto.setItems(items);
        return dto;
    }
}
