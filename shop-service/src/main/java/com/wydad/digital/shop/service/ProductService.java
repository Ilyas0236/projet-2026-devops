package com.wydad.digital.shop.service;

import com.wydad.digital.shop.dto.ProductDto;
import com.wydad.digital.shop.dto.ProductRequest;
import com.wydad.digital.shop.enums.SportSection;
import com.wydad.digital.shop.model.Category;
import com.wydad.digital.shop.model.Product;
import com.wydad.digital.shop.model.ProductImage;
import com.wydad.digital.shop.model.ProductVariant;
import com.wydad.digital.shop.repository.CategoryRepository;
import com.wydad.digital.shop.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

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

    @Transactional
    public ProductDto createProduct(ProductRequest request) {
        Product product = applyRequest(new Product(), request);

        if (product.getSku() == null || product.getSku().isBlank()) {
            product.setSku(generateSku(product.getName()));
        }
        if (product.getSlug() == null || product.getSlug().isBlank()) {
            product.setSlug(generateSlug(product.getName()) + "-" + System.currentTimeMillis());
        }
        return mapToDto(productRepository.save(product));
    }

    @Transactional
    public ProductDto updateProduct(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produit non trouvé: " + id));
        // slug/sku existants conservés : ne pas casser les liens et références
        applyRequest(product, request, false);
        return mapToDto(productRepository.save(product));
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produit non trouvé: " + id));
        productRepository.delete(product);
    }

    private Product applyRequest(Product product, ProductRequest request) {
        applyRequest(product, request, true);
        return product;
    }

    private void applyRequest(Product product, ProductRequest request, boolean generateDefaults) {
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setBasePrice(request.getBasePrice());
        product.setMainImageUrl(request.getMainImageUrl());
        product.setSportSection(request.getSportSection());
        if (request.getActive() != null) {
            product.setActive(request.getActive());
        }
        if (request.getCategoryName() != null && !request.getCategoryName().isBlank()) {
            Category category = categoryRepository.findByNameIgnoreCase(request.getCategoryName())
                    .or(() -> categoryRepository.findBySlug(request.getCategoryName()))
                    .orElseGet(() -> categoryRepository.save(Category.builder()
                            .name(request.getCategoryName())
                            .slug(generateSlug(request.getCategoryName()))
                            .active(true)
                            .build()));
            product.setCategory(category);
        }
        if (request.getSku() != null && !request.getSku().isBlank()) {
            product.setSku(request.getSku());
        } else if (generateDefaults) {
            product.setSku(null); // sera généré dans createProduct
        }

        // Stock : une seule variante UNIQUE (le formulaire admin gère le stock global)
        Integer stock = request.getStockQuantity();
        if (stock != null && stock >= 0) {
            ProductVariant variant = product.getVariants().isEmpty()
                    ? ProductVariant.builder().product(product).size(com.wydad.digital.shop.enums.ProductSize.UNIQUE).build()
                    : product.getVariants().get(0);
            variant.setStockQuantity(stock);
            if (variant.getSku() == null || variant.getSku().isBlank()) {
                variant.setSku("VAR-" + product.getName().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "-"));
            }
            if (!product.getVariants().contains(variant)) {
                product.getVariants().add(variant);
            }
        }

        // Image principale : miroir dans la table images pour l'affichage public
        if (request.getMainImageUrl() != null && !request.getMainImageUrl().isBlank()
                && product.getImages().stream().noneMatch(i -> request.getMainImageUrl().equals(i.getUrl()))) {
            ProductImage image = ProductImage.builder()
                    .url(request.getMainImageUrl())
                    .primaryImage(true)
                    .displayOrder(0)
                    .product(product)
                    .build();
            product.getImages().add(image);
        }
    }

    private String generateSku(String name) {
        String base = name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "-");
        String sku = "WAC-" + base;
        int i = 2;
        while (productRepository.existsBySku(sku)) {
            sku = "WAC-" + base + "-" + i++;
        }
        return sku;
    }

    private String generateSlug(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
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