package com.pvr.primenaturals.service;

import com.pvr.primenaturals.entity.Review;
import com.pvr.primenaturals.repository.ReviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ReviewService {

    @Autowired
    private ReviewRepository reviewRepository;

    public List<com.pvr.primenaturals.dto.response.ReviewDTO> getReviewsByProduct(Long productId) {
        return reviewRepository.findByProductIdOrderByCreatedAtDesc(productId).stream()
                .map(this::mapToDTO).collect(java.util.stream.Collectors.toList());
    }

    public com.pvr.primenaturals.dto.response.ReviewDTO addReview(Review review) {
        if (review.getRating() < 1 || review.getRating() > 5) {
            throw new RuntimeException("Rating must be between 1 and 5");
        }
        Review saved = reviewRepository.save(review);
        return mapToDTO(saved);
    }

    private com.pvr.primenaturals.dto.response.ReviewDTO mapToDTO(Review review) {
        com.pvr.primenaturals.dto.response.ReviewDTO dto = new com.pvr.primenaturals.dto.response.ReviewDTO();
        dto.setId(review.getId());
        dto.setUserId(review.getUser().getId());
        dto.setUserName(review.getUser().getName());
        dto.setProductId(review.getProduct().getId());
        dto.setProductName(review.getProduct().getName());
        dto.setRating(review.getRating());
        dto.setComment(review.getComment());
        dto.setCreatedAt(review.getCreatedAt());
        return dto;
    }
}
