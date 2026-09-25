package com.flashsale.product;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository class ProductRepository {
 private final JdbcTemplate jdbc; ProductRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 Product create(Product p){return jdbc.queryForObject("insert into product(sku,name,description,list_price_minor,available_stock,status,created_by,updated_by) values (?,?,?,?,?,'DRAFT',?,?) returning id,sku,name,description,list_price_minor,available_stock,status,updated_by",(r,n)->map(r),p.sku(),p.name(),p.description(),p.priceMinor(),p.stock(),p.updatedBy(),p.updatedBy());}
 Product update(long id,Product p){try{return jdbc.queryForObject("update product set name=?,description=?,list_price_minor=?,available_stock=?,updated_by=?,version=version+1 where id=? returning id,sku,name,description,list_price_minor,available_stock,status,updated_by",(r,n)->map(r),p.name(),p.description(),p.priceMinor(),p.stock(),p.updatedBy(),id);}catch(org.springframework.dao.EmptyResultDataAccessException e){return null;}}
 Product status(long id,String status,long actor){try{return jdbc.queryForObject("update product set status=?::product_status,updated_by=?,version=version+1 where id=? returning id,sku,name,description,list_price_minor,available_stock,status,updated_by",(r,n)->map(r),status,actor,id);}catch(org.springframework.dao.EmptyResultDataAccessException e){return null;}}
 Product get(long id){return jdbc.query("select id,sku,name,description,list_price_minor,available_stock,status,updated_by from product where id=?",r->r.next()?map(r):null,id);}
 List<Product> list(boolean onSale){return jdbc.query("select id,sku,name,description,list_price_minor,available_stock,status,updated_by from product "+(onSale?"where status='ON_SALE' ":"")+"order by id",(r,n)->map(r));}
 long count(boolean onSale){Long count=jdbc.queryForObject("select count(*) from product "+(onSale?"where status='ON_SALE'":""),Long.class);return count==null?0:count;}
 private Product map(java.sql.ResultSet r)throws java.sql.SQLException{return new Product(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getLong(5),r.getInt(6),r.getString(7),r.getLong(8));}
}
