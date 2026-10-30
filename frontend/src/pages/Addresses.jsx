import { useCallback, useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import ErrorNotice from '../components/ErrorNotice';

// 시드 데이터(data.sql)의 시연 지역. 법정동 코드를 외우지 않아도 눌러서 채울 수 있게 한다
const DEMO_REGIONS = [
  { name: '서울 종로구 삼청동', code: '1111014000' },
  { name: '경기 광명시 하안동', code: '4121010300' },
];

const EMPTY = { label: '', address: '', detailAddress: '', legalDongCode: '' };

// 카카오(다음) 우편번호 검색. 키 없이 쓰는 공개 스크립트이고 결과에 법정동 코드(bcode)가 들어 있다
let postcodeLoading = null;
function loadPostcode() {
  if (window.daum?.Postcode) return Promise.resolve();
  if (!postcodeLoading) {
    postcodeLoading = new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = 'https://t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js';
      script.onload = resolve;
      script.onerror = () => {
        postcodeLoading = null;
        reject(new Error('주소 검색을 불러오지 못했습니다. 네트워크를 확인해 주세요.'));
      };
      document.head.appendChild(script);
    });
  }
  return postcodeLoading;
}

function AddressForm({ initial, submitLabel, showSelect, onSubmit, onCancel }) {
  const { member } = useAuth();
  const [form, setForm] = useState(initial);
  const [select, setSelect] = useState(true);
  const [locationAgreed, setLocationAgreed] = useState(Boolean(member.locationAgreed));
  const [area, setArea] = useState(null);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const change = (key) => (event) => setForm((prev) => ({ ...prev, [key]: event.target.value }));

  // 법정동 코드는 서버가 요구하는 값이라 화면에 보이지 않게 주소 검색 결과에서 받아 온다
  const openPostcode = async () => {
    setError(null);
    try {
      await loadPostcode();
      new window.daum.Postcode({
        oncomplete: (data) =>
          setForm((prev) => ({
            ...prev,
            address: data.roadAddress || data.jibunAddress,
            legalDongCode: data.bcode,
          })),
      }).open();
    } catch (e) {
      setError(e);
    }
  };

  const pickRegion = (region) => setForm((prev) => ({ ...prev, address: region.name, legalDongCode: region.code }));

  // 주소가 정해지면 바로 배송 가능 여부를 보여 준다
  useEffect(() => {
    setArea(null);
    if (form.legalDongCode.length !== 10) return undefined;
    let ignore = false;
    api
      .post('/api/service-area/check', { legalDongCode: form.legalDongCode })
      .then((data) => !ignore && setArea(data))
      .catch(() => {});
    return () => {
      ignore = true;
    };
  }, [form.legalDongCode]);

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await onSubmit(form, { select, locationAgreed });
    } catch (e) {
      setError(e);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form className="form" onSubmit={handleSubmit}>
      <ErrorNotice error={error} />
      <label className="field">
        <span className="field__label">별칭</span>
        <input className="field__input" value={form.label} onChange={change('label')} maxLength={30} placeholder="집, 회사" />
      </label>
      <div className="field">
        <span className="field__label">주소</span>
        <div className="filters" style={{ marginBottom: 0 }}>
          <input
            className="field__input"
            style={{ flex: 1 }}
            value={form.address}
            readOnly
            placeholder="주소 찾기를 눌러 검색하세요"
            required
          />
          <button type="button" className="btn btn--outline" onClick={openPostcode}>
            주소 찾기
          </button>
        </div>
        <span className="chips">
          {DEMO_REGIONS.map((region) => (
            <button key={region.code} type="button" className="chip" onClick={() => pickRegion(region)}>
              {region.name} (시연)
            </button>
          ))}
        </span>
        {area && (
          <span className={area.available ? 'field__hint' : 'error-text'}>
            {area.available
              ? `배송 가능 지역입니다. 이용 마트: ${area.martName}`
              : '아직 서비스 지역이 아닙니다. 저장은 할 수 있지만 지금은 재료 담기와 주문을 이용할 수 없어요.'}
          </span>
        )}
      </div>
      <label className="field">
        <span className="field__label">상세 주소</span>
        <input className="field__input" value={form.detailAddress} onChange={change('detailAddress')} maxLength={255} required />
      </label>
      {showSelect && (
        <>
          <label className="check">
            <input type="checkbox" checked={select} onChange={(e) => setSelect(e.target.checked)} />
            현재 배송지로 선택
          </label>
          <label className="check">
            <input type="checkbox" checked={locationAgreed} onChange={(e) => setLocationAgreed(e.target.checked)} />
            위치 정보 이용에 동의합니다. (선택)
          </label>
        </>
      )}
      <div className="row-actions">
        <button type="submit" className="btn" disabled={submitting}>
          {submitting ? '저장 중...' : submitLabel}
        </button>
        {onCancel && (
          <button type="button" className="btn btn--outline" onClick={onCancel}>
            취소
          </button>
        )}
      </div>
    </form>
  );
}

