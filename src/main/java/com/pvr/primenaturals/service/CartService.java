package com.pvr.primenaturals.service;

import com.pvr.primenaturals.dto.request.CartRequest;
import com.pvr.primenaturals.dto.response.CartDTO;
import com.pvr.primenaturals.dto.response.CartItemDTO;
import com.pvr.primenaturals.entity.Cart;
import com.pvr.primenaturals.entity.CartItem;
import com.pvr.primenaturals.entity.Product;
import com.pvr.primenaturals.entity.User;
import com.pvr.primenaturals.exception.ResourceNotFoundException;
import com.pvr.primenaturals.repository.CartRepository;
import com.pvr.primenaturals.repository.ProductRepository;
import com.pvr.primenaturals.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CartService {
    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    public CartDTO getCartForUser(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Cart cart = cartRepository.findByUserId(user.getId()).orElseGet(() -> {
            Cart newCart = new Cart();
            newCart.setUser(user);
            return cartRepository.save(newCart);
        });
        return mapToDTO(cart);
    }

    public CartDTO addToCart(String email, CartRequest request) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Cart cart = cartRepository.findByUserId(user.getId()).orElseGet(() -> {
            Cart newCart = new Cart();
            newCart.setUser(user);
            return cartRepository.save(newCart);
        });

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        Optional<CartItem> existingItem = cart.getCartItems().stream()
                .filter(item -> item.getProduct().getId().equals(product.getId()))
                .findFirst();

        if (existingItem.isPresent()) {
            existingItem.get().setQuantity(existingItem.get().getQuantity() + request.getQuantity());
        } else {
            CartItem newItem = new CartItem();
            newItem.setCart(cart);
            newItem.setProduct(product);
            newItem.setQuantity(request.getQuantity());
            cart.getCartItems().add(newItem);
        }

        Cart updatedCart = cartRepository.save(cart);
        return mapToDTO(updatedCart);
    }

    public CartDTO removeFromCart(String email, Long cartItemId) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Cart cart = cartRepository.findByUserId(user.getId()).orElseThrow(() -> new ResourceNotFoundException("Cart not found"));

        cart.getCartItems().removeIf(item -> item.getId().equals(cartItemId));
        Cart updatedCart = cartRepository.save(cart);
        return mapToDTO(updatedCart);
    }

    public CartDTO updateQuantity(String email, Long cartItemId, int newQuantity) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Cart cart = cartRepository.findByUserId(user.getId()).orElseThrow(() -> new ResourceNotFoundException("Cart not found"));

        if (newQuantity <= 0) {
            return removeFromCart(email, cartItemId);
        }

        for (CartItem item : cart.getCartItems()) {
            if (item.getId().equals(cartItemId)) {
                item.setQuantity(newQuantity);
                break;
            }
        }

        Cart updatedCart = cartRepository.save(cart);
        return mapToDTO(updatedCart);
    }

    private CartDTO mapToDTO(Cart cart) {
        CartDTO dto = new CartDTO();
        dto.setCartId(cart.getId());
        BigDecimal total = BigDecimal.ZERO;

        List<CartItemDTO> itemDTOs = cart.getCartItems().stream().map(item -> {
            CartItemDTO iDto = new CartItemDTO();
            iDto.setId(item.getId());
            iDto.setProductId(item.getProduct().getId());
            iDto.setProductName(item.getProduct().getName());
            iDto.setProductImageUrl(item.getProduct().getImageUrl());
            iDto.setPrice(item.getProduct().getPrice());
            iDto.setQuantity(item.getQuantity());
            BigDecimal sub = item.getProduct().getPrice().multiply(new BigDecimal(item.getQuantity()));
            iDto.setSubTotal(sub);
            return iDto;
        }).collect(Collectors.toList());

        for (CartItemDTO i : itemDTOs) {
            total = total.add(i.getSubTotal());
        }

        dto.setItems(itemDTOs);
        dto.setTotal(total);
        return dto;
    }
}
