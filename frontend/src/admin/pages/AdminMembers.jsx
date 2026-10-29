import { useState } from 'react';
import { api } from '../../api/client';
import ErrorNotice from '../../components/ErrorNotice';
import Pager from '../../components/Pager';
import { formatDateTime } from '../../labels';
import useLoad from '../useLoad';

const MEMBERSHIP_TYPE = { PAID: '유료', TRIAL: '체험' };

export default function AdminMembers() {
  const [filter, setFilter] = useState({ email: '', name: '', membershipActive: '' });
  const [page, setPage] = useState(0);
  const [message, setMessage] = useState(null);
  const [actionError, setActionError] = useState(null);
  const { data, error, reload } = useLoad(() => api.get('/api/admin/members', { ...filter, page, size: 20 }), [filter, page]);

  const setFilterValue = (key) => (e) => {
    setPage(0);
    setFilter((prev) => ({ ...prev, [key]: e.target.value }));
  };

  const grant = async (member) => {
    if (!window.confirm(`${member.name}(${member.email}) 회원에게 멤버십을 지급할까요?`)) return;
    setActionError(null);
    setMessage(null);
    try {
      const result = await api.post(`/api/admin/members/${member.memberId}/memberships`);
      setMessage(`멤버십을 지급했습니다. 종료 ${formatDateTime(result.endAt)}`);
      reload();
    } catch (e) {
      setActionError(e);
    }
  };

  return (
    <>
      <div className="admin__head">
        <h1 className="page__title">회원·멤버십</h1>
      </div>
      <div className="filters">
        <input className="field__input" placeholder="이메일" value={filter.email} onChange={setFilterValue('email')} />
        <input className="field__input" placeholder="이름" value={filter.name} onChange={setFilterValue('name')} />
        <select className="field__input" value={filter.membershipActive} onChange={setFilterValue('membershipActive')}>
          <option value="">멤버십 전체</option>
          <option value="true">이용 중</option>
          <option value="false">미이용</option>
        </select>
      </div>
      {message && <div className="notice notice--info">{message}</div>}
      <ErrorNotice error={error ?? actionError} />
      <div className="card table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>ID</th>
              <th>이메일</th>
              <th>이름</th>
              <th>멤버십</th>
              <th>종료 시각</th>
              <th>체험 사용</th>
              <th>가입일</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {data?.content.map((member) => (
              <tr key={member.memberId}>
                <td>{member.memberId}</td>
                <td>{member.email}</td>
                <td>{member.name}</td>
                <td>{member.membershipActive ? MEMBERSHIP_TYPE[member.membershipType] ?? '이용 중' : '-'}</td>
                <td>{formatDateTime(member.membershipEndAt)}</td>
                <td>{member.trialUsed ? '예' : '아니오'}</td>
                <td>{formatDateTime(member.createdAt)}</td>
                <td>
                  <button type="button" className="chip" onClick={() => grant(member)}>
                    멤버십 지급
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {data && data.content.length === 0 && <p className="state-message">회원이 없습니다.</p>}
      </div>
      {data && <Pager page={data.page} totalPages={data.totalPages} onChange={setPage} />}
    </>
  );
}
