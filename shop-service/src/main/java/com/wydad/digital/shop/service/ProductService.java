package com.wydad.digital.shop.service;

import com.wydad.digital.shop.dto.ProductDto;
import com.wydad.digital.shop.enums.SportSection;
import com.wydad.digital.shop.model.Product;
import com.wydad.digital.shop.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    public Page<ProductDto> getAllActiveProducts(Pageable pageable) {
        return productRepository.findByActiveTrue(pageable)
                .map(this::mapToDto);
    }

    public Page<ProductDto> getProductsByCategory(Long categoryId, Pageable pageable) {
        return productRepository.findByCategoryIdAndActiveTrue(categoryId, pageable)
                .map(this::mapToDto);
    }

    public Page<ProductDto> getProductsBySport(SportSection sportSection, Pageable pageable) {
        return productRepository.findBySportSectionAndActiveTrue(sportSection, pageable)
                .map(this::mapToDto);
    }

    public Page<ProductDto> searchProducts(String query, Pageable pageable) {
        return productRepository.findByNameContainingIgnoreCaseAndActiveTrue(query, pageable)
                .map(this::mapToDto);
    }

    public ProductDto getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produit non trouvé: " + id));
        return mapToDto(product);
    }

    private ProductDto mapToDto(Product p) {
        return ProductDto.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .basePrice(p.getBasePrice())
                .sku(p.getSku())
                .slug(p.getSlug())
                .customizable(p.getCustomizable())
                .active(p.getActive())
                .averageRating(p.getAverageRating())
                .reviewCount(p.getReviewCount())
                .sportSection(p.getSportSection() != null ? p.getSportSection().name() : null)
                .categoryName(p.getCategory() != null ? p.getCategory().getName() : null)
                .variants(p.getVariants().stream()
                        .map(v -> ProductDto.VariantDto.builder()
                                .id(v.getId())
                                .size(v.getSize() != null ? v.getSize().name() : null)
                                .color(v.getColor())
                                .colorHex(v.getColorHex())
                                .stockQuantity(v.getStockQuantity())
                                .build())
                        .toList())
                .images(p.getImages().stream()
                        .map(i -> i.getUrl())
                        .toList())
                .build();
    }
}