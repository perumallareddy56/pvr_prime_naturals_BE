package com.pvr.primenaturals.controller;

import com.pvr.primenaturals.entity.Product;
import com.pvr.primenaturals.entity.Review;
import com.pvr.primenaturals.entity.User;
import com.pvr.primenaturals.repository.UserRepository;
import com.pvr.primenaturals.security.UserDetailsImpl;
import com.pvr.primenaturals.service.ProductService;
import com.pvr.primenaturals.service.ReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private ProductService productService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.pvr.primenaturals.repository.ReviewRepository reviewRepository;

    @GetMapping("/product/{productId}")
    public List<com.pvr.primenaturals.dto.response.ReviewDTO> getProductReviews(@PathVariable Long productId) {
        return reviewService.getReviewsByProduct(productId);
    }

    @PostMapping("/product/{productId}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<?> addReview(@PathVariable Long productId, @RequestBody Review reviewRequest) {
        Product product = productService.getProductById(productId);
        
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepository.findById(userDetails.getId()).orElseThrow();

        Review review = Review.builder()
                .product(product)
                .user(user)
                .rating(reviewRequest.getRating())
                .comment(reviewRequest.getComment())
                .build();

        com.pvr.primenaturals.dto.response.ReviewDTO savedReview = reviewService.addReview(review);
        return ResponseEntity.ok(savedReview);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteReview(@PathVariable Long id) {
        reviewRepository.deleteById(id);
        return ResponseEntity.ok("Review deleted");
    }
}
