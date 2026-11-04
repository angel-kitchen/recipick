import * as PortOne from '@portone/browser-sdk/v2';
import { api } from './client';

// 화면에 보여 준 동의 문구의 버전. 문구를 바꾸면 버전도 올린다
export const CONSENT_TEXT_VERSION = 'v1';
export const MEMBERSHIP_PRICE = 3900;

// 모바일 카드 등록은 페이지를 옮겨 갔다 돌아오므로 등록에 쓸 issueId를 그동안 들고 있어야 한다.
// 30분짜리 일회성 토큰이라 탭을 닫으면 사라지는 sessionStorage에 두고 쓰자마자 지운다
const ISSUE_ID_KEY = 'billingKeyIssueId';

function saveIssueId(issueId) {
  try {
    sessionStorage.setItem(ISSUE_ID_KEY, issueId);
  } catch {
    // 저장하지 못해도 PC 팝업 등록은 issueId를 바로 넘기므로 된다. 모바일은 돌아온 뒤 다시 등록하라고 안내한다
  }
}

// 꺼낸 값은 일회성이라 바로 지운다. 저장소를 못 쓰면 보관한 값이 없는 것으로 본다
function takeIssueId() {
  try {
    const issueId = sessionStorage.getItem(ISSUE_ID_KEY);
    sessionStorage.removeItem(ISSUE_ID_KEY);
    return issueId;
  } catch {
    return null;
  }
}

// 결과: { status: 'REGISTERED', outcome } | { status: 'CLOSED', message } | { status: 'REDIRECTED' }
export async function registerCard(customer) {
  const issue = await api.post('/api/billing-keys/issue-requests');
  saveIssueId(issue.issueId);
  let response;
  try {
    response = await PortOne.requestIssueBillingKey({
      storeId: issue.storeId,
      channelKey: issue.channelKey,
      billingKeyMethod: 'CARD',
      issueId: issue.issueId,
      issueName: issue.issueName,
      displayAmount: issue.displayAmount,
      currency: issue.currency,
      // 서버가 PortOne의 customer.id가 로그인 회원과 같은지 확인한다
      customer: { ...customer, customerId: issue.customerId },
      redirectUrl: `${window.location.origin}/membership`,
    });
  } catch (e) {
    takeIssueId();
    throw e;
  }
  if (!response) return { status: 'REDIRECTED' };
  takeIssueId();
  return finishCardRegistration(response.billingKey, response.code, response.message, issue.issueId);
}

// 모바일에서 돌아오면 쿼리의 billingKey·code·message와 보관한 issueId로 이어서 등록한다
export async function finishCardRegistration(billingKey, code, message, issueId = takeIssueId()) {
  // 창을 닫았거나 발급이 실패했다. 서버에 보낼 키가 없다
  if (code || !billingKey) return { status: 'CLOSED', message };
  if (!issueId) return { status: 'CLOSED', message: '카드 등록 요청이 만료됐어요. 다시 등록해 주세요.' };
  const result = await api.post('/api/billing-keys', { issueId, billingKey });
  return { status: 'REGISTERED', outcome: result.outcome };
}

const consentBody = () => ({
  autopayAgreed: true,
  consentedAmount: MEMBERSHIP_PRICE,
  consentTextVersion: CONSENT_TEXT_VERSION,
});

export const startTrial = () => api.post('/api/memberships/trial', consentBody());

export const subscribe = (idempotencyKey) =>
  api.post('/api/memberships/subscribe', consentBody(), { 'Idempotency-Key': idempotencyKey });

export const reserveCancellation = (membershipId) => api.post(`/api/memberships/${membershipId}/cancel-reservation`);

export const terminate = (membershipId, idempotencyKey) =>
  api.post(`/api/memberships/${membershipId}/terminate`, undefined, { 'Idempotency-Key': idempotencyKey });
