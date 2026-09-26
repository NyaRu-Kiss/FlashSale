import { check } from 'k6';
import exec from 'k6/execution';
import { Gauge } from 'k6/metrics';
import { csvEnv, jsonEnv, request } from './lib.js';

const scenario = __ENV.SCENARIO || 'product_read';
const token = __ENV.LOAD_TOKEN || '';
const tokens = csvEnv('LOAD_TOKENS');
const productId = __ENV.PRODUCT_ID || '1';
const activityId = __ENV.ACTIVITY_ID || '1';
const couponTemplateId = __ENV.COUPON_TEMPLATE_ID || '1';
const orderNumbers = csvEnv('ORDER_NUMBERS');
const burstTotal = Number(__ENV.BURST_TOTAL_REQUESTS || 10000);
const burstUniqueUsers = Number(__ENV.BURST_UNIQUE_USERS || 9000);
const burstStartedAt = new Gauge('burst_started_at_ms');
const burstFinishedAt = new Gauge('burst_finished_at_ms');

function authHeaders(extra = {}) {
  const selected = tokens.length ? tokens[(__VU - 1) % tokens.length] : token;
  return { Authorization: `Bearer ${selected}`, ...extra };
}

export const options = {
  scenarios: {
    load: scenarioOptions(scenario),
  },
  thresholds: {
    transport_failures: ['count<1'],
    unexpected_responses: ['count<1'],
  },
  discardResponseBodies: false,
};

function scenarioOptions(name) {
  if (name === 'activity_burst') return {
    executor: 'constant-arrival-rate', rate: Number(__ENV.BURST_RATE || 10000), timeUnit: '1s', duration: __ENV.BURST_DURATION || '1s',
    preAllocatedVUs: Number(__ENV.PREALLOCATED_VUS || 1000), maxVUs: Number(__ENV.MAX_VUS || 10000),
  };
  if (name === 'activity_limit') return {
    executor: 'constant-arrival-rate', rate: Number(__ENV.LIMIT_RATE || 100), timeUnit: '1s', duration: __ENV.DURATION || '30s',
    preAllocatedVUs: Number(__ENV.PREALLOCATED_VUS || 100), maxVUs: Number(__ENV.MAX_VUS || 1000),
  };
  if (name === 'product_read') return {
    executor: 'constant-vus', vus: Number(__ENV.VUS || 5000), duration: __ENV.DURATION || '30s',
  };
  if (name === 'product_read_cold') return { executor: 'shared-iterations', iterations: 1, maxDuration: __ENV.MAX_DURATION || '30s' };
  if (name === 'product_read_warm') return {
    executor: 'constant-vus', vus: Number(__ENV.VUS || 5000), duration: __ENV.DURATION || '30s',
  };
  if (name === 'direct_purchase' || name === 'payment_cancel') return {
    executor: 'ramping-vus', startVUs: 0, stages: [{ duration: '15s', target: 10 }, { duration: '30s', target: 50 }, { duration: '30s', target: 100 }, { duration: '15s', target: 0 }],
  };
  return { executor: 'constant-vus', vus: Number(__ENV.VUS || 100), duration: __ENV.DURATION || '30s' };
}

export default function () {
  if (scenario === 'product_read' || scenario === 'product_read_warm') return productRead();
  if (scenario === 'product_read_cold') return productReadCold();
  if (!token && !tokens.length) throw new Error('LOAD_TOKEN or LOAD_TOKENS is required for this scenario');
  if (scenario === 'activity_burst') return activityBurst();
  if (scenario === 'activity_limit') return activityBurst();
  if (scenario === 'direct_purchase') return directPurchase();
  if (scenario === 'coupon_claim') return couponClaim();
  if (scenario === 'duplicate_order') return duplicateOrder();
  if (scenario === 'payment_cancel') return paymentCancel();
  throw new Error(`Unknown SCENARIO: ${scenario}`);
}

function productRead() {
  request('GET', `/api/v1/products/${productId}`);
}

