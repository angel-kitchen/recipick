import { useCallback, useEffect, useState } from 'react';
import { kakaoAvailable } from '../address/kakao';
import { DEMO_REGIONS, openPostcode, useServiceArea } from '../address/postcode';
import { api } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import ErrorNotice from '../components/ErrorNotice';
import LocationPickerModal from '../components/LocationPickerModal';

const EMPTY = { label: '', address: '', detailAddress: '', legalDongCode: '' };

function AddressForm({ initial, submitLabel, showSelect, onSubmit, onCancel }) {
  const { member } = useAuth();
  const [form, setForm] = useState(initial);
  const [select, setSelect] = useState(true);
  const [locationAgreed, setLocationAgreed] = useState(Boolean(member.locationAgreed));
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [locating, setLocating] = useState(false);
  // 주소가 정해지면 바로 배송 가능 여부를 보여 준다
  const { area } = useServiceArea(form.legalDongCode);

  const change = (key) => (event) => setForm((prev) => ({ ...prev, [key]: event.target.value }));

  const search = async () => {
    setError(null);
    try {
      await openPostcode(({ address, legalDongCode }) => setForm((prev) => ({ ...prev, address, legalDongCode })));
    } catch (e) {
      setError(e);
    }
  };

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
          <button type="button" className="btn btn--outline" onClick={search}>
            주소 찾기
          </button>
          <button
            type="button"
            className="btn btn--outline"
            disabled={!locationAgreed || !kakaoAvailable}
            title={
              !kakaoAvailable
                ? '지금은 현재 위치 기능을 사용할 수 없어요'
                : !locationAgreed
                  ? '위치 정보 이용에 동의하면 쓸 수 있어요'
                  : undefined
            }
            onClick={() => setLocating(true)}
          >
            현재 위치로 찾기
          </button>
        </div>
        <span className="chips">
          {DEMO_REGIONS.map((region) => (
            <button
              key={region.code}
              type="button"
              className="chip"
              onClick={() => setForm((prev) => ({ ...prev, address: region.name, legalDongCode: region.code }))}
            >
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
          {!member.locationAgreed && (
            <label className="check">
              <input
                type="checkbox"
                checked={locationAgreed}
                onChange={(e) => setLocationAgreed(e.target.checked)}
                required
              />
              위치 정보 이용에 동의합니다. (필수)
            </label>
          )}
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
      {locating && (
        <LocationPickerModal
          onClose={() => setLocating(false)}
          onConfirm={({ address, legalDongCode }) => {
            setLocating(false);
            setForm((prev) => ({ ...prev, address, legalDongCode }));
          }}
        />
      )}
    </form>
  );
}

export default function Addresses() {
  const { member, reloadMember } = useAuth();
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [adding, setAdding] = useState(false);
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
