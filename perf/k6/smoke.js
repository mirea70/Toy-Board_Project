import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  vus: 2,
  duration: '20s',
  thresholds: { http_req_failed: ['rate<0.01'] },
};

export default function () {
  const res = http.get(`${BASE_URL}/v1/articles?boardId=1&page=1&pageSize=10`);
  check(res, { '200': (r) => r.status === 200 });
  sleep(1);
}
