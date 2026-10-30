import { useState } from 'react';
import { api } from '../../api/client';
import ErrorNotice from '../../components/ErrorNotice';
import Pager from '../../components/Pager';
import { AI_REQUEST_TYPE, AI_RESULT_STATUS, formatDateTime } from '../../labels';
import useLoad from '../useLoad';

export default function AdminAiRequests() {
  const [filter, setFilter] = useState({ requestType: '', success: '', from: '', to: '' });
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState(null);
  const { data, error, loading } = useLoad(() => api.get('/api/admin/ai-requests', { ...filter, page, size: 20 }), [filter, page]);

  const setFilterValue = (key) => (e) => {
    setPage(0);
    setFilter((prev) => ({ ...prev, [key]: e.target.value }));
  };

  return (
    <>
      <div className="admin__head">
        <h1 className="page__title">AI 요청</h1>
      </div>
      <div className="filters">
        <select className="field__input" aria-label="요청 유형" value={filter.requestType} onChange={setFilterValue('requestType')}>
          <option value="">유형 전체</option>
          {Object.entries(AI_REQUEST_TYPE).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
        <select className="field__input" aria-label="성공 여부" value={filter.success} onChange={setFilterValue('success')}>
          <option value="">성공 여부 전체</option>
          <option value="true">성공</option>
          <option value="false">실패</option>
        </select>
        <input className="field__input" type="date" aria-label="시작일" value={filter.from} onChange={setFilterValue('from')} />
        <input className="field__input" type="date" aria-label="종료일" value={filter.to} onChange={setFilterValue('to')} />
      </div>
      <ErrorNotice error={error} />
      {selected && (
        <section className="card admin__section">
          <h2 className="tile__title">요청 {selected.aiRequestId} 상세</h2>
          <pre className="json">{JSON.stringify(selected, null, 2)}</pre>
          <div className="row-actions">
            <button type="button" className="btn btn--outline" onClick={() => setSelected(null)}>
              닫기
            </button>
          </div>
        </section>
      )}
      <div className="card table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>ID</th>
              <th>회원 ID</th>
              <th>유형</th>
              <th>입력 문장</th>
              <th>결과</th>
              <th>성공</th>
              <th>모델</th>
              <th>지연(ms)</th>
              <th>시각</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {!loading && data?.content.map((log) => (
              <tr key={log.aiRequestId} className="is-clickable" onClick={() => setSelected(log)}>
                <td>{log.aiRequestId}</td>
                <td>{log.memberId ?? '-'}</td>
                <td>{AI_REQUEST_TYPE[log.requestType] ?? log.requestType}</td>
                <td>{log.rawText}</td>
                <td>{AI_RESULT_STATUS[log.resultStatus] ?? log.resultStatus}</td>
                <td>{log.success ? '예' : '아니오'}</td>
                <td>{log.model ?? '-'}</td>
                <td>{log.latencyMs}</td>
                <td>{formatDateTime(log.createdAt)}</td>
                <td>
                  <button
                    type="button"
                    className="chip"
                    aria-label={`요청 ${log.aiRequestId} 상세 보기`}
                    onClick={(e) => {
                      e.stopPropagation();
                      setSelected(log);
                    }}
                  >
                    상세
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {loading && <p className="state-message">불러오는 중입니다.</p>}
        {!loading && data && data.content.length === 0 && <p className="state-message">기록이 없습니다.</p>}
      </div>
      {data && <Pager page={data.page} totalPages={data.totalPages} onChange={setPage} />}
    </>
  );
}
