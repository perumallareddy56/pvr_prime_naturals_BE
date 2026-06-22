package com.pvr.primenaturals.controller;

import com.pvr.primenaturals.entity.Product;
import com.pvr.primenaturals.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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



    @GetMapping
    public List<Product> getAllProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        
        // Potential security improvement: force activeOnly=true for non-admins if needed
        // For now, respect the parameter which defaults to true
        
        if (search != null && !search.isEmpty()) {
            return productService.searchProducts(search, type, activeOnly);
        }
        if (type != null && !type.isEmpty() && !type.equalsIgnoreCase("All")) {
            return productService.getProductsByTypeName(type, activeOnly);
        }
        
        return activeOnly ? productService.getAllActiveProducts() : productService.getAllProductsForAdmin();
    }

    @GetMapping("/{id:[0-9]+}")
    public Product getProductById(@PathVariable Long id) {
        return productService.getProductById(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Product addProduct(@RequestBody Product product) {
        return productService.addProduct(product);
    }

    @PutMapping("/{id:[0-9]+}")
    @PreAuthorize("hasRole('ADMIN')")
    public Product updateProduct(@PathVariable Long id, @RequestBody Product product) {
        return productService.updateProduct(id, product);
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
