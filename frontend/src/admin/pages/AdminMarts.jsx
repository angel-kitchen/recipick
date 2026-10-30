import { useState } from 'react';
import { api } from '../../api/client';
import ErrorNotice from '../../components/ErrorNotice';
import { MART_STATUS } from '../../labels';
import { useAdmin } from '../AdminContext';
import useLoad from '../useLoad';

function MartForm({ mart, onDone, onCancel }) {
  const [form, setForm] = useState({
    latitude: String(mart.latitude),
    longitude: String(mart.longitude),
    serviceRadiusM: String(mart.serviceRadiusM ?? ''),
    status: mart.status,
  });
  const [error, setError] = useState(null);
  const change = (key) => (e) => setForm((prev) => ({ ...prev, [key]: e.target.value }));

  const submit = async (e) => {
    e.preventDefault();
    setError(null);
    try {
      await api.patch(`/api/admin/marts/${mart.martId}`, {
        latitude: form.latitude,
        longitude: form.longitude,
        serviceRadiusM: form.serviceRadiusM ? Number(form.serviceRadiusM) : null,
        status: form.status,
      });
      onDone();
    } catch (err) {
      setError(err);
    }
  };

  return (
    <form className="card form admin__section" onSubmit={submit}>
      <h2 className="tile__title">{mart.name} 수정</h2>
      <ErrorNotice error={error} />
      <div className="form-grid">
        <label className="field">
          <span className="field__label">위도</span>
          <input className="field__input" value={form.latitude} onChange={change('latitude')} required />
        </label>
        <label className="field">
          <span className="field__label">경도</span>
          <input className="field__input" value={form.longitude} onChange={change('longitude')} required />
        </label>
        <label className="field">
          <span className="field__label">서비스 반경(m)</span>
          <input className="field__input" inputMode="numeric" value={form.serviceRadiusM} onChange={change('serviceRadiusM')} />
          <span className="field__hint">지도 표시용 값입니다. 배송 가능 여부는 지역(법정동)으로 판정합니다.</span>
        </label>
        <label className="field">
          <span className="field__label">운영 상태</span>
          <select className="field__input" value={form.status} onChange={change('status')}>
            {Object.entries(MART_STATUS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
      </div>
      <div className="row-actions" style={{ marginTop: 0 }}>
        <button type="submit" className="btn">
          저장
        </button>
        <button type="button" className="btn btn--outline" onClick={onCancel}>
          취소
        </button>
      </div>
    </form>
  );
}

function MartCreateForm({ onDone, onCancel }) {
  const [form, setForm] = useState({ regionId: '', name: '', latitude: '', longitude: '', serviceRadiusM: '' });
  const [error, setError] = useState(null);
  const change = (key) => (e) => setForm((prev) => ({ ...prev, [key]: e.target.value }));

  const submit = async (e) => {
    e.preventDefault();
    setError(null);
    try {
      await api.post('/api/admin/marts', {
        regionId: Number(form.regionId),
        name: form.name,
        latitude: form.latitude,
        longitude: form.longitude,
        serviceRadiusM: form.serviceRadiusM ? Number(form.serviceRadiusM) : null,
      });
      onDone();
    } catch (err) {
      setError(err);
    }
  };

  return (
    <form className="card form admin__section" onSubmit={submit}>
      <h2 className="tile__title">마트 등록</h2>
      <ErrorNotice error={error} />
      <div className="form-grid">
        <label className="field">
          <span className="field__label">이름</span>
          <input className="field__input" value={form.name} onChange={change('name')} maxLength={50} required />
        </label>
        <label className="field">
          <span className="field__label">지역 ID</span>
          <input className="field__input" inputMode="numeric" value={form.regionId} onChange={change('regionId')} required />
          <span className="field__hint">배송 가능 여부를 판정하는 지역(법정동)의 ID입니다.</span>
        </label>
        <label className="field">
          <span className="field__label">위도</span>
          <input className="field__input" value={form.latitude} onChange={change('latitude')} required />
        </label>
        <label className="field">
          <span className="field__label">경도</span>
          <input className="field__input" value={form.longitude} onChange={change('longitude')} required />
        </label>
        <label className="field">
          <span className="field__label">서비스 반경(m)</span>
          <input className="field__input" inputMode="numeric" value={form.serviceRadiusM} onChange={change('serviceRadiusM')} />
        </label>
      </div>
      <div className="row-actions" style={{ marginTop: 0 }}>
        <button type="submit" className="btn">
          등록
        </button>
        <button type="button" className="btn btn--outline" onClick={onCancel}>
          취소
        </button>
      </div>
    </form>
  );
}

export default function AdminMarts() {
  const { data, error, reload } = useLoad(() => api.get('/api/admin/marts'), []);
  const [editing, setEditing] = useState(null);
  const [creating, setCreating] = useState(false);
  const { admin } = useAdmin();
  // 서버가 마트 등록을 SUPER_ADMIN에게만 허용한다
  const canCreate = admin.role === 'SUPER_ADMIN';

  return (
    <>
      <div className="admin__head">
        <h1 className="page__title">마트</h1>
        {canCreate && (
          <button type="button" className="btn" onClick={() => setCreating(true)}>
            마트 등록
          </button>
        )}
      </div>
      <ErrorNotice error={error} />
      {canCreate && creating && (
        <MartCreateForm
          onDone={() => {
            setCreating(false);
            reload();
          }}
          onCancel={() => setCreating(false)}
        />
      )}
      {editing && (
        <MartForm
          key={editing.martId}
          mart={editing}
          onDone={() => {
            setEditing(null);
            reload();
          }}
          onCancel={() => setEditing(null)}
        />
      )}
      <div className="card table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>ID</th>
              <th>이름</th>
              <th>위도</th>
              <th>경도</th>
              <th>반경(m)</th>
              <th>상태</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {data?.map((mart) => (
              <tr key={mart.martId}>
                <td>{mart.martId}</td>
                <td>{mart.name}</td>
                <td>{mart.latitude}</td>
                <td>{mart.longitude}</td>
                <td>{mart.serviceRadiusM}</td>
                <td>{MART_STATUS[mart.status]}</td>
                <td>
                  <button type="button" className="chip" onClick={() => setEditing(mart)}>
                    수정
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
