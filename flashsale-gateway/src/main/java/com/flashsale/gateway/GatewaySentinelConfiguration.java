package com.flashsale.gateway;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.cloud.context.config.annotation.RefreshScope;

/** Local Sentinel rules for R01; Nacos-backed rule distribution belongs to R02. */
@Configuration(proxyBeanMethods = false)
@RefreshScope
class GatewaySentinelConfiguration {
    GatewaySentinelConfiguration(
            @Value("${flashsale.gateway.rate-limit.default-qps:100}") double defaultQps,
            @Value("${flashsale.gateway.rate-limit.ip-qps:100}") double ipQps,
            @Value("${flashsale.gateway.rate-limit.activity-hotspot-qps:20}") double activityHotspotQps,
            @Value("${flashsale.gateway.rate-limit.coupon-hotspot-qps:20}") double couponHotspotQps) {
        FlowRuleManager.loadRules(List.of(
                flow("gateway:all", defaultQps),
                flow("gateway:path:/api/v1/auth/register", defaultQps),
                flow("gateway:path:/api/v1/auth/login", defaultQps),
                flow("gateway:path:/api/v1/orders", defaultQps),
                flow("gateway:path:/api/v1/coupons/{templateId}/claims", defaultQps)));
        ParamFlowRuleManager.loadRules(List.of(
                param("gateway:ip", ipQps, 0),
                param("gateway:hotspot:activity", activityHotspotQps, 0),
                param("gateway:hotspot:coupon", couponHotspotQps, 0)));
    }

    private static FlowRule flow(String resource, double count) {
        FlowRule rule = new FlowRule(resource);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(count);
        return rule;
    }

    private static ParamFlowRule param(String resource, double count, int index) {
        ParamFlowRule rule = new ParamFlowRule(resource);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(count);
        rule.setParamIdx(index);
        return rule;
    }
}
