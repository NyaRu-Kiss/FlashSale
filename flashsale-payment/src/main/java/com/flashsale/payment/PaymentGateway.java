package com.flashsale.payment;

public interface PaymentGateway {
    PaymentResult initiate(PaymentRequest request);
    PaymentCallback verifyAndParse(CallbackRequest request);
    record PaymentRequest(String orderNumber,long amountMinor,String currency,String paymentStatus) {}
    record PaymentResult(boolean success,String transactionId,String failureCode) {}
    record CallbackRequest(String transactionId,long amountMinor,String currency,String paymentStatus,String eventId) {}
    record PaymentCallback(String transactionId,long amountMinor,String currency,boolean success,String eventId) {}
}
