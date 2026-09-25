package com.flashsale.job;

@FunctionalInterface
public interface CompensationAlert {
    void failed(CompensationRecord record);
}
