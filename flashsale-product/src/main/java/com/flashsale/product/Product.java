package com.flashsale.product;
public record Product(long id,String sku,String name,String description,long priceMinor,int stock,String status,long updatedBy) {}
