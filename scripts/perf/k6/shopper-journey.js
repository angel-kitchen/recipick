// 실행 준비: docs/performance/load-test.md의 "100명 종합 시나리오 준비" 참고.
// 조회가 기본이다. purchase는 결제 성공/환불이 아니라 주문 생성 → 결제 전 취소만 측정한다.
import { sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { request, requireState } from './lib/shopper-api.js';
import { USER_COUNT, MODE, SMOKE, writesEnabled, validateFixture, loadOptions } from './lib/shopper-config.js';

const fixture = JSON.parse(open(__ENV.FIXTURE_FILE || './shopper-fixtures.example.json'));
export const options = loadOptions();
const journeyFailed = new Rate('journey_failed');
const journeyDuration = new Trend('journey_duration', true);
const completed = new Counter('journey_completed');
const blockedWriters = new Counter('blocked_writers');
const cleanupFailed = new Counter('cleanup_failed');
let writerBlocked = false; // VU별 독립 상태. 불명확한 주문이 남은 회원에게 새 주문을 반복 생성하지 않는다.

export function setup() {
  validateFixture(fixture);
  const users = [];
  const runId = `${Date.now().toString(36)}-${Math.floor(Math.random() * 1000000).toString(36)}`;
  for (let i = 1; i <= (SMOKE ? 1 : USER_COUNT); i++) {
    const email = `${fixture.emailPrefix}${String(i).padStart(3, '0')}@${fixture.emailDomain}`;
    const login = request('POST', '/api/auth/login', null,
      { email, password: __ENV.PERF_PASSWORD }, 'login', 'setup');
    requireState(login?.accessToken, '로그인 토큰 존재');
    const token = login.accessToken;
    const me = request('GET', '/api/members/me', token, null, 'member_me', 'setup');
    requireState(me?.mart?.martId, '테스트 회원의 이용 마트 지정');
    const products = request('GET', '/api/products?size=1', token, null, 'product_list', 'setup');
    requireState(products?.content?.length > 0, '회원 마트의 상품 데이터 존재');
    const recipes = request('GET', '/api/recipes?size=1', token, null, 'recipe_list', 'setup');
    requireState(recipes?.content?.length > 0, '레시피 데이터 존재');
    const productId = writesEnabled ? fixture.productIds[(i - 1) % fixture.productIds.length]
      : products.content[0].productId;
    if (writesEnabled) {
      const cart = request('GET', '/api/cart-items', token, null, 'cart_list', 'setup');
      requireState(Array.isArray(cart.items) && cart.items.length === 0, '성능 계정 장바구니가 비어 있음');
      const ongoing = request('GET', '/api/orders?statusGroup=ONGOING&size=1', token, null, 'order_list', 'setup');
      requireState(ongoing.totalElements === 0, '성능 계정에 진행 중 주문 없음');
      request('GET', `/api/products/${productId}`, token, null, 'product_detail', 'setup');
    }
    users.push({ token, productId, recipeId: recipes.content[0].recipeId });
  }
  return { users, runId };
}

function browse(user) {
  const keyword = fixture.keywords[(__VU + __ITER) % fixture.keywords.length];
  request('GET', `/api/products?keyword=${encodeURIComponent(keyword)}&size=20`, user.token, null, 'product_search');
  request('GET', `/api/products/${user.productId}`, user.token, null, 'product_detail');
  request('GET', '/api/categories', null, null, 'categories');
}

function recipes(user) {
  request('GET', '/api/recipes?size=20', user.token, null, 'recipe_list');
  request('GET', `/api/recipes/${user.recipeId}?servings=2`, user.token, null, 'recipe_detail');
}

function account(user) {
  request('GET', '/api/members/me', user.token, null, 'member_me');
  request('GET', '/api/orders?size=20', user.token, null, 'order_list');
  request('GET', '/api/points', user.token, null, 'point_balance');
}

function cancelBeforePay(user, order, key) {
  const payment = request('GET', `/api/payments/orders/${order.orderId}`, user.token, null, 'payment_detail');
  requireState(payment.paymentId === order.paymentId && payment.paymentStatus === 'IN_PROGRESS'
    && payment.amount > 0, '취소 대상은 양수 결제 대기 주문');
  const canceled = request('POST', `/api/payments/${order.paymentId}/cancel`, user.token,
    { reason: '성능 테스트 결제 전 취소' }, 'cancel_before_pay', 'journey', [200], key);
  requireState(canceled.paymentStatus === 'FAILED' && canceled.failureReason === 'CANCELED_BEFORE_PAY',
    '결제 전 취소 상태 확인');
}

function shopping(user, runId, createOrder) {
  let cartItemId = null;
  let order = null;
  let canceled = false;
  let orderAttempted = false;
  const key = `perf-${runId}-${__VU}-${__ITER}`;
  try {
    const added = request('POST', '/api/cart-items', user.token,
      { productId: user.productId, quantity: 1 }, 'cart_add');
    cartItemId = added.cartItemId;
    requireState(cartItemId, '추가 장바구니 ID 존재');
    request('PATCH', `/api/cart-items/${cartItemId}`, user.token, { quantity: 2 }, 'cart_update');
    request('GET', '/api/cart-items', user.token, null, 'cart_list');
    if (createOrder) {
      const preview = request('POST', '/api/orders/preview', user.token,
        { cartItemIds: [cartItemId], legalDongCode: fixture.delivery.legalDongCode }, 'order_preview');
      requireState(preview.orderable && preview.payAmount > 0, '양수 결제 주문 미리보기 가능');
      const body = { ...fixture.delivery, cartItemIds: [cartItemId], usedPoint: 0 };
      orderAttempted = true;
      try {
        order = request('POST', '/api/orders', user.token, body, 'order_create', 'journey', [200, 201], key);
      } catch (error) {
        // 응답 유실·서버 오류만 같은 키·같은 본문으로 한 번 확인한다. 다른 키로 재시도하지 않는다.
        if (error.status !== 0 && error.status < 500) throw error;
        order = request('POST', '/api/orders', user.token, body, 'order_create_retry', 'journey', [200, 201], key);
      }
      requireState(order?.orderId && order?.paymentId && order.status === 'PENDING_PAYMENT' && order.amount > 0,
        '생성 결과는 결제 대기 주문');
      cancelBeforePay(user, order, `${key}-cancel`);
      canceled = true;
      const detail = request('GET', `/api/orders/${order.orderId}`, user.token, null, 'order_detail');
      requireState(detail.status === 'CANCELED', '주문 취소 종료 확인');
    }
  } catch (error) {
    writerBlocked = true;
    blockedWriters.add(1);
    throw error;
  } finally {
    // 확인된 본인 항목만 정리한다. 취소 여부가 불명확한 주문이 있으면 임의 삭제하지 않는다.
    if (cartItemId && (!orderAttempted || canceled)) {
      try { request('DELETE', `/api/cart-items/${cartItemId}`, user.token, null, 'cart_delete'); }
      catch (_) {
        writerBlocked = true; cleanupFailed.add(1);
        throw new Error('장바구니 정리 실패: 해당 회원의 쓰기 시나리오 중단');
      }
    }
  }
}

export default function (data) {
  const user = data.users[__VU - 1];
  if (writerBlocked) { sleep(1); return; }
  blockedWriters.add(0);
  cleanupFailed.add(0);
  const started = Date.now();
  let ok = false;
  try {
    if (SMOKE && (MODE === 'read' || MODE === 'mixed')) {
      browse(user); recipes(user); account(user);
      if (MODE === 'mixed') {
        shopping(user, data.runId, false);
        shopping(user, data.runId, true);
      }
    } else if (MODE === 'cart' || MODE === 'purchase') {
      shopping(user, data.runId, MODE === 'purchase');
    } else {
      // 초기 비율은 측정용 가정이다. mixed: 상품 50%·레시피 20%·내 정보 10%·장바구니 10%·주문 10%.
      const slot = (__VU + __ITER) % 10;
      if (MODE === 'mixed' && slot === 8) shopping(user, data.runId, false);
      else if (MODE === 'mixed' && slot === 9) shopping(user, data.runId, true);
      else if (slot < 7 && (MODE === 'read' || slot < 5)) browse(user);
      else if (slot < 9 && (MODE === 'read' || slot < 7)) recipes(user);
      else account(user);
    }
    ok = true;
    completed.add(1, { mode: MODE });
  } catch (error) {
    // 오류 분류만 출력하고 응답 본문·인증 정보는 출력하지 않는다.
    console.error(error.message);
  } finally {
    journeyFailed.add(!ok, { mode: MODE });
    journeyDuration.add(Date.now() - started, { mode: MODE });
    sleep(1);
  }
}
