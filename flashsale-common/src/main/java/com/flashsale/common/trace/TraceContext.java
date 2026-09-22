package com.flashsale.common.trace;

public final class TraceContext {
    public static final String HEADER = "X-Trace-Id";
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TraceContext() {}

    public static String getOrCreate() {
        String traceId = CURRENT.get();
        if (traceId == null || traceId.isBlank()) {
            traceId = java.util.UUID.randomUUID().toString();
            CURRENT.set(traceId);
        }
        return traceId;
    }

    public static void set(String traceId) { CURRENT.set(traceId); }
    public static String get() { return CURRENT.get(); }
    public static void clear() { CURRENT.remove(); }
}
