package com.flashsale.order;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderControllerContractTest {
    @Test void exposesDocumentedOrderEntrypoints() {
        Class<?> type = OrderController.class;
        assertTrue(type.isAnnotationPresent(RequestMapping.class));
        assertTrue(Arrays.stream(type.getDeclaredMethods()).anyMatch(m -> has(m, PostMapping.class, "/preview")));
        assertTrue(Arrays.stream(type.getDeclaredMethods()).anyMatch(m -> has(m, PostMapping.class, "")));
        assertTrue(Arrays.stream(type.getDeclaredMethods()).anyMatch(m -> has(m, GetMapping.class, "")));
        assertTrue(Arrays.stream(type.getDeclaredMethods()).anyMatch(m -> has(m, GetMapping.class, "/{orderNumber}")));
        assertTrue(Arrays.stream(type.getDeclaredMethods()).anyMatch(m -> has(m, PostMapping.class, "/{orderNumber}/cancel")));
    }

    private static boolean has(Method method, Class<? extends java.lang.annotation.Annotation> annotation, String path) {
        var value = method.getAnnotation(annotation);
        if (value instanceof PostMapping p) return path.isEmpty() && p.path().length == 0 && p.value().length == 0 || Arrays.asList(p.path()).contains(path) || Arrays.asList(p.value()).contains(path);
        if (value instanceof GetMapping g) return path.isEmpty() && g.path().length == 0 && g.value().length == 0 || Arrays.asList(g.path()).contains(path) || Arrays.asList(g.value()).contains(path);
        return false;
    }
}
