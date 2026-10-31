import { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { kakaoAvailable } from '../address/kakao';
import { DEMO_REGIONS, openPostcode, useServiceArea } from '../address/postcode';
import { useAuth } from '../auth/AuthContext';
import ConfirmModal from '../components/ConfirmModal';
import ErrorNotice from '../components/ErrorNotice';
import LocationPickerModal from '../components/LocationPickerModal';
import { clearPendingAddress, readPendingAddress, registerAddress, savePendingAddress } from '../pendingAddress';

// 배송받을 동네(법정동)를 먼저 정하고 서비스 지역인지 확인한다. 이용 마트는 이 동네로 정해진다.
// - 비로그인: 확인한 주소를 이 탭에 보관하고 가입으로 보낸다. 로그인하면 Login이 그 주소를 배송지로 저장한다
// - 로그인했는데 배송지가 없음: 바로 배송지로 저장한다
export default function Start() {
  const { member, loading, reloadMember } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const next = location.state?.next ?? '/recipes';

  // 가입 화면에서 "변경"을 눌러 돌아왔다면 보관해 둔 주소를 채워 둔다
  const [pending] = useState(readPendingAddress);
  const [agreed, setAgreed] = useState(Boolean(pending) || Boolean(member?.locationAgreed));
  const [place, setPlace] = useState(
    pending ? { address: pending.address, legalDongCode: pending.legalDongCode, dongName: '' } : null,
  );
  const [detailAddress, setDetailAddress] = useState(pending?.detailAddress ?? '');
  // 로그인 직후 보관한 주소를 저장하지 못해서 돌아온 경우 그 이유를 먼저 보여 준다
  const [error, setError] = useState(() => (location.state?.notice ? new Error(location.state.notice) : null));
  const [submitting, setSubmitting] = useState(false);
  const [locating, setLocating] = useState(false);
  const [leaving, setLeaving] = useState(false);
  const { area, failed, retry } = useServiceArea(place?.legalDongCode);

  if (loading) return <p className="state-message">불러오는 중입니다.</p>;
  if (member?.currentAddress) return <Navigate to={next} replace />;

  const pick = (selected) => {
    setError(null);
    if (!/^\d{10}$/.test(selected.legalDongCode ?? '')) {
      setError(new Error('이 주소의 지역 정보를 찾지 못했습니다. 다른 주소로 검색해 주세요.'));
      return;
    }
    setPlace(selected);
  };

  const search = async () => {
    setError(null);
    try {
      await openPostcode(pick);
    } catch (e) {
      setError(e);
    }
  };

  const canStart = agreed && place && detailAddress.trim() !== '' && !submitting;

  const start = async (event) => {
    event.preventDefault();
    setError(null);
    const value = {
      address: place.address,
      detailAddress: detailAddress.trim(),
      legalDongCode: place.legalDongCode,
    };
    if (!member) {
      // 저장하지 못한 채 가입으로 넘어가면 로그인한 뒤 주소가 없어 이 화면으로 되돌아오므로 여기서 멈춘다
      if (!savePendingAddress(value)) {
        setError(new Error('가입 전 주소를 저장할 수 없습니다. 브라우저 저장소 설정을 확인한 뒤 다시 시도해 주세요.'));
        return;
      }
      navigate('/signup');
      return;
    }
    setSubmitting(true);
    try {
      await registerAddress(value);
      clearPendingAddress();
      // 배송지가 생기면 위의 Navigate가 원래 가려던 곳으로 보낸다
      await reloadMember();
    } catch (e) {
      setError(e);
      setSubmitting(false);
    }
  };

  return (
    <div className="container page">
      <div className="start">
        <div className="start__intro">
          <p className="start__eyebrow">START WITH YOUR NEIGHBORHOOD</p>
          <h1 className="page__title start__title">
            배송받을 동네부터
            <br />
            확인할게요
          </h1>
          <p className="start__lead">
            레시픽은 동네(법정동)마다 마트 한 곳이 배송해요.
            <br />
            주소를 먼저 정하면 그 마트의 가격과 재고로 장을 볼 수 있어요.
          </p>

          <div className="card start__consent">
            <label className="check">
              <input type="checkbox" checked={agreed} onChange={(e) => setAgreed(e.target.checked)} />
              <strong>위치 정보 이용 동의 (필수)</strong>
            </label>
            <p className="field__hint">현재 위치는 주소를 찾는 데만 쓰고 저장하지 않아요.</p>
            <details className="start__terms">
              <summary>내용 보기</summary>
              <ul className="list-plain">
                <li>이용 목적: 배송 가능한 지역인지 판정하고 이용 마트를 정합니다.</li>
                <li>이용 항목: 선택한 주소의 법정동</li>
                <li>보관: 현재 위치 자체는 저장하지 않고, 동의한 시각과 확정한 배송지, 이용 마트만 저장합니다.</li>
              </ul>
            </details>
          </div>

          <div className="row-actions">
            <button
              type="button"
              className="btn btn--large"
              disabled={!agreed || !kakaoAvailable}
              title={kakaoAvailable ? undefined : '지금은 현재 위치 기능을 사용할 수 없어요'}
              onClick={() => setLocating(true)}
            >
              현재 위치로 찾기
            </button>
            <button type="button" className="btn btn--large btn--outline" disabled={!agreed} onClick={search}>
              주소 직접 검색
            </button>
          </div>
          <span className="chips">
            {DEMO_REGIONS.map((region) => (
              <button
                key={region.code}
                type="button"
                className="chip"
                disabled={!agreed}
                onClick={() => pick({ address: region.name, legalDongCode: region.code, dongName: region.dongName })}
              >
                {region.name} (시연)
              </button>
            ))}
          </span>
        </div>

        <form className="card start__result" onSubmit={start}>
          <ErrorNotice error={error} />
          <p className="start__label">확인한 주소</p>
          {place ? (
            <>
              <p className="start__address">{place.address}</p>
              {place.dongName && <p className="field__hint">법정동 {place.dongName}</p>}
            </>
          ) : (
            <p className="field__hint">주소를 선택하면 배송 가능 여부를 알려 드려요.</p>
          )}

          {place && area?.available && (
            <div className="start__available">
              <strong>배송 가능</strong> {area.martName}에서 배송해요
            </div>
          )}
          {place && area && !area.available && (
            <div className="notice notice--info">
              아직 서비스 지역이 아니에요. 가입하고 레시피는 둘러볼 수 있지만 재료 담기와 주문은 할 수 없어요.
            </div>
          )}
          {place && !area && !failed && <p className="field__hint">배송 가능 여부를 확인하고 있어요.</p>}
          {place && failed && (
            <div className="notice notice--info notice--action">
              <span>배송 가능 여부를 확인하지 못했어요.</span>
              <button type="button" className="btn btn--muted" onClick={retry}>
                다시 확인
              </button>
            </div>
          )}

          <label className="field">
            <span className="field__label">상세 주소</span>
            <input
              className="field__input"
              value={detailAddress}
              onChange={(e) => setDetailAddress(e.target.value)}
              maxLength={255}
              placeholder="동·호수 등"
              disabled={!place}
            />
          </label>
          <button type="submit" className="btn btn--large btn--block" disabled={!canStart}>
            {submitting ? '저장 중...' : '이 주소로 시작하기'}
          </button>
          {member ? (
            <p className="auth-card__footer">
              <button type="button" onClick={() => setLeaving(true)}>
                나중에 할게요
              </button>
            </p>
          ) : (
            <p className="auth-card__footer">
              이미 회원이신가요? <Link to="/login">로그인</Link>
            </p>
          )}
        </form>
      </div>
      {leaving && (
        <ConfirmModal
          title="배송지 등록을 나중에 하시겠어요?"
          confirmLabel="지금 등록하기"
          cancelLabel="나중에 할게요"
          onConfirm={() => setLeaving(false)}
          onCancel={() => navigate('/recipes')}
          onClose={() => setLeaving(false)}
        >
          배송지를 등록하기 전에는 레시피 조회 이외의 기능은 이용할 수 없어요.
          주소를 지정하고 장보기를 바로 시작하세요!
        </ConfirmModal>
      )}
      {locating && (
        <LocationPickerModal
          onClose={() => setLocating(false)}
          onConfirm={(selected) => {
            setLocating(false);
            pick(selected);
          }}
        />
      )}
    </div>
  );
}
