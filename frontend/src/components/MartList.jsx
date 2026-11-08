import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { findAddressByCoords, kakaoAvailable } from '../address/kakao';
import { api } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import ErrorNotice from './ErrorNotice';

const NEAR_LIMIT = 5;
const TIER_ORDER = { CITY: 0, PROVINCE: 1, FAR: 2 };
const TIER_LABEL = { CITY: '같은 구·시', PROVINCE: '같은 시·도' };

// 법정동 코드 앞자리로 가까운 정도를 나눈다. 앞 5자리가 같으면 같은 구·시, 앞 2자리가 같으면 같은 시·도다.
// 거리(km)가 아니라 참고용 단계이고, 이용 가능 여부는 서버가 정한 이용 마트(member.mart)만 따른다
function tierOf(myCode, martCode) {
  if (!martCode) return 'FAR';
  if (martCode.slice(0, 5) === myCode.slice(0, 5)) return 'CITY';
  if (martCode.slice(0, 2) === myCode.slice(0, 2)) return 'PROVINCE';
  return 'FAR';
}

function MartRow({ mart, mine, tier, address, busy, onSwitch }) {
  const open = mart.status === 'OPEN';
  return (
    <li className="mart-list__item">
      <span>
        <strong>{mart.name}</strong>
        {TIER_LABEL[tier] && <small className="mart-list__tier">{TIER_LABEL[tier]}</small>}
        {address && <small className="mart-list__sub">내 배송지 '{address.label || address.address}'가 이 마트 지역이에요</small>}
      </span>
      <span className="mart-list__tags">
        {mine && <span className="badge">내 이용 마트</span>}
        <span className={open ? 'badge' : 'badge badge--warn'}>{open ? '가맹 마트 운영 중' : '준비 중'}</span>
        {address && (
          <button type="button" className="btn btn--muted" disabled={busy} onClick={onSwitch}>
            {busy ? '바꾸는 중...' : '이 마트로 바꾸기'}
          </button>
        )}
      </span>
    </li>
  );
}

