import { check } from 'k6';
import { authHeaders, csvEnv, jsonEnv, request, token } from './lib.js';

const scenario = __ENV.SCENARIO || 'product_read';
const productId = __ENV.PRODUCT_ID || '1';
const activityId = __ENV.ACTIVITY_ID || '1';
const couponTemplateId = __ENV.COUPON_TEMPLATE_ID || '1';
const orderNumbers = csvEnv('ORDER_NUMBERS');

export const options = {
  scenarios: {
    load: scenarioOptions(scenario),
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    checks: ['rate>0.95'],
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
  if (name === 'direct_purchase' || name === 'payment_cancel') return {
    executor: 'ramping-vus', startVUs: 0, stages: [{ duration: '15s', target: 10 }, { duration: '30s', target: 50 }, { duration: '30s', target: 100 }, { duration: '15s', target: 0 }],
  };
  return { executor: 'constant-vus', vus: Number(__ENV.VUS || 100), duration: __ENV.DURATION || '30s' };
}

export default function () {
  if (scenario === 'product_read') return productRead();
  if (!token) throw new Error('LOAD_TOKEN is required for this scenario');
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

function activityBurst() {
  const body = jsonEnv('ACTIVITY_ORDER_BODY', { kind: 'ACTIVITY', activity_id: Number(activityId), user_coupon_id: null, items: [{ product_id: Number(productId), quantity: 1 }] });
  const key = `ORDER_SUBMIT_burst_${__VU}_${__ITER}`;
  const response = request('POST', '/api/v1/orders', body, authHeaders({ 'Idempotency-Key': key }));
  check(response, { 'burst response has trace': r => Boolean(r.json('trace_id')) });
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
