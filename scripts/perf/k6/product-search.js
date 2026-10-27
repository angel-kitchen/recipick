// 상품 검색 부하 테스트. 실행 방법은 docs/performance/load-test.md 참고.
// 전제: scripts/perf/seed-products.sql, scripts/perf/seed-members.sql 로 데이터를 만들어 둔다.
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8080';
// 로컬 성능 테스트용 값이다(seed-members.sql 의 해시 원문). 실서비스 비밀번호를 쓰지 않는다.
const PERF_PASSWORD = 'Perf!2345pass';
const MEMBER_COUNT = 100;

// 로그인(BCrypt)은 무거워서 요청마다 하지 않고 시작할 때 한 번만 하고 토큰을 재사용한다(토큰 유효 30분).
const KEYWORDS = ['대파', '유기농', '양파', '두부', '소고기'];

export const options = {
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    search: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 10 },
        { duration: '1m', target: 10 },
        { duration: '30s', target: 50 },
        { duration: '1m', target: 50 },
        { duration: '30s', target: 100 },
        { duration: '1m', target: 100 },
        { duration: '20s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    // 합격선이 아니라 Before/After 비교용 기준선이다. 실패해도 측정 결과는 그대로 남는다.
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
    // API별 결과를 요약에 따로 보이게 하기 위한 항목이다.
    'http_req_duration{name:search_keyword}': ['p(95)<1000'],
    'http_req_duration{name:search_filter}': ['p(95)<1000'],
    'http_req_duration{name:categories}': ['p(95)<1000'],
  },
};

export function setup() {
  const tokens = [];
  for (let i = 1; i <= MEMBER_COUNT; i++) {
    const email = `perf-user-${String(i).padStart(3, '0')}@recipick.test`;
    const res = http.post(
      `${BASE_URL}/api/auth/login`,
      JSON.stringify({ email, password: PERF_PASSWORD }),
      { headers: { 'Content-Type': 'application/json' } },
    );
    if (res.status !== 200) {
      throw new Error(`로그인 실패 ${email}: ${res.status} (seed-members.sql 실행 여부 확인)`);
    }
    tokens.push(res.json('data.accessToken'));
  }
  return { tokens };
}

export default function (data) {
  // VU마다 서로 다른 회원 토큰을 쓴다.
  const token = data.tokens[(__VU - 1) % data.tokens.length];
  const params = { headers: { Authorization: `Bearer ${token}` } };
  const keyword = encodeURIComponent(KEYWORDS[Math.floor(Math.random() * KEYWORDS.length)]);

  const pick = Math.random();
  let res;
  if (pick < 0.5) {
    // 키워드 검색(LIKE '%키워드%')
    res = http.get(`${BASE_URL}/api/products?keyword=${keyword}&size=20`, { ...params, tags: { name: 'search_keyword' } });
  } else if (pick < 0.8) {
    // 카테고리 + 가격 범위 필터, 가격 낮은 순
    const categoryId = 1 + Math.floor(Math.random() * 20);
    res = http.get(
      `${BASE_URL}/api/products?categoryId=${categoryId}&minPrice=1000&maxPrice=20000&sort=price,asc&size=20`,
      { ...params, tags: { name: 'search_filter' } },
    );
  } else {
    // 비교용 가벼운 공개 API
    res = http.get(`${BASE_URL}/api/categories`, { tags: { name: 'categories' } });
  }
  check(res, { '상태 200': (r) => r.status === 200 });
  sleep(1);
}
