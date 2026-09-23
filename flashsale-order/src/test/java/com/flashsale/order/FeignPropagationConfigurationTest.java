package com.flashsale.order;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.flashsale.common.trace.TraceContext;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FeignPropagationConfigurationTest {
    @AfterEach
    void clearTrace() { TraceContext.clear(); }

    @Test
    void propagatesTraceToFutureFeignCalls() {
        TraceContext.set("trace-r02");
        RequestTemplate request = new RequestTemplate();

        new FeignPropagationConfiguration().requestContextPropagation().apply(request);

        assertEquals("trace-r02", request.headers().get(TraceContext.HEADER).iterator().next());
    }
}
