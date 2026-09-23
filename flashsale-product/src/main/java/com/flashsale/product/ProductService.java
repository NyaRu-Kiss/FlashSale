package com.flashsale.product;
import com.flashsale.common.security.Principal; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import java.util.List;
@Service class ProductService { private final ProductRepository repo; ProductService(ProductRepository repo){this.repo=repo;}
 private void op(Principal p){if(p==null||!p.canManageBusiness())throw new IllegalArgumentException("FORBIDDEN");}
 @Transactional Product create(Principal p,Product x){op(p);if(x.sku()==null||x.sku().isBlank()||x.priceMinor()<0||x.stock()<0)throw new IllegalArgumentException("VALIDATION_ERROR");return repo.create(new Product(0,x.sku(),x.name(),x.description(),x.priceMinor(),x.stock(),"DRAFT",p.userId()));}
 @Transactional Product update(Principal p,long id,Product x){op(p);if(repo.get(id)==null)throw new IllegalArgumentException("PRODUCT_NOT_FOUND");return repo.update(id,new Product(id,null,x.name(),x.description(),x.priceMinor(),x.stock(),null,p.userId()));}
 @Transactional Product changeStatus(Principal p,long id,String status){op(p);Product old=repo.get(id);if(old==null)throw new IllegalArgumentException("PRODUCT_NOT_FOUND");if(!status.equals("ON_SALE")&&!status.equals("OFF_SALE"))throw new IllegalArgumentException("VALIDATION_ERROR");return repo.status(id,status,p.userId());}
 Product get(long id){Product p=repo.get(id);if(p==null)throw new IllegalArgumentException("PRODUCT_NOT_FOUND");return p;}
 Product getPublic(long id){Product product=get(id);if(!"ON_SALE".equals(product.status()))throw new IllegalArgumentException("PRODUCT_NOT_ON_SALE");return product;}
 List<Product> list(boolean onSale){return repo.list(onSale);}
 long count(boolean onSale){return repo.count(onSale);}
 List<Product> listForOperator(Principal principal){op(principal);return repo.list(false);}
 Product getForOperator(Principal principal,long id){op(principal);return get(id);}
 InventoryView inventory(Principal principal,long id){op(principal);Product product=get(id);return new InventoryView(product.id(),product.stock(),0);}
 record InventoryView(long productId,int availableStock,int reservedStock) {}
}
