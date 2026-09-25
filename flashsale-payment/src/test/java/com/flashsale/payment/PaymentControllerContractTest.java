package com.flashsale.payment;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentControllerContractTest {
    @Test void exposesPaymentAndCallbackEntrypoints() {
        Class<?> type = PaymentController.class;
        assertTrue(type.isAnnotationPresent(RequestMapping.class));
        assertTrue(Arrays.stream(type.getDeclaredMethods()).anyMatch(m -> mapped(m, "/orders/{orderNumber}/payments")));
        assertTrue(Arrays.stream(type.getDeclaredMethods()).anyMatch(m -> mapped(m, "/payments/callback")));
    }
    private static boolean mapped(java.lang.reflect.Method m, String path) {
        PostMapping p = m.getAnnotation(PostMapping.class);
        return p != null && (Arrays.asList(p.path()).contains(path) || Arrays.asList(p.value()).contains(path));
    }
}
