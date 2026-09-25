package com.flashsale.inventory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Parses the fixed inventory dataset used by the acceptance profile. */
public final class InventoryAcceptanceSeedParser {
    private InventoryAcceptanceSeedParser() {
    }

    public static List<Seed> parse(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<Seed> seeds = new ArrayList<>();
        Set<Long> resourceIds = new HashSet<>();
        for (String entry : value.split(",", -1)) {
            String[] pair = entry.trim().split("=", -1);
            if (pair.length != 2 || pair[0].isBlank() || pair[1].isBlank()) {
                throw invalid(value);
            }
            long resourceId;
            int quantity;
            try {
                resourceId = Long.parseLong(pair[0].trim());
                quantity = Integer.parseInt(pair[1].trim());
            } catch (NumberFormatException exception) {
                throw invalid(value);
            }
            if (resourceId <= 0 || quantity < 0 || !resourceIds.add(resourceId)) {
                throw invalid(value);
            }
            seeds.add(new Seed(resourceId, quantity));
        }
        return List.copyOf(seeds);
    }

    private static IllegalArgumentException invalid(String value) {
        return new IllegalArgumentException("Invalid INVENTORY_ACCEPTANCE_SEED: " + value);
    }

    public record Seed(long resourceId, int quantity) {
    }
}
