package com.flashsale.payment;
import java.util.UUID;
public final class SimulatedPaymentGateway implements PaymentGateway {
 public PaymentResult initiate(PaymentRequest r){boolean ok="SUCCESS".equalsIgnoreCase(r.paymentStatus());return new PaymentResult(ok,ok?UUID.randomUUID().toString():null,ok?null:"PAYMENT_FAILED");}
 public PaymentCallback verifyAndParse(CallbackRequest r){if(r==null||r.transactionId()==null||r.eventId()==null)throw new IllegalArgumentException("INVALID_CALLBACK");return new PaymentCallback(r.transactionId(),r.amountMinor(),r.currency(),"SUCCESS".equalsIgnoreCase(r.paymentStatus()),r.eventId());}
}
