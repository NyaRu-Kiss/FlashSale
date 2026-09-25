package com.flashsale.inventory;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Loads a deterministic inventory dataset only for acceptance runs. */
@Component
@Profile("acceptance")
public final class InventoryAcceptanceSeedInitializer {
    private static final Logger log = LoggerFactory.getLogger(InventoryAcceptanceSeedInitializer.class);

    private final InventoryLedger ledger;
    private final String configuration;

    public InventoryAcceptanceSeedInitializer(InventoryLedger ledger,
                                              @Value("${INVENTORY_ACCEPTANCE_SEED:}") String configuration) {
        this.ledger = ledger;
        this.configuration = configuration;
    }

    @PostConstruct
    void load() {
        var seeds = InventoryAcceptanceSeedParser.parse(configuration);
        long total = 0;
        for (var seed : seeds) {
            ledger.initialize(seed.resourceId(), seed.quantity());
            total += seed.quantity();
        }
        log.info("Loaded acceptance inventory seed: resources={}, total_quantity={}", seeds.size(), total);
    }
}
