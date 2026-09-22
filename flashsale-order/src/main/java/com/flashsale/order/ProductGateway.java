package com.flashsale.order;

public interface ProductGateway {
    ProductSnapshot getProduct(long productId);

    record ProductSnapshot(long productId, String sku, String name, long listPriceMinor,
                           long salePriceMinor, boolean onSale) {}
}
