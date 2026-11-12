// 레시피 목록·상세·자동완성 부하 테스트. 레시피 5만 건에서 목록 검색과 상세 조회의 지연·DB 풀 대기를 잰다.
// 전제: scripts/perf/seed-members.sql, scripts/perf/seed-recipes.sql 로 성능 전용 DB에 데이터를 만들어 둔다.
// 실행(호스트 k6, 레포 루트): k6 run -e BASE_URL=http://localhost:28080 scripts/perf/k6/recipe-search.js
//   1명·1회 점검은 -e SMOKE=1 을 붙인다.
//   RECIPE_FIRST_ID는 시드의 1번 레시피 ID다(기본 101 = 로컬 초기 레시피 100개 다음).
//   SELECT MIN(id) FROM recipe WHERE name LIKE 'PERF-%'; 로 확인한다.
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = (__ENV.BASE_URL || 'http://localhost:28080').replace(/\/$/, '');
const SMOKE = __ENV.SMOKE === '1';
// 로컬 성능 테스트용 값이다(seed-members.sql 의 해시 원문). 실서비스 비밀번호를 쓰지 않는다.
const PERF_PASSWORD = __ENV.PERF_PASSWORD || 'Perf!2345pass';
const MEMBER_COUNT = 100;
const RECIPE_FIRST_ID = Number(__ENV.RECIPE_FIRST_ID || 101);
const RECIPE_COUNT = 50000;
// 상세 조회의 30%를 인기 레시피 10개에 몰아, 같은 행의 조회수 UPDATE가 겹칠 때의 락 대기를 드러낸다
const HOT_RECIPE_COUNT = 10;
const HOT_DETAIL_RATIO = 0.3;

const LIST_KEYWORDS = ['김치', '된장', '닭', '두부', '파스타'];
// 자동완성은 키워드별로 30초 캐시되므로 대부분 캐시 적중이다. 캐시가 비는 순간의 전체 로드 비용은 서버 히스토그램 최대값으로 본다
const SUGGESTION_KEYWORDS = ['김', 'ㄱㅊ', '된장', '닭', '파스'];

const loadStages = [
    { duration: '30s', target: 10 },
    { duration: '1m', target: 10 },
    { duration: '30s', target: 30 },
    { duration: '1m', target: 30 },
    { duration: '30s', target: 60 },
    { duration: '1m', target: 60 },
    { duration: '30s', target: 100 },
    { duration: '1m', target: 100 },
    { duration: '20s', target: 0 },
];

export const options = {
    setupTimeout: '3m',
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
    scenarios: {
        recipe: SMOKE
            ? { executor: 'per-vu-iterations', vus: 1, iterations: 1 }
            : { executor: 'ramping-vus', startVUs: 0, stages: loadStages, gracefulRampDown: '10s' },
    },
    thresholds: {
        // 합격선이 아니라 Before/After 비교용 기준선이다. 실패해도 측정 결과는 그대로 남는다.
        'http_req_failed{phase:journey}': ['rate<0.01'],
        'http_req_duration{phase:journey}': ['p(95)<1000'],
        // API별 결과를 요약에 따로 보이게 하기 위한 항목이다.
        'http_req_duration{name:recipe_list}': ['p(95)<1000'],
        'http_req_duration{name:recipe_detail}': ['p(95)<1000'],
        'http_req_duration{name:recipe_suggestions}': ['p(95)<1000'],
    },
};

// 시드 번호 n의 레시피 ID. n이 20의 배수면 INACTIVE라 상세가 404이므로 고르지 않는다.
function recipeIdOf(n) {
    return RECIPE_FIRST_ID + n - 1;
}

function pickRecipeNo() {
    if (Math.random() < HOT_DETAIL_RATIO) {
        return 1 + Math.floor(Math.random() * HOT_RECIPE_COUNT);
    }
    let n;
    do {
        n = 1 + Math.floor(Math.random() * RECIPE_COUNT);
    } while (n % 20 === 0);
    return n;
}