// 가맹 마트 목록(GET /api/config/marts, 로그인 불필요).
// 배송지가 있으면 가까운 지역의 마트를 최대 5곳까지 보여 주고, 내 이용 마트가 그 안에 없으면 아래에 따로 보여 준다.
// 내 저장 배송지 중 그 마트 지역에 해당하는 것이 있으면 바로 그 배송지를 선택해 이용 마트를 바꿀 수 있다
export default function MartList({ onChanged }) {
  const { member, reloadMember } = useAuth();
  const myCode = member?.currentAddress?.legalDongCode;
  const [marts, setMarts] = useState(null);
  const [failed, setFailed] = useState(false);
  // 마트별 법정동 코드. rank가 'fail'이면 지도 키가 없거나 변환이 모두 실패한 것이라 단계 없이 목록만 보여 준다
  const [codes, setCodes] = useState({});
  const [rank, setRank] = useState('idle');
  const [addresses, setAddresses] = useState([]);
  const [busyId, setBusyId] = useState(null);
  const [message, setMessage] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    let ignore = false;
    fetch('/api/config/marts')
      .then((res) => {
        if (!res.ok) throw new Error(String(res.status));
        return res.json();
      })
      .then((json) => {
        if (!ignore) setMarts(json.data ?? []);
      })
      .catch(() => {
        if (!ignore) setFailed(true);
      });
    return () => {
      ignore = true;
    };
  }, []);

  // 마트 위치를 법정동 코드로 바꿔 내 배송지와 비교한다
  useEffect(() => {
    if (!marts || marts.length === 0 || !myCode) return undefined;
    if (!kakaoAvailable) {
      setRank('fail');
      return undefined;
    }
    let ignore = false;
    setRank((prev) => (prev === 'done' ? prev : 'loading'));
    Promise.all(
      marts.map((mart) =>
        findAddressByCoords(Number(mart.latitude), Number(mart.longitude))
          .then((found) => found.legalDongCode)
          .catch(() => null),
      ),
    ).then((list) => {
      if (ignore) return;
      if (list.every((code) => code === null)) {
        setRank('fail');
        return;
      }
      setCodes(Object.fromEntries(marts.map((mart, i) => [mart.martId, list[i]])));
      setRank('done');
    });
    return () => {
      ignore = true;
    };
  }, [marts, myCode]);

  // 내 저장 배송지. 마트 지역과 같은 배송지를 찾는 데 쓴다
  const loadAddresses = useCallback(async () => {
    try {
      const data = await api.get('/api/members/me/addresses');
      setAddresses(data?.addresses ?? []);
    } catch {
      // 배송지를 못 읽어도 목록은 보여 주고, 바꾸기 버튼만 나오지 않는다
      setAddresses([]);
    }
  }, []);

  useEffect(() => {
    if (member) loadAddresses();
  }, [member, loadAddresses]);

  const isMine = (mart) => member?.mart?.martId === mart.martId;
  // 마트 지역과 법정동이 같은 저장 배송지. 이미 선택된 배송지와 준비 중인 마트는 바꿀 수 없다
  const addressFor = (mart) => {
    const code = codes[mart.martId];
    if (!code || mart.status !== 'OPEN') return null;
    return addresses.find((a) => !a.selected && a.legalDongCode === code) ?? null;
  };

  const switchTo = async (mart, address) => {
    const name = address.label || address.address;
    if (!window.confirm(`배송지를 '${name}'(으)로 바꾸고 ${mart.name}을(를) 이용할까요? 이전 마트 상품은 장바구니에서 삭제해야 해요.`)) return;
    setBusyId(mart.martId);
    setError(null);
    setMessage(null);
    try {
      const result = await api.put(`/api/members/me/addresses/${address.addressId}/select`);
      await (onChanged ? onChanged() : reloadMember());
      await loadAddresses();
      if (result?.mart && result.mart.martId !== mart.martId) {
        setMessage(`선택한 배송지의 이용 마트는 ${result.mart.name}이에요.`);
      } else if (result?.martChanged) {
        setMessage(`이용 마트가 ${result.mart?.name}(으)로 바뀌었어요. 이전 마트 상품은 장바구니에서 삭제해야 해요.`);
      } else {
        setMessage('배송지를 바꿨어요.');
      }
    } catch (e) {
      setError(e);
    } finally {
      setBusyId(null);
    }
  };

  const row = (mart, tier) => {
    const address = addressFor(mart);
    return (
      <MartRow
        key={mart.martId}
        mart={mart}
        mine={isMine(mart)}
        tier={tier}
        address={address}
        busy={busyId === mart.martId}
        onSwitch={() => switchTo(mart, address)}
      />
    );
  };

  const renderBody = () => {
    if (failed) return <p className="field__hint">마트 목록을 불러오지 못했어요. 잠시 뒤에 다시 시도해 주세요.</p>;
    if (!marts) return <p className="field__hint">마트 목록을 불러오는 중이에요.</p>;
    if (marts.length === 0) return <p className="field__hint">표시할 마트가 아직 없어요.</p>;
    if (myCode && rank === 'loading') return <p className="field__hint">가까운 마트를 찾고 있어요.</p>;

    // 배송지가 없거나 단계를 구하지 못하면 내 이용 마트와 운영 중인 마트를 앞에 두고 5곳까지만 보여 준다
    if (!myCode || rank !== 'done') {
      const ordered = [...marts].sort(
        (a, b) => Number(isMine(b)) - Number(isMine(a)) || Number(b.status === 'OPEN') - Number(a.status === 'OPEN'),
      );
      const rest = ordered.slice(NEAR_LIMIT);
      return (
        <>
          {!myCode && (
            <p className="field__hint">
              <Link to={member ? '/addresses' : '/start'}>배송지를 {member ? '등록' : '입력'}</Link>하면 가까운 마트부터 보여 드려요.
            </p>
          )}
          <ul className="mart-list">{ordered.slice(0, NEAR_LIMIT).map((mart) => row(mart))}</ul>
          {rest.length > 0 && (
            <details className="mart-list__more">
              <summary>마트 {rest.length}곳 더 보기</summary>
              <ul className="mart-list">{rest.map((mart) => row(mart))}</ul>
            </details>
          )}
        </>
      );
    }

    const tierOfMart = (mart) => tierOf(myCode, codes[mart.martId]);
    // 운영 중인 마트, 가까운 단계, 내 이용 마트 순으로 5곳을 고른다
    const near = marts
      .filter((mart) => tierOfMart(mart) !== 'FAR')
      .sort(
        (a, b) =>
          Number(b.status === 'OPEN') - Number(a.status === 'OPEN') ||
          TIER_ORDER[tierOfMart(a)] - TIER_ORDER[tierOfMart(b)] ||
          Number(isMine(b)) - Number(isMine(a)),
      )
      .slice(0, NEAR_LIMIT);
    const myMart = marts.find(isMine);
    const myMartShown = near.some(isMine);

    return (
      <>
        <p className="mart-list__heading">가까운 마트</p>
        {near.length > 0 ? (
          <ul className="mart-list">{near.map((mart) => row(mart, tierOfMart(mart)))}</ul>
        ) : (
          <p className="field__hint">내 배송지와 가까운 지역에는 가맹 마트가 아직 없어요.</p>
        )}
        {myMart && !myMartShown && (
          <>
            <p className="mart-list__heading">내 이용 마트</p>
            <ul className="mart-list">{row(myMart)}</ul>
          </>
        )}
      </>
    );
  };

  return (
    <>
      {message && <div className="notice notice--info">{message}</div>}
      <ErrorNotice error={error} />
      {renderBody()}
    </>
  );
}
