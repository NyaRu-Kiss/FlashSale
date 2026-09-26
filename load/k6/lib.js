import http from 'k6/http';
import { Counter } from 'k6/metrics';

export const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
export const transportFailures = new Counter('transport_failures');
export const businessAccepted = new Counter('business_accepted');
export const businessRejected = new Counter('business_rejected');
export const unexpectedResponses = new Counter('unexpected_responses');

export function request(method, path, body, headers = {}) {
  const params = {
    headers: { 'Content-Type': 'application/json', ...headers },
    tags: { endpoint: path.replace(/\/\d+/g, '/{id}') },
    responseCallback: http.expectedStatuses({ min: 200, max: 499 }),
  };
  const response = method === 'GET'
    ? http.get(`${baseUrl}${path}`, params)
    : http.request(method, `${baseUrl}${path}`, body === undefined ? null : JSON.stringify(body), params);
  if (response.status === 0 || response.status >= 500) {
    transportFailures.add(1);
  } else if (response.status < 300 && response.json('code') === 'SUCCESS') {
    businessAccepted.add(1);
  } else if (response.status >= 400 && response.status < 500) {
    businessRejected.add(1);
  } else {
    unexpectedResponses.add(1);
  }
  return response;
}

export function jsonEnv(name, fallback) {
  const value = __ENV[name];
  return value ? JSON.parse(value) : fallback;
}

export function csvEnv(name) {
  return (__ENV[name] || '').split(',').map(v => v.trim()).filter(Boolean);
}
