export const USER_COUNT = 100;
export const MODE = __ENV.MODE || 'read';
export const SMOKE = __ENV.SMOKE === '1';
export const BASE_URL = (__ENV.BASE_URL || 'http://host.docker.internal:8080').replace(/\/$/, '');
export const MODES = ['read', 'cart', 'purchase', 'mixed'];
export const writesEnabled = MODE !== 'read';

export function validateFixture(fixture) {
    if (!MODES.includes(MODE)) throw new Error('MODE는 read/cart/purchase/mixed 중 하나여야 합니다.');
    if (!__ENV.PERF_PASSWORD) throw new Error('성능 전용 계정의 PERF_PASSWORD가 필요합니다.');
    if (!fixture.emailPrefix || !fixture.emailDomain) throw new Error('성능 계정 이메일 규칙이 필요합니다.');
    if (!Array.isArray(fixture.keywords) || !fixture.keywords.length) throw new Error('검색어가 필요합니다.');
    if (writesEnabled) {
        if (!Array.isArray(fixture.productIds) || !fixture.productIds.length
                || fixture.productIds.some(id => !Number.isSafeInteger(id) || id <= 0)) {
            throw new Error('쓰기 모드는 확인된 성능 전용 productIds를 채운 뒤 실행하세요.');
        }
        const d = fixture.delivery;
        if (!d || !d.receiverName || !/^\d{10,11}$/.test(d.receiverPhone)
                || !d.address || !/^\d{10}$/.test(d.legalDongCode)) {
            throw new Error('쓰기 모드는 이용 마트와 일치하는 성능용 배송 정보를 먼저 채워야 합니다.');
        }
    }
}

// 100은 최대 동시 VU 수다. 요청 수/초가 아니며, 단계 사이와 요청 흐름 끝에 대기한다.
export function loadOptions() {
    return {
        setupTimeout: '3m',
        summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
        scenarios: SMOKE ? {
            smoke: { executor: 'per-vu-iterations', vus: 1, iterations: 1, maxDuration: '2m' },
        } : {
            shoppers: {
                executor: 'ramping-vus', startVUs: 0,
                stages: [
                    { duration: '30s', target: 10 }, { duration: '1m', target: 10 },
                    { duration: '30s', target: 30 }, { duration: '1m', target: 30 },
                    { duration: '30s', target: 60 }, { duration: '1m', target: 60 },
                    { duration: '30s', target: USER_COUNT }, { duration: '1m', target: USER_COUNT },
                    { duration: '20s', target: 0 },
                ], gracefulRampDown: '30s', gracefulStop: '30s',
            },
        },
        thresholds: {
            'http_req_failed{phase:journey}': ['rate<0.01'],
            'http_req_duration{phase:journey}': ['p(95)<1000'],
            journey_failed: ['rate<0.01'],
            contract_failed: ['rate<0.01'],
            blocked_writers: ['count==0'],
            cleanup_failed: ['count==0'],
            skipped_writes: ['count==0'],
            'http_req_duration{name:product_search,phase:journey}': ['p(95)<1000'],
            'http_req_duration{name:recipe_list,phase:journey}': ['p(95)<1000'],
        },
    };
}
