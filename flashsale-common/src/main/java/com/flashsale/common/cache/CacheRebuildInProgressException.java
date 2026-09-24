package com.flashsale.common.cache;

public final class CacheRebuildInProgressException extends RuntimeException {
    public CacheRebuildInProgressException(String cacheKey) {
        super("CACHE_REBUILD_IN_PROGRESS: " + cacheKey);
    }
}
