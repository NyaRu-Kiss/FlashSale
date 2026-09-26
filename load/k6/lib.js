import http from 'k6/http';
import { check } from 'k6';

export const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
export const token = __ENV.LOAD_TOKEN || '';

export function authHeaders(extra = {}) {
  return { Authorization: `Bearer ${token}`, ...extra };
}

export function request(method, path, body, headers = {}) {
  const params = { headers: { 'Content-Type': 'application/json', ...headers }, tags: { endpoint: path } };
  const response = method === 'GET'
    ? http.get(`${baseUrl}${path}`, params)
    : http.request(method, `${baseUrl}${path}`, body === undefined ? null : JSON.stringify(body), params);
  check(response, { 'HTTP status is successful or business rejection': r => r.status >= 200 && r.status < 500 });
  return response;
}

export function jsonEnv(name, fallback) {
  const value = __ENV[name];
  return value ? JSON.parse(value) : fallback;
}

export function csvEnv(name) {
  return (__ENV[name] || '').split(',').map(v => v.trim()).filter(Boolean);
}