function productReadCold() {
  const response = request('GET', `/api/v1/products/${productId}`);
  check(response, { 'cold read returns a complete product': r => {
    if (r.status < 200 || r.status >= 300) return false;
    const body = r.json('data') || r.json();
    return Boolean(body && body.name && body.description !== undefined
      && body.list_price_minor !== undefined && body.available_stock > 0);
  }});
}

function activityBurst() {
  const iteration = exec.scenario.iterationInTest;
  if (scenario === 'activity_burst' && iteration === 0) burstStartedAt.add(Date.now());
  const body = jsonEnv('ACTIVITY_ORDER_BODY', { kind: 'ACTIVITY', activity_id: Number(activityId), user_coupon_id: null, items: [{ product_id: Number(productId), quantity: 1 }] });
  const tokenIndex = scenario === 'activity_burst'
    ? (iteration < burstUniqueUsers ? iteration : iteration - burstUniqueUsers) : 0;
  const selected = tokens.length ? tokens[tokenIndex % tokens.length] : token;
  const key = scenario === 'activity_burst'
    ? `ORDER_SUBMIT_burst_${iteration}` : `ORDER_SUBMIT_limit_${__VU}_${__ITER}`;
  const response = request('POST', '/api/v1/orders', body, {
    Authorization: `Bearer ${selected}`, 'Idempotency-Key': key,
    'X-H03-Iteration': String(iteration),
  });
  check(response, { 'burst response has trace': r => Boolean(r.json('trace_id') || r.json('traceId')) });
  if (scenario === 'activity_burst' && iteration === burstTotal - 1) burstFinishedAt.add(Date.now());
}

function directPurchase() {
  const body = jsonEnv('DIRECT_ORDER_BODY', { kind: 'DIRECT', activity_id: null, user_coupon_id: null, items: [{ product_id: Number(productId), quantity: 1 }] });
  request('POST', '/api/v1/orders', body, authHeaders({ 'Idempotency-Key': `ORDER_SUBMIT_direct_${__VU}_${__ITER}` }));
}

function couponClaim() {
  request('POST', `/api/v1/coupons/${couponTemplateId}/claims`, {}, authHeaders({ 'Idempotency-Key': `COUPON_CLAIM_load_${__VU}_${__ITER}` }));
}

function duplicateOrder() {
  const body = jsonEnv('DIRECT_ORDER_BODY', { kind: 'DIRECT', activity_id: null, user_coupon_id: null, items: [{ product_id: Number(productId), quantity: 1 }] });
  const key = __ENV.DUPLICATE_KEY || 'ORDER_SUBMIT_duplicate_shared';
  request('POST', '/api/v1/orders', body, authHeaders({ 'Idempotency-Key': key }));
}

function paymentCancel() {
  if (!orderNumbers.length) throw new Error('ORDER_NUMBERS is required for payment_cancel');
  const orderNumber = orderNumbers[(__VU - 1) % orderNumbers.length];
  if (__ITER % 2 === 0) {
    request('POST', `/api/v1/orders/${orderNumber}/payments`, { amount_minor: Number(__ENV.PAYMENT_AMOUNT_MINOR || 100), currency: __ENV.PAYMENT_CURRENCY || 'CNY', payment_status: 'SUCCESS' }, authHeaders({ 'Idempotency-Key': `PAYMENT_KEY_${__VU}_${__ITER}` }));
  } else {
    request('POST', `/api/v1/orders/${orderNumber}/cancel`, { reason: 'LOAD_CANCEL' }, authHeaders());
  }
}

export function handleSummary(data) {
  const started = data.metrics.burst_started_at_ms?.values?.value || null;
  const finished = data.metrics.burst_finished_at_ms?.values?.value || null;
  const result = {
    scenario, burst_total_requests: burstTotal, burst_unique_users: burstUniqueUsers,
    burst_repeated_requests: Math.max(0, burstTotal - burstUniqueUsers),
    burst_started_at_ms: started, burst_finished_at_ms: finished,
    burst_window_ms: started && finished ? finished - started : null, metrics: data.metrics,
  };
  return { stdout: JSON.stringify(result, null, 2), 'summary.json': JSON.stringify(result, null, 2) };
}
