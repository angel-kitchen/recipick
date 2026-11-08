import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { findAddressByCoords, kakaoAvailable } from '../address/kakao';
import { useAuth } from '../auth/AuthContext';

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

function MartRow({ mart, mine, tier }) {
  const open = mart.status === 'OPEN';
  return (
    <li className="mart-list__item">
      <span>
        <strong>{mart.name}</strong>
        {TIER_LABEL[tier] && <small className="mart-list__tier">{TIER_LABEL[tier]}</small>}
      </span>
      <span className="mart-list__tags">
        {mine && <span className="badge">내 이용 마트</span>}
        <span className={open ? 'badge' : 'badge badge--warn'}>{open ? '가맹 마트 운영 중' : '준비 중'}</span>
      </span>
    </li>
  );
}

// 가맹 마트 목록(GET /api/config/marts, 로그인 불필요).
// 배송지가 있으면 내 이용 마트와 가까운 지역의 마트를 먼저 보여 주고, 먼 지역은 접어 둔다
export default function MartList() {
  const { member } = useAuth();
  const myCode = member?.currentAddress?.legalDongCode;
  const [marts, setMarts] = useState(null);
  const [failed, setFailed] = useState(false);
  // 마트별 법정동 코드. rank가 'fail'이면 지도 키가 없거나 변환이 모두 실패한 것이라 단계 없이 전체 목록을 보여 준다
  const [codes, setCodes] = useState({});
  const [rank, setRank] = useState('idle');

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
    setRank('loading');
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

  if (failed) return <p className="field__hint">마트 목록을 불러오지 못했어요. 잠시 뒤에 다시 시도해 주세요.</p>;
  if (!marts) return <p className="field__hint">마트 목록을 불러오는 중이에요.</p>;
  if (marts.length === 0) return <p className="field__hint">표시할 마트가 아직 없어요.</p>;
  if (myCode && rank === 'loading') return <p className="field__hint">가까운 마트를 찾고 있어요.</p>;

  const isMine = (mart) => member?.mart?.martId === mart.martId;
  const mine = marts.filter(isMine);
  const others = marts.filter((mart) => !isMine(mart));

  // 배송지가 없거나 단계를 구하지 못하면 내 이용 마트를 맨 위에 두고 전체를 그대로 보여 준다
  if (!myCode || rank !== 'done') {
    return (
      <>
        {!myCode && (
          <p className="field__hint">
            <Link to={member ? '/addresses' : '/start'}>배송지를 {member ? '등록' : '입력'}</Link>하면 가까운 마트부터 보여 드려요.
          </p>
        )}
        <ul className="mart-list">
          {[...mine, ...others].map((mart) => (
            <MartRow key={mart.martId} mart={mart} mine={isMine(mart)} />
          ))}
        </ul>
      </>
    );
  }

  const tierOfMart = (mart) => tierOf(myCode, codes[mart.martId]);
  const near = others
    .filter((mart) => tierOfMart(mart) !== 'FAR')
    .sort((a, b) => TIER_ORDER[tierOfMart(a)] - TIER_ORDER[tierOfMart(b)]);
  const far = others.filter((mart) => tierOfMart(mart) === 'FAR');

  return (
    <>
      <ul className="mart-list">
        {mine.map((mart) => (
          <MartRow key={mart.martId} mart={mart} mine />
        ))}
        {near.map((mart) => (
          <MartRow key={mart.martId} mart={mart} tier={tierOfMart(mart)} />
        ))}
      </ul>
      {mine.length === 0 && near.length === 0 && (
        <p className="field__hint">내 배송지와 가까운 지역에는 가맹 마트가 아직 없어요.</p>
      )}
      {far.length > 0 && (
        <details className="mart-list__more">
          <summary>다른 지역 마트 {far.length}곳 더 보기</summary>
          <ul className="mart-list">
            {far.map((mart) => (
              <MartRow key={mart.martId} mart={mart} />
            ))}
          </ul>
        </details>
      )}
    </>
  );
}
