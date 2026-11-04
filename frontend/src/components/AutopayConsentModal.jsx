import { useState } from 'react';
import ConfirmModal from './ConfirmModal';
import { MEMBERSHIP_PRICE } from '../api/membership';
import { formatWon } from '../labels';

// 체험·구독 시작 전 자동결제 동의. 문구를 바꾸면 api/membership.js의 CONSENT_TEXT_VERSION도 올린다
export default function AutopayConsentModal({ trial, busy, error, onConfirm, onCancel }) {
  const [agreed, setAgreed] = useState(false);
  const [askedAgree, setAskedAgree] = useState(false);
  const firstChargeDate = new Date();
  if (trial) firstChargeDate.setDate(firstChargeDate.getDate() + 7);

  return (
    <ConfirmModal
      title={trial ? '무료 체험 시작' : '멤버십 구독'}
      confirmLabel={trial ? '체험 시작' : `${formatWon(MEMBERSHIP_PRICE)} 결제하고 구독`}
      cancelLabel="취소"
      busy={busy}
      error={askedAgree && !agreed ? { message: '자동 결제 동의에 체크해 주세요.' } : error}
      onConfirm={() => (agreed ? onConfirm() : setAskedAgree(true))}
      onCancel={onCancel}
    >
      <dl className="info-list">
        <dt>결제 금액</dt>
        <dd>월 {formatWon(MEMBERSHIP_PRICE)}</dd>
        <dt>첫 결제일</dt>
        <dd>
          {firstChargeDate.toLocaleDateString('ko-KR')}
          {trial ? ' (7일 체험이 끝나는 날, 체험 시작 때는 결제되지 않아요)' : ' (오늘)'}
        </dd>
        <dt>갱신 주기</dt>
        <dd>첫 결제일을 기준으로 매월 등록한 카드로 자동 결제돼요.</dd>
        <dt>해지 방법</dt>
        <dd>
          내 멤버십에서 언제든 해지 예약을 하면 다음 결제부터 멈추고 남은 기간은 그대로 쓸 수 있어요. 유료 이용 중에는 즉시
          해지도 할 수 있고, 이용 요금과 위약금을 뺀 금액을 돌려받아요.
        </dd>
      </dl>
      <label style={{ display: 'flex', gap: 8, marginTop: 12 }}>
        <input type="checkbox" checked={agreed} disabled={busy} onChange={(e) => setAgreed(e.target.checked)} />
        위 내용을 확인했고 자동 결제에 동의합니다.
      </label>
    </ConfirmModal>
  );
}
