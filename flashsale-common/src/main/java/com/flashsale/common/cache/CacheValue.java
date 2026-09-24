package com.flashsale.common.cache;

/** A cache value may represent a normal value or a controlled negative result. */
public record CacheValue<T>(T value, String errorCode) {
    public static <T> CacheValue<T> value(T value) { return new CacheValue<>(value, null); }
    public static <T> CacheValue<T> error(String errorCode) { return new CacheValue<>(null, errorCode); }
    public boolean isError() { return errorCode != null; }
}
