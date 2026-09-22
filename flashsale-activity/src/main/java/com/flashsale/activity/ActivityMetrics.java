package com.flashsale.activity;
public record ActivityMetrics(ActivityStatus status,int availableStock,long eventCount,long checkpoint,long pauseBarrier,boolean recoverable) {}