export default function Addresses() {
  const { member, reloadMember } = useAuth();
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const location = useLocation();
  const navigate = useNavigate();
  // 로그인 직후 배송지가 없어서 넘어온 경우. 등록 폼을 바로 열고, 끝나거나 건너뛰면 가려던 곳으로 보낸다
  const welcome = Boolean(location.state?.welcome);
  const next = location.state?.next ?? '/recipes';
  const [adding, setAdding] = useState(welcome);
  const [editingId, setEditingId] = useState(null);
  const [deletingId, setDeletingId] = useState(null);
  const [message, setMessage] = useState(null);

  const load = useCallback(async () => {
    try {
      setData(await api.get('/api/members/me/addresses'));
    } catch (e) {
      setError(e);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  // 배송지가 바뀌면 이용 마트도 바뀔 수 있어 회원 정보를 다시 읽는다
  const refresh = async () => {
    await Promise.all([load(), reloadMember()]);
  };

  const act = async (action, successMessage) => {
    setError(null);
    setMessage(null);
    try {
      const result = await action();
      await refresh();
      if (result?.martChanged) {
        setMessage(`이용 마트가 ${result.mart?.name}(으)로 바뀌었습니다. 이전 마트 상품은 장바구니에서 삭제해야 합니다.`);
      } else if (successMessage) {
        setMessage(successMessage);
      }
    } catch (e) {
      setError(e);
    }
  };

  const create = async (form, { select, locationAgreed }) => {
    const result = await api.post('/api/members/me/addresses', { ...form, select, locationAgreed });
    await refresh();
    if (welcome) {
      setAdding(false);
      navigate(next, { replace: true });
      return;
    }
    setAdding(false);
    setMessage(
      result.martChanged
        ? `이용 마트가 ${result.mart?.name}(으)로 바뀌었습니다.`
        : '배송지를 등록했습니다.',
    );
  };

  const update = (addressId) => async (form) => {
    await api.patch(`/api/members/me/addresses/${addressId}`, form);
    await refresh();
    setEditingId(null);
    setMessage('배송지를 수정했습니다.');
  };

  return (
    <div className="container page">
      <h1 className="page__title">배송지</h1>
      <p className="page__sub">
        {member.mart ? `이용 마트: ${member.mart.name}` : '선택된 배송지가 없어 이용 마트가 정해지지 않았습니다.'}
      </p>
      {welcome && (
        <div className="notice notice--info">
          환영해요! 먼저 배송지를 등록해 주세요. 서비스 지역인지 바로 알려 드려요.{' '}
          <Link to={next} onClick={() => setAdding(false)}>
            나중에 할게요
          </Link>
        </div>
      )}
      {message && <div className="notice notice--info">{message}</div>}
      <ErrorNotice error={error} />
      {!data && !error && <p className="state-message">불러오는 중입니다.</p>}

      {data && (
        <>
          <div className="stack">
            {data.addresses.length === 0 && <p className="state-message">등록된 배송지가 없습니다.</p>}
            {data.addresses.map((address) => (
              <section key={address.addressId} className="card">
                {editingId === address.addressId ? (
                  <AddressForm
                    initial={{
                      label: address.label ?? '',
                      address: address.address,
                      detailAddress: address.detailAddress,
                      legalDongCode: address.legalDongCode,
                    }}
                    submitLabel="수정 저장"
                    onSubmit={update(address.addressId)}
                    onCancel={() => setEditingId(null)}
                  />
                ) : (
                  <>
                    <div className="detail__facts" style={{ marginTop: 0 }}>
                      {address.label && <strong>{address.label}</strong>}
                      {address.selected && <span className="badge">현재 배송지</span>}
                      <span className={address.serviceAvailable ? 'badge' : 'badge badge--warn'}>
                        {address.serviceAvailable ? '배송 가능' : '서비스 지역 아님'}
                      </span>
                    </div>
                    <p style={{ margin: 0 }}>
                      {address.address} {address.detailAddress}
                    </p>
                    <div className="row-actions">
                      {!address.selected && (
                        <button
                          type="button"
                          className="btn"
                          onClick={() => act(() => api.put(`/api/members/me/addresses/${address.addressId}/select`))}
                        >
                          이 배송지 선택
                        </button>
                      )}
                      <button type="button" className="btn btn--outline" onClick={() => setEditingId(address.addressId)}>
                        수정
                      </button>
                      {deletingId === address.addressId ? (
                        <>
                          <button
                            type="button"
                            className="btn"
                            onClick={() =>
                              act(() => api.delete(`/api/members/me/addresses/${address.addressId}`), '배송지를 삭제했습니다.')
                            }
                          >
                            정말 삭제
                          </button>
                          <button type="button" className="btn btn--outline" onClick={() => setDeletingId(null)}>
                            취소
                          </button>
                        </>
                      ) : (
                        <button type="button" className="btn btn--outline" onClick={() => setDeletingId(address.addressId)}>
                          삭제
                        </button>
                      )}
                    </div>
                  </>
                )}
              </section>
            ))}
          </div>

          <section className="card" style={{ marginTop: 24 }}>
            {adding ? (
              <>
                <h2 className="tile__title">새 배송지</h2>
                <AddressForm initial={EMPTY} submitLabel="등록" showSelect onSubmit={create} onCancel={() => setAdding(false)} />
              </>
            ) : (
              <button
                type="button"
                className="btn"
                disabled={data.addresses.length >= data.maxCount}
                onClick={() => setAdding(true)}
              >
                배송지 추가 ({data.addresses.length}/{data.maxCount})
              </button>
            )}
          </section>
        </>
      )}
    </div>
  );
}
