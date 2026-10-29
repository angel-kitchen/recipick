import { useState } from 'react';
import { api } from '../../api/client';
import ErrorNotice from '../../components/ErrorNotice';
import Pager from '../../components/Pager';
import { AUDIT_ACTION, AUDIT_TARGET, formatDateTime } from '../../labels';
import useLoad from '../useLoad';

function AuditDetail({ id }) {
  const { data, error } = useLoad(() => api.get(`/api/admin/audit-logs/${id}`), [id]);
  if (error) return <ErrorNotice error={error} />;
  if (!data) return <p className="state-message">불러오는 중입니다.</p>;
  return (
    <section className="card admin__section">
      <h2 className="tile__title">로그 {id} 상세</h2>
      <pre className="json">{JSON.stringify(data, null, 2)}</pre>
    </section>
  );
}

export default function AdminAudit() {
  const [targetType, setTargetType] = useState('');
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState(null);
  const { data, error } = useLoad(() => api.get('/api/admin/audit-logs', { targetType, page, size: 20 }), [targetType, page]);

  return (
    <>
      <div className="admin__head">
        <h1 className="page__title">감사 로그</h1>
      </div>
      <div className="filters">
        <select
          className="field__input"
          value={targetType}
          onChange={(e) => {
            setPage(0);
            setTargetType(e.target.value);
          }}
        >
          <option value="">대상 전체</option>
          {Object.entries(AUDIT_TARGET).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      </div>
      <ErrorNotice error={error} />
      {selected && <AuditDetail id={selected} />}
      <div className="card table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>ID</th>
              <th>관리자 ID</th>
              <th>행위</th>
              <th>대상</th>
              <th>대상 ID</th>
              <th>마트 ID</th>
              <th>시각</th>
            </tr>
          </thead>
          <tbody>
            {data?.content.map((log) => (
              <tr key={log.id} className="is-clickable" onClick={() => setSelected(log.id)}>
                <td>{log.id}</td>
                <td>{log.adminId}</td>
                <td>{AUDIT_ACTION[log.action] ?? log.action}</td>
                <td>{AUDIT_TARGET[log.targetType] ?? log.targetType}</td>
                <td>{log.targetId ?? '-'}</td>
                <td>{log.martId ?? '-'}</td>
                <td>{formatDateTime(log.createdAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {data && data.content.length === 0 && <p className="state-message">기록이 없습니다.</p>}
      </div>
      {data && <Pager page={data.page} totalPages={data.totalPages} onChange={setPage} />}
    </>
  );
}
