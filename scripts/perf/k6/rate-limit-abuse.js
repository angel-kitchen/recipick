// 요청 수 제한 Before/After 부하 테스트. 실행 방법은 docs/performance/product/rate-limit.md 참고.
// 악성 IP 1개가 캐시를 피하는 검색을 쉬지 않고 보내는 동안, 서로 다른 IP의 일반 회원이 받는 응답 시간을 잰다.
//
// 전제
// - scripts/perf/seed-products.sql, scripts/perf/seed-members.sql 로 데이터를 만들어 둔다.
// - 앱은 IP를 request.getRemoteAddr() 로만 구분한다. 한 PC에서는 접속 주소를 바꿔도 출발 IP가 127.0.0.1로 같아서
//   (Docker Desktop도 컨테이너 요청을 모두 같은 IP로 보이게 한다) 서로 다른 IP를 만들 수 없다.
// - 그래서 앱을 SERVER_FORWARD_HEADERS_STRATEGY=native 로 띄운다(운영 설정과 같다).
//   신뢰하는 프록시(127.0.0.1 포함)가 보낸 X-Forwarded-For 를 앱이 실제 클라이언트 IP로 읽는다.
//   일반 회원은 회원마다 다른 X-Forwarded-For 를 보내고, 악성은 헤더 없이 127.0.0.1 로 보낸다.
import http from 'k6/http';
import { check, sleep } from 'k6';

const PORT = __ENV.PORT || '8080';
// 로컬 성능 테스트용 값이다(seed-members.sql 의 해시 원문). 실서비스 비밀번호를 쓰지 않는다.
const PERF_PASSWORD = 'Perf!2345pass';
const NORMAL_USERS = 20;
const ABUSER_VUS = Number(__ENV.ABUSER_VUS || 30);
const DURATION = __ENV.DURATION || '90s';

const BASE_URL = `http://127.0.0.1:${PORT}`;
// 회원마다 서로 다른 클라이언트 IP처럼 보이게 하는 헤더
const normalHeaders = (index) => ({ 'X-Forwarded-For': `10.0.0.${11 + index}` });

const KEYWORDS = ['대파', '유기농', '양파', '두부', '소고기'];
// 캐시(30초)에 걸리지 않도록 3글자 조합으로 매번 다른 키워드를 만든다. LIKE '%…%' 라서 DB를 끝까지 훑는다.
const SYLLABLES = ['가', '나', '다', '라', '마', '바', '사', '아', '자', '차', '카', '타', '파', '하'];
const randomSyllable = () => SYLLABLES[Math.floor(Math.random() * SYLLABLES.length)];

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    normal: {
      executor: 'constant-vus',
      vus: NORMAL_USERS,
      duration: DURATION,
      exec: 'normalUser',
      tags: { who: 'normal' },
    },
    abuser: {
      executor: 'constant-vus',
      vus: ABUSER_VUS,
      duration: DURATION,
      exec: 'abuser',
      tags: { who: 'abuser' },
    },
  },
  thresholds: {
    // 합격선이 아니라 요약에 항목을 따로 보이게 하기 위한 값이다. 실패해도 측정 결과는 그대로 남는다.
    'http_req_duration{who:normal}': ['p(95)<1000'],
    'http_req_duration{who:abuser}': ['p(95)<1000'],
    'http_reqs{who:normal,status:429}': ['count>=0'],
    'http_reqs{who:abuser,status:429}': ['count>=0'],
    'http_reqs{who:abuser}': ['count>=0'],
    'http_reqs{who:normal}': ['count>=0'],
  },
};

function login(extraHeaders, index) {
  const email = `perf-user-${String(index).padStart(3, '0')}@recipick.test`;
  const res = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email, password: PERF_PASSWORD }),
    { headers: { 'Content-Type': 'application/json', ...extraHeaders } },
  );
  if (res.status !== 200) {
    throw new Error(`로그인 실패 ${email}: ${res.status} (seed-members.sql 실행 여부, 요청 수 제한 카운터 확인)`);
  }
  return res.json('data.accessToken');
}

export function setup() {
  // 로그인은 회원마다 자기 IP에서 한 번씩만 한다(auth 정책은 IP당 분당 10회).
  const normalTokens = [];
  for (let i = 0; i < NORMAL_USERS; i++) {
    normalTokens.push(login(normalHeaders(i), i + 1));
  }
  const abuserToken = login({}, NORMAL_USERS + 1);
  return { normalTokens, abuserToken };
}

export function normalUser(data) {
  // 같은 번호의 VU는 항상 같은 회원, 같은 IP로 요청한다.
  const index = __VU % NORMAL_USERS;
  const params = {
    headers: { Authorization: `Bearer ${data.normalTokens[index]}`, ...normalHeaders(index) },
  };
  const keyword = encodeURIComponent(KEYWORDS[Math.floor(Math.random() * KEYWORDS.length)]);

  const pick = Math.random();
  let res;
  if (pick < 0.5) {
    res = http.get(`${BASE_URL}/api/products?keyword=${keyword}&size=20`, params);
  } else if (pick < 0.8) {
    const categoryId = 1 + Math.floor(Math.random() * 20);
    res = http.get(
      `${BASE_URL}/api/products?categoryId=${categoryId}&minPrice=1000&maxPrice=20000&sort=price,asc&size=20`,
      params,
    );
  } else {
    res = http.get(`${BASE_URL}/api/categories`, { headers: normalHeaders(index) });
  }
  check(res, { '일반 회원 상태 200': (r) => r.status === 200 });
  sleep(1);
}

export function abuser(data) {
  const params = { headers: { Authorization: `Bearer ${data.abuserToken}` } };
  const keyword = encodeURIComponent(randomSyllable() + randomSyllable() + randomSyllable());
  http.get(`${BASE_URL}/api/products?keyword=${keyword}&size=20`, params);
}
