package com.flashsale.product;
import com.flashsale.common.api.ApiResponse; import com.flashsale.common.security.*; import com.flashsale.common.trace.TraceContext; import jakarta.validation.Valid; import jakarta.validation.constraints.*; import org.springframework.web.bind.annotation.*; import java.util.List;
@RestController @RequestMapping("/api/v1") class ProductController {private final ProductService service;private final JwtTokenService tokens; ProductController(ProductService s,JwtTokenService t){service=s;tokens=t;}
 @GetMapping("/products") ApiResponse<List<Product>> list(){return ApiResponse.success(service.list(true),TraceContext.getOrCreate());}
 @GetMapping("/products/{id}") ApiResponse<Product> get(@PathVariable long id){return ApiResponse.success(service.get(id),TraceContext.getOrCreate());}
 @PostMapping("/admin/products") ApiResponse<Product> create(@RequestHeader("Authorization")String h,@Valid@RequestBody Request r){return ok(service.create(actor(h),r.toProduct()));}
 @PutMapping("/admin/products/{id}") ApiResponse<Product> update(@RequestHeader("Authorization")String h,@PathVariable long id,@Valid@RequestBody Request r){return ok(service.update(actor(h),id,r.toProduct()));}
 @PostMapping("/admin/products/{id}/on-sale") ApiResponse<Product> on(@RequestHeader("Authorization")String h,@PathVariable long id){return ok(service.changeStatus(actor(h),id,"ON_SALE"));}
 @PostMapping("/admin/products/{id}/off-sale") ApiResponse<Product> off(@RequestHeader("Authorization")String h,@PathVariable long id){return ok(service.changeStatus(actor(h),id,"OFF_SALE"));}
 private ApiResponse<Product> ok(Product p){return ApiResponse.success(p,TraceContext.getOrCreate());} private Principal actor(String h){try{return tokens.parse(h.substring(7));}catch(Exception e){throw new IllegalArgumentException("UNAUTHENTICATED");}}
 record Request(@NotBlank String sku,@NotBlank String name,String description,@Min(0)long priceMinor,@Min(0)int stock){Product toProduct(){return new Product(0,sku,name,description,priceMinor,stock,null,0);}}
}
