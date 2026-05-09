package com.secondhand.platform.modules.product;

import com.secondhand.platform.modules.product.application.CreateProductRequest;
import com.secondhand.platform.modules.product.application.ProductApplicationService;
import com.secondhand.platform.shared.kernel.Result;
import com.secondhand.platform.shared.web.CurrentUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductApplicationService productApplicationService;
    private final CurrentUserResolver currentUserResolver;

    public ProductController(ProductApplicationService productApplicationService, CurrentUserResolver currentUserResolver) {
        this.productApplicationService = productApplicationService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping
    public Result<List<ProductListItemResponse>> list() {
        return Result.ok(productApplicationService.listProducts());
    }

    @GetMapping("/seller/{sellerId}")
    public Result<List<ProductListItemResponse>> listSellerProducts(@PathVariable Long sellerId) {
        return Result.ok(productApplicationService.listProductsBySeller(sellerId));
    }

    @GetMapping("/mine")
    public Result<List<ProductListItemResponse>> listMine(HttpServletRequest request) {
        long sellerId = currentUserResolver.resolve(request);
        return Result.ok(productApplicationService.listMyProducts(sellerId));
    }

    @GetMapping("/favorites")
    public Result<List<ProductListItemResponse>> listFavorites(HttpServletRequest request) {
        long userId = currentUserResolver.resolve(request);
        return Result.ok(productApplicationService.listFavorites(userId));
    }

    @PostMapping("/{productId}/favorite")
    public Result<Void> favorite(@PathVariable Long productId, HttpServletRequest request) {
        long userId = currentUserResolver.resolve(request);
        productApplicationService.favoriteProduct(userId, productId);
        return Result.ok(null);
    }

    @DeleteMapping("/{productId}/favorite")
    public Result<Void> unfavorite(@PathVariable Long productId, HttpServletRequest request) {
        long userId = currentUserResolver.resolve(request);
        productApplicationService.unfavoriteProduct(userId, productId);
        return Result.ok(null);
    }

    @GetMapping("/{productId}")
    public Result<ProductDetailResponse> detail(@PathVariable Long productId) {
        return Result.ok(productApplicationService.detailProduct(productId));
    }

    @PostMapping
    public Result<CreateProductResponse> create(@RequestBody CreateProductRequest request, HttpServletRequest httpRequest) {
        long sellerId = currentUserResolver.resolve(httpRequest);
        return Result.ok(productApplicationService.createProduct(sellerId, request));
    }

    @PutMapping("/{productId}")
    public Result<UpdateProductResponse> update(@PathVariable Long productId, @RequestBody CreateProductRequest body, HttpServletRequest request) {
        long userId = currentUserResolver.resolve(request);
        return Result.ok(productApplicationService.updateProduct(userId, productId, body));
    }
}
