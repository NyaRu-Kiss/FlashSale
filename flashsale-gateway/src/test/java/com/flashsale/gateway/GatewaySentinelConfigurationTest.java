package com.flashsale.gateway;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import org.junit.jupiter.api.Test;

class GatewaySentinelConfigurationTest {
    @Test
    void installsGlobalInterfaceAndHotspotRules() {
        new GatewaySentinelConfiguration(100, 100, 20, 20);

        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> "gateway:all".equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> "gateway:path:/api/v1/coupons/{templateId}/claims".equals(rule.getResource())));
        assertTrue(ParamFlowRuleManager.getRules().stream()
                .anyMatch(rule -> "gateway:ip".equals(rule.getResource())));
        assertTrue(ParamFlowRuleManager.getRules().stream()
                .anyMatch(rule -> "gateway:hotspot:activity".equals(rule.getResource())));
        assertTrue(ParamFlowRuleManager.getRules().stream()
                .anyMatch(rule -> "gateway:hotspot:coupon".equals(rule.getResource())));
    }
}
