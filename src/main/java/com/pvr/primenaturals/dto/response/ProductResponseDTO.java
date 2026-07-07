package com.pvr.primenaturals.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponseDTO {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stockQuantity;
    private String imageUrl;
    private String weight;
    private List<String> process;
    private Long subCategoryId;
    private String subCategoryName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean active;
}
