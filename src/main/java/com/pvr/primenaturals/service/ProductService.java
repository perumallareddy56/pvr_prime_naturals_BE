package com.pvr.primenaturals.service;

import com.pvr.primenaturals.entity.Product;
import com.pvr.primenaturals.entity.ProductSubCategory;
import com.pvr.primenaturals.entity.ProductType;
import com.pvr.primenaturals.exception.ResourceNotFoundException;
import com.pvr.primenaturals.repository.*;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    @Autowired
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    private void broadcastProductSync() {
        messagingTemplate.convertAndSend("/topic/products/sync", "REFRESH");
    }

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductTypeRepository productTypeRepository;

    @Autowired
    private ProductSubCategoryRepository productSubCategoryRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private WishlistRepository wishlistRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    public List<ProductType> getAllProductTypes() {
        return productTypeRepository.findAll();
    }

    public List<ProductSubCategory> getSubCategoriesByType(Long typeId) {
        return productSubCategoryRepository.findByProductTypeId(typeId);
    }

    public List<ProductSubCategory> getAllSubCategories() {
        return productSubCategoryRepository.findAll();
    }

    public ProductType addProductType(ProductType type) {
        return productTypeRepository.save(type);
    }

    public ProductSubCategory addSubCategory(ProductSubCategory subCategory) {
        return productSubCategoryRepository.save(subCategory);
    }

    @Transactional
    public void deleteProductType(Long id) {
        List<ProductSubCategory> subCats = productSubCategoryRepository.findByProductTypeId(id);
        for (ProductSubCategory sc : subCats) {
            deleteProductSubCategory(sc.getId());
        }
        productTypeRepository.deleteById(id);
    }

    @Transactional
    public void deleteProductSubCategory(Long id) {
        List<Product> products = productRepository.findBySubCategoryId(id);
        for (Product p : products) {
            deleteProduct(p.getId());
        }
        productSubCategoryRepository.deleteById(id);
    }

    @Cacheable(value = "products", key = "'active'")
    public List<Product> getAllActiveProducts() {
        return productRepository.findByActiveTrue();
    }

    public List<Product> getAllProductsForAdmin() {
        return productRepository.findAll();
    }

    @Cacheable(value = "products", key = "#id")
    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id " + id));
    }

    @Cacheable(value = "products", key = "#name + '_' + #typeName + '_' + #activeOnly")
    public List<Product> searchProducts(String name, String typeName, boolean activeOnly) {
        if (typeName != null && !typeName.trim().isEmpty() && !typeName.equalsIgnoreCase("All")) {
            return activeOnly ? productRepository.findByActiveTrueAndNameContainingIgnoreCaseAndSubCategoryProductTypeName(name, typeName)
                              : productRepository.findByNameContainingIgnoreCaseAndSubCategoryProductTypeName(name, typeName);
        }
        return activeOnly ? productRepository.findByActiveTrueAndNameContainingIgnoreCase(name) 
                          : productRepository.findByNameContainingIgnoreCase(name);
    }

    @Cacheable(value = "products", key = "#name + '_' + #activeOnly")
    public List<Product> getProductsByTypeName(String name, boolean activeOnly) {
        return activeOnly ? productRepository.findByActiveTrueAndSubCategoryProductTypeName(name)
                          : productRepository.findBySubCategoryProductTypeName(name);
    }

    @CacheEvict(value = "products", allEntries = true)
    public Product addProduct(Product product) {
        Product p = productRepository.save(product);
        broadcastProductSync();
        return p;
    }

    @CacheEvict(value = "products", allEntries = true)
    public Product updateProduct(Long id, Product newProductData) {
        Product existingProduct = getProductById(id);
        existingProduct.setName(newProductData.getName());
        existingProduct.setDescription(newProductData.getDescription());
        existingProduct.setPrice(newProductData.getPrice());
        existingProduct.setStockQuantity(newProductData.getStockQuantity());
        existingProduct.setImageUrl(newProductData.getImageUrl());
        existingProduct.setWeight(newProductData.getWeight());
        if (newProductData.getSubCategory() != null && newProductData.getSubCategory().getId() != null) {
            ProductSubCategory subCat = productSubCategoryRepository.findById(newProductData.getSubCategory().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Sub-category not found"));
            existingProduct.setSubCategory(subCat);
        }
        Product p = productRepository.save(existingProduct);
        broadcastProductSync();
        return p;
    }

    @CacheEvict(value = "products", allEntries = true, beforeInvocation = true)
    @Transactional
    public void deleteProduct(Long id) {
        Product product = getProductById(id);
        
        // Check if product is in any active orders (simplified: check if any order items exist)
        boolean hasOrders = orderItemRepository.existsByProductId(id);
        
        if (hasOrders) {
            // Soft delete: just mark as inactive to preserve order history
            product.setActive(false);
            productRepository.save(product);
        } else {
            // Hard delete: clean up associations and remove from DB
            cartItemRepository.deleteByProductId(id);
            wishlistRepository.deleteByProductId(id);
            reviewRepository.deleteByProductId(id);
            productRepository.deleteById(id);
        }
        broadcastProductSync();
    }

    @CacheEvict(value = "products", allEntries = true, beforeInvocation = true)
    @Transactional
    public void restoreProduct(Long id) {
        Product product = getProductById(id);
        product.setActive(true);
        productRepository.save(product);
        broadcastProductSync();
    }
}
