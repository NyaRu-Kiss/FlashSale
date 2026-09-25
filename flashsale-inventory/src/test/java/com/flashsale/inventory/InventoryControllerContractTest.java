package com.flashsale.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryControllerContractTest {
    @Test void exposesReservationLifecycleEntrypoints() {
        Class<?> type = InventoryController.class;
        assertTrue(type.isAnnotationPresent(RequestMapping.class));
        assertTrue(Arrays.stream(type.getDeclaredMethods()).anyMatch(m -> mapped(m, "/reservations")));
        assertTrue(Arrays.stream(type.getDeclaredMethods()).anyMatch(m -> mapped(m, "/reservations/{key}/confirm")));
        assertTrue(Arrays.stream(type.getDeclaredMethods()).anyMatch(m -> mapped(m, "/reservations/{key}/release")));
    }
    private static boolean mapped(java.lang.reflect.Method m, String path) {
        PostMapping p = m.getAnnotation(PostMapping.class);
        return p != null && (Arrays.asList(p.path()).contains(path) || Arrays.asList(p.value()).contains(path));
    }
}
