package com.flashsale.job;

import java.util.Optional;

public interface CompensationStore {
    Optional<CompensationRecord> find(String key);
    void save(CompensationRecord record);
}
