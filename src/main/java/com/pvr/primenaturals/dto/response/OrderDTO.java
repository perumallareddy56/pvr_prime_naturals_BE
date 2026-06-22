package com.pvr.primenaturals.dto.response;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderDTO {
    private Long id;
    private Long userId;
    private String userName;
    private BigDecimal totalAmount;
    private String status;
    private String paymentId;
    private String paymentMethod;
    private LocalDateTime createdAt;
    private List<OrderItemDTO> items;
}
