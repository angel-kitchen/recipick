import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { api, isUnknownOutcome } from '../api/client';
import { customerOf } from '../api/payments';
import {
  finishCardRegistration,
  registerCard,
  reserveCancellation,
  startTrial,
  subscribe,
  terminate,
} from '../api/membership';
import { useAuth } from '../auth/AuthContext';
import AutopayConsentModal from '../components/AutopayConsentModal';
import ConfirmModal from '../components/ConfirmModal';
import ErrorNotice from '../components/ErrorNotice';
import { MEMBERSHIP_SOURCE, MEMBERSHIP_STATUS, MEMBERSHIP_TYPE, formatDateTime, formatWon } from '../labels';

const PAID_SOURCES = ['SUBSCRIBE', 'TRIAL_CONVERT'];

export default function Membership() {
  const { member, reloadMember } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [membership, setMembership] = useState(null);
  const [card, setCard] = useState(null);
  const [error, setError] = useState(null);
  // 처리 결과 안내. checking이면 같은 키로 결과를 다시 확인하는 버튼을 보여 준다
  const [notice, setNotice] = useState(null);
  const [modal, setModal] = useState(null);
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState(null);
  // 결과를 모르는 동안에는 같은 키로만 다시 보낸다. 새 키는 새 청구·새 환불이 된다
  const subscribeKey = useRef(null);
  const terminateKey = useRef(null);
  const redirectHandled = useRef(false);

  const load = useCallback(async () => {
    try {
      const [me, mine] = await Promise.all([api.get('/api/memberships/me'), api.get('/api/billing-keys/me')]);
      setMembership(me);
      setCard(mine);
    } catch (e) {
      setError(e);
    }
  }, []);

  // 멤버십이 바뀌는 일이 끝나면 이 화면과 헤더 등 다른 화면의 회원 정보를 함께 다시 읽는다.
  // 결제·해지는 이미 처리됐으므로 회원 정보를 못 읽어도 처리가 실패한 것처럼 보이지 않게 한다
  const refresh = async () => {
    await load();
    await reloadMember().catch(() => {});
  };

  // 모바일 카드 등록은 이 페이지로 리디렉션하며 billingKey 또는 code·message를 쿼리로 붙인다
  useEffect(() => {
    if (searchParams.get('transactionType') !== 'ISSUE_BILLING_KEY' || redirectHandled.current) {
      load();
      return;
    }
    redirectHandled.current = true;
    finishCardRegistration(searchParams.get('billingKey'), searchParams.get('code'), searchParams.get('message'))
      .then(cardNotice, (e) => setNotice({ error: e }))
      .finally(() => {
        // 새로고침할 때 같은 등록을 다시 보내지 않게 쿼리를 지운다
        navigate('/membership', { replace: true });
        load();
      });
  }, [load]);

  const cardNotice = (result) => {
    if (result.status === 'REGISTERED') setNotice({ message: '카드가 등록됐어요.' });
    if (result.status === 'CLOSED') setNotice({ message: result.message || '카드 등록을 마치지 않았어요.' });
  };

  const run = async (task) => {
    setBusy(true);
    setActionError(null);
    try {
      await task();
    } catch (e) {
      setActionError(e);
    } finally {
      setBusy(false);
    }
  };

  const addCard = () =>
    run(async () => {
      setNotice(null);
      const result = await registerCard(customerOf(member));
      if (result.status === 'REDIRECTED') return;
      cardNotice(result);
      await load();
    });

  const closeModal = () => {
    setModal(null);
    setActionError(null);
  };

  const doTrial = () =>
    run(async () => {
      await startTrial();
      setModal(null);
      setNotice({ message: '무료 체험이 시작됐어요.' });
      await refresh();
    });

  const doSubscribe = () =>
    run(async () => {
      subscribeKey.current ??= crypto.randomUUID();
      let result;
      try {
        result = await subscribe(subscribeKey.current);
      } catch (e) {
        if (!isUnknownOutcome(e)) subscribeKey.current = null;
        throw e;
      }
      setModal(null);
      if (result.status === 'IN_PROGRESS' || result.status === 'UNKNOWN') {
        setNotice({ message: '첫 결제를 확인하고 있어요. 잠시 뒤 결과를 다시 확인해 주세요.', checking: 'subscribe' });
        // 처리 중이어도 서버에는 결제 대기 이용권이 생겼을 수 있어 상태를 다시 읽는다
        await refresh();
        return;
      }
      subscribeKey.current = null;
      setNotice(
        result.status === 'PAID'
          ? { message: '구독이 시작됐어요.' }
          : { message: '첫 결제에 실패했어요. 카드를 확인하고 다시 구독해 주세요.' },
      );
      await refresh();
    });

  const doReserve = (target) =>
    run(async () => {
      await reserveCancellation(target.membershipId);
      setModal(null);
      setNotice({ message: '해지 예약했어요. 다음 자동 결제부터 멈춰요.' });
      await refresh();
    });

  const doTerminate = (target) =>
    run(async () => {
      terminateKey.current ??= crypto.randomUUID();
      let result;
      try {
        result = await terminate(target.membershipId, terminateKey.current);
      } catch (e) {
        if (!isUnknownOutcome(e)) terminateKey.current = null;
        throw e;
      }
      setModal(null);
      const review = result.reviewRequired ? ' 운영팀이 함께 확인하고 있어요.' : '';
      if (result.refundStatus === 'REQUESTED') {
        setNotice({ message: `환불을 확인하고 있어요.${review}`, checking: 'terminate', target });
        return;
      }
      terminateKey.current = null;
      setNotice({
        message:
          result.refundStatus === 'COMPLETED'
            ? `해지했어요. ${formatWon(result.refundAmount)}을 환불했어요.${review}`
            : `환불에 실패해 멤버십을 그대로 유지했어요. 다시 해지해 주세요.${review}`,
      });
      await refresh();
    });

  if (!membership) {
    return (
      <div className="container page">
        <ErrorNotice error={error} />
        {!error && <p className="state-message">불러오는 중입니다.</p>}
      </div>
    );
  }

  const { current, pending } = membership;
  const reservable = [current, pending].find(
    (m) => m && m.source !== 'ADMIN_GRANT' && m.autoRenew && !m.cancelReservedAt,
  );
  const terminable = current && current.type === 'PAID' && PAID_SOURCES.includes(current.source) ? current : null;
  const canStart = !membership.active && !pending;
  // 해지 버튼은 멤버십이 있으면 항상 보이고, 지금 할 수 없는 동작은 이유와 함께 비활성으로 둔다
  const hasMembership = Boolean(current || pending);
  const heldMembership = current ?? pending;
  const reserveBlockedReason =
    heldMembership?.source === 'ADMIN_GRANT'
      ? '관리자가 지급한 멤버십은 해지 예약을 할 수 없어요.'
      : heldMembership?.cancelReservedAt
        ? '이미 해지 예약했어요.'
        : '자동 갱신을 쓰지 않는 멤버십이라 해지 예약할 것이 없어요.';

  return (
    <div className="container page">
      <h1 className="page__title">내 멤버십</h1>
      {notice && (
        <div className="notice" role="status">
          {notice.error ? notice.error.message : notice.message}
          {notice.checking && (
            <div className="row-actions" style={{ marginTop: 8 }}>
              <button
                type="button"
                className="btn btn--outline"
                disabled={busy}
                onClick={() => (notice.checking === 'subscribe' ? doSubscribe() : doTerminate(notice.target))}
              >
                결과 다시 확인
              </button>
            </div>
          )}
        </div>
      )}
      {!modal && <ErrorNotice error={actionError} />}
      <section className="card">
        <h2 className="tile__title">
          {membership.active ? '멤버십 이용 중' : '멤버십 회원이 아닙니다'}{' '}
          <span className={`badge ${membership.active ? '' : 'badge--warn'}`}>{membership.active ? '이용 중' : '미가입'}</span>
        </h2>
        {current ? (
          <dl className="info-list">
            <dt>종류</dt>
            <dd>{MEMBERSHIP_TYPE[current.type] ?? current.type}</dd>
            <dt>가입 경로</dt>
            <dd>{MEMBERSHIP_SOURCE[current.source] ?? current.source}</dd>
            <dt>시작</dt>
            <dd>{formatDateTime(current.startAt)}</dd>
            <dt>종료</dt>
            <dd>{formatDateTime(current.effectiveEndAt ?? current.endAt)}</dd>
            {current.inGrace && (
              <><dt>결제 상태</dt><dd>갱신 결제 유예 중이에요. 결제수단을 확인해 주세요.</dd></>
            )}
            <dt>자동 갱신</dt>
            <dd>{current.autoRenew ? '사용' : '사용 안 함'}</dd>
            {current.cancelReservedAt && (
              <>
                <dt>해지 예약</dt>
                <dd>{formatDateTime(current.cancelReservedAt)}</dd>
              </>
            )}
          </dl>
        ) : (
          <p className="field__hint">자동 장바구니는 멤버십 회원만 이용할 수 있어요.</p>
        )}
        {pending && (
          <dl className="info-list" style={{ marginTop: 12 }}>
            <dt>처리 중</dt>
            <dd>
              {MEMBERSHIP_STATUS[pending.status] ?? pending.status}
              {` (${MEMBERSHIP_SOURCE[pending.source] ?? pending.source})`}
            </dd>
            <dt>이용 기간</dt>
            <dd>
              {formatDateTime(pending.startAt)} ~ {formatDateTime(pending.endAt)}
            </dd>
          </dl>
        )}
        {canStart && !card?.registered && (
          <p className="field__hint" style={{ marginTop: 12 }}>
            무료 체험과 구독은 결제 카드를 먼저 등록해야 시작할 수 있어요.
          </p>
        )}
        <div className="row-actions" style={{ marginTop: 12 }}>
          {canStart && membership.trialEligible && (
            <button
              type="button"
              className="btn"
              disabled={busy || !card?.registered}
              onClick={() => setModal({ type: 'trial' })}
            >
              무료 체험 시작
            </button>
          )}
          {canStart && (
            <button
              type="button"
              className="btn"
              disabled={busy || !card?.registered}
              onClick={() => setModal({ type: 'subscribe' })}
            >
              구독하기
            </button>
          )}
          {hasMembership && (
            <>
              <button
                type="button"
                className="btn btn--outline"
                disabled={busy || !reservable}
                onClick={() => setModal({ type: 'reserve', target: reservable })}
              >
                해지 예약
              </button>
              <button
                type="button"
                className="btn btn--outline"
                disabled={busy || !terminable}
                onClick={() => setModal({ type: 'terminate', target: terminable })}
              >
                즉시 해지
              </button>
            </>
          )}
        </div>
        {hasMembership && !reservable && <p className="field__hint">{reserveBlockedReason}</p>}
        {hasMembership && !terminable && (
          <p className="field__hint">즉시 해지는 유료 구독 중일 때만 할 수 있어요. 무료 체험은 해지 예약을 써 주세요.</p>
        )}
      </section>

      <section className="card" style={{ marginTop: 16 }}>
        <h2 className="tile__title">결제 카드</h2>
        <p className="field__hint">
          {card?.registered ? `등록됨 · ${formatDateTime(card.issuedAt)}` : '등록된 카드가 없어요.'}{' '}
          카드 번호는 저장하지 않아요.
        </p>
        <div className="row-actions" style={{ marginTop: 12 }}>
          <button type="button" className="btn btn--outline" disabled={busy} onClick={addCard}>
            {card?.registered ? '카드 변경' : '카드 등록'}
          </button>
        </div>
      </section>

      {(modal?.type === 'trial' || modal?.type === 'subscribe') && (
        <AutopayConsentModal
          trial={modal.type === 'trial'}
          busy={busy}
          error={actionError}
          onConfirm={modal.type === 'trial' ? doTrial : doSubscribe}
          onCancel={closeModal}
        />
      )}
      {modal?.type === 'reserve' && (
        <ConfirmModal
          title="해지 예약"
          confirmLabel="해지 예약"
          cancelLabel="취소"
          busy={busy}
          error={actionError}
          onConfirm={() => doReserve(modal.target)}
          onCancel={closeModal}
        >
          {modal.target.inGrace
            ? '현재 결제 유예 중이에요. 해지 예약하면 유예 혜택이 지금 종료되고 다음 결제를 중단해요.'
            : '다음 자동 결제를 중단해요. 이용 중인 정상 기간의 혜택은 종료 시각까지 유지돼요. 결제 대기는 아직 혜택이 시작되지 않은 상태예요.'}
        </ConfirmModal>
      )}
      {modal?.type === 'terminate' && (
        <ConfirmModal
          title="즉시 해지"
          confirmLabel="지금 해지"
          cancelLabel="취소"
          busy={busy}
          error={actionError}
          onConfirm={() => doTerminate(modal.target)}
          onCancel={closeModal}
        >
          지금 멤버십을 끝내고, 결제 금액에서 이용한 날만큼의 요금과 위약금을 뺀 금액을 돌려받아요. 환불이 끝나야 해지가
          완료돼요.
        </ConfirmModal>
      )}
    </div>
  );
}
