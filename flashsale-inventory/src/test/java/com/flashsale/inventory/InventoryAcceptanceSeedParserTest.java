package com.flashsale.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InventoryAcceptanceSeedParserTest {
    @Test
    void parsesSingleAndMultipleResources() {
        assertEquals(List.of(new InventoryAcceptanceSeedParser.Seed(9001, 10)),
                InventoryAcceptanceSeedParser.parse("9001=10"));
        assertEquals(List.of(new InventoryAcceptanceSeedParser.Seed(9001, 10),
                        new InventoryAcceptanceSeedParser.Seed(9002, 20)),
                InventoryAcceptanceSeedParser.parse("9001=10, 9002=20"));
    }

    @Test
    void blankConfigurationDoesNotLoadSeeds() {
        assertEquals(List.of(), InventoryAcceptanceSeedParser.parse("  "));
    }

    @Test
    void rejectsDuplicateResourceIds() {
        assertThrows(IllegalArgumentException.class,
                () -> InventoryAcceptanceSeedParser.parse("9001=10,9001=20"));
    }

    @Test
    void rejectsInvalidResourceId() {
        assertThrows(IllegalArgumentException.class,
                () -> InventoryAcceptanceSeedParser.parse("0=10"));
        assertThrows(IllegalArgumentException.class,
                () -> InventoryAcceptanceSeedParser.parse("abc=10"));
    }

    @Test
    void rejectsNegativeQuantity() {
        assertThrows(IllegalArgumentException.class,
                () -> InventoryAcceptanceSeedParser.parse("9001=-1"));
    }

    @Test
    void rejectsMissingSeparatorOrExtraSeparator() {
        assertThrows(IllegalArgumentException.class,
                () -> InventoryAcceptanceSeedParser.parse("9001"));
        assertThrows(IllegalArgumentException.class,
                () -> InventoryAcceptanceSeedParser.parse("9001=10=20"));
    }

    @Test
    void initializerIsAcceptanceOnly() {
        Profile profile = InventoryAcceptanceSeedInitializer.class.getAnnotation(Profile.class);
        assertEquals(List.of("acceptance"), List.of(profile.value()));
    }
}
