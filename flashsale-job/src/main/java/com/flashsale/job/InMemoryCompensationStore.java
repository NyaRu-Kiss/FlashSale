package com.flashsale.job;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryCompensationStore implements CompensationStore {
    private final Map<String, CompensationRecord> records = new ConcurrentHashMap<>();
    public Optional<CompensationRecord> find(String key) { return Optional.ofNullable(records.get(key)); }
    public void save(CompensationRecord record) { records.put(record.key(), record); }
}