function pick(list) {
    return list[Math.floor(Math.random() * list.length)];
}

export function setup() {
    // 로그인은 Cookie를 발급하는 POST라 CSRF Cookie와 같은 값의 헤더가 필요하다. Cookie는 k6가 setup 동안 유지한다
    const csrf = http.get(`${BASE_URL}/api/auth/csrf`, { tags: { phase: 'setup' } });
    if (csrf.status !== 200) {
        throw new Error(`CSRF 토큰 발급 실패: ${csrf.status}`);
    }
    const setupParams = {
        headers: { 'Content-Type': 'application/json', [csrf.json('data.headerName')]: csrf.json('data.token') },
        tags: { phase: 'setup' },
    };
    const tokens = [];
    for (let i = 1; i <= MEMBER_COUNT; i++) {
        const email = `perf-user-${String(i).padStart(3, '0')}@recipick.test`;
        const res = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({ email, password: PERF_PASSWORD }), setupParams);
        if (res.status !== 200) {
            throw new Error(`로그인 실패 ${email}: ${res.status} (seed-members.sql 실행 여부 확인)`);
        }
        tokens.push(res.json('data.accessToken'));
    }

    // 시드 1번 레시피가 예상한 ID에 있는지 확인해, 다른 데이터로 측정하는 실수를 막는다
    const first = http.get(`${BASE_URL}/api/recipes/${recipeIdOf(1)}`, {
        headers: { Authorization: `Bearer ${tokens[0]}` },
        tags: { phase: 'setup' },
    });
    const name = first.status === 200 ? first.json('data.name') : '';
    if (!name.startsWith('PERF-') || !name.endsWith(' 1')) {
        throw new Error(`RECIPE_FIRST_ID=${RECIPE_FIRST_ID} 가 시드 1번 레시피가 아닙니다(status ${first.status}). seed-recipes.sql 실행과 ID를 확인하세요.`);
    }
    return { tokens };
}

function listRecipes(params) {
    const keyword = encodeURIComponent(pick(LIST_KEYWORDS));
    const res = http.get(`${BASE_URL}/api/recipes?keyword=${keyword}&size=20`, {
        ...params, tags: { phase: 'journey', name: 'recipe_list' },
    });
    check(res, {
        '목록 200': (r) => r.status === 200,
        '목록 20건': (r) => r.status === 200 && r.json('data.content').length === 20,
    });
}

function getRecipe(params) {
    const id = recipeIdOf(pickRecipeNo());
    const res = http.get(`${BASE_URL}/api/recipes/${id}?servings=2`, {
        ...params, tags: { phase: 'journey', name: 'recipe_detail' },
    });
    check(res, {
        '상세 200': (r) => r.status === 200,
        '상세 ID 일치': (r) => r.status === 200 && r.json('data.recipeId') === id,
    });
}

function suggestRecipes(params) {
    const keyword = encodeURIComponent(pick(SUGGESTION_KEYWORDS));
    const res = http.get(`${BASE_URL}/api/recipes/suggestions?keyword=${keyword}`, {
        ...params, tags: { phase: 'journey', name: 'recipe_suggestions' },
    });
    check(res, { '자동완성 200': (r) => r.status === 200 });
}

export default function (data) {
    // VU마다 서로 다른 회원 토큰을 쓴다.
    const token = data.tokens[(__VU - 1) % data.tokens.length];
    const params = { headers: { Authorization: `Bearer ${token}` } };

    if (SMOKE) {
        listRecipes(params);
        getRecipe(params);
        suggestRecipes(params);
        return;
    }

    const roll = Math.random();
    if (roll < 0.5) {
        listRecipes(params);
    } else if (roll < 0.8) {
        getRecipe(params);
    } else {
        suggestRecipes(params);
    }
    sleep(1);
}
