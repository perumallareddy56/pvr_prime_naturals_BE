package com.pvr.primenaturals.controller;

import com.pvr.primenaturals.dto.response.ProductResponseDTO;
import com.pvr.primenaturals.entity.Product;
import com.pvr.primenaturals.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/products")
public class ProductController {

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("pong");
    }

    @Autowired
    private ProductService productService;

    private ProductResponseDTO mapToResponseDTO(Product p) {
        if (p == null) return null;
        return ProductResponseDTO.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .price(p.getPrice())
                .stockQuantity(p.getStockQuantity())
                .imageUrl(p.getImageUrl())
                .weight(p.getWeight())
                .process(p.getProcess())
                .subCategoryId(p.getSubCategory() != null ? p.getSubCategory().getId() : null)
                .subCategoryName(p.getSubCategory() != null ? p.getSubCategory().getName() : null)
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .active(p.isActive())
                .build();
    }

    @GetMapping
    public List<ProductResponseDTO> getAllProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        
        List<Product> products;
        if (search != null && !search.isEmpty()) {
            products = productService.searchProducts(search, type, activeOnly);
        } else if (type != null && !type.isEmpty() && !type.equalsIgnoreCase("All")) {
            products = productService.getProductsByTypeName(type, activeOnly);
        } else {
            products = activeOnly ? productService.getAllActiveProducts() : productService.getAllProductsForAdmin();
        }
        return products.stream().map(this::mapToResponseDTO).collect(Collectors.toList());
    }

    @GetMapping("/{id:[0-9]+}")
    public ProductResponseDTO getProductById(@PathVariable Long id) {
        return mapToResponseDTO(productService.getProductById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponseDTO addProduct(@RequestBody Product product) {
        return mapToResponseDTO(productService.addProduct(product));
    }

    @PutMapping("/{id:[0-9]+}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponseDTO updateProduct(@PathVariable Long id, @RequestBody Product product) {
        return mapToResponseDTO(productService.updateProduct(id, product));
    }

    @DeleteMapping("/{id:[0-9]+}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id:[0-9]+}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> restoreProduct(@PathVariable Long id) {
        productService.restoreProduct(id);
        return ResponseEntity.ok().build();
    }

    // Category Management Aliases
    @GetMapping("/types")
    public List<com.pvr.primenaturals.entity.ProductType> getAllProductTypes() {
        return productService.getAllProductTypes();
    }

    @PostMapping("/types")
    @PreAuthorize("hasRole('ADMIN')")
    public com.pvr.primenaturals.entity.ProductType addProductType(@RequestBody com.pvr.primenaturals.entity.ProductType type) {
        return productService.addProductType(type);
    }

    @DeleteMapping("/types/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteProductType(@PathVariable Long id) {
        productService.deleteProductType(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/subcategories")
    public List<com.pvr.primenaturals.entity.ProductSubCategory> getAllSubCategories() {
        return productService.getAllSubCategories();
    }

    @PostMapping("/subcategories")
    @PreAuthorize("hasRole('ADMIN')")
    public com.pvr.primenaturals.entity.ProductSubCategory addSubCategory(@RequestBody com.pvr.primenaturals.entity.ProductSubCategory subCategory) {
        return productService.addSubCategory(subCategory);
    }

    @DeleteMapping("/subcategories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteSubCategory(@PathVariable Long id) {
        productService.deleteProductSubCategory(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/types/{typeId}/subcategories")
    public List<com.pvr.primenaturals.entity.ProductSubCategory> getSubCategories(@PathVariable Long typeId) {
        return productService.getSubCategoriesByType(typeId);
    }
}
