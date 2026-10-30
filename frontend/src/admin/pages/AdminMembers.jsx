import { useState } from 'react';
import { api } from '../../api/client';
import ErrorNotice from '../../components/ErrorNotice';
import Pager from '../../components/Pager';
import { MEMBERSHIP_SOURCE, MEMBERSHIP_STATUS, formatDateTime } from '../../labels';
import useLoad from '../useLoad';

const MEMBERSHIP_TYPE = { PAID: '유료', TRIAL: '체험' };

function MembershipHistory({ member, version, onClose }) {
  const [page, setPage] = useState(0);
  const { data, error } = useLoad(
    () => api.get(`/api/admin/members/${member.memberId}/memberships`, { page, size: 10 }),
    [member.memberId, page, version],
  );

  return (
    <section className="card admin__section">
      <h2 className="tile__title">
        {member.name}({member.email}) 멤버십 이력
      </h2>
      <ErrorNotice error={error} />
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>ID</th>
              <th>구분</th>
              <th>경로</th>
              <th>상태</th>
              <th>시작</th>
              <th>종료</th>
              <th>자동 갱신</th>
              <th>해지 예약</th>
              <th>종료 처리</th>
              <th>지급 관리자</th>
            </tr>
          </thead>
          <tbody>
            {data?.content.map((item) => (
              <tr key={item.membershipId}>
                <td>{item.membershipId}</td>
                <td>{MEMBERSHIP_TYPE[item.type] ?? item.type}</td>
                <td>{MEMBERSHIP_SOURCE[item.source] ?? item.source}</td>
                <td>{MEMBERSHIP_STATUS[item.status] ?? item.status ?? '-'}</td>
                <td>{formatDateTime(item.startAt)}</td>
                <td>{formatDateTime(item.endAt)}</td>
                <td>{item.autoRenew ? '예' : '아니오'}</td>
                <td>{formatDateTime(item.cancelReservedAt)}</td>
                <td>{formatDateTime(item.terminatedAt)}</td>
                <td>{item.grantedByAdminId ?? '-'}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {data && data.content.length === 0 && <p className="state-message">멤버십 이력이 없습니다.</p>}
      </div>
      {data && <Pager page={data.page} totalPages={data.totalPages} onChange={setPage} />}
      <div className="row-actions">
        <button type="button" className="btn btn--outline" onClick={onClose}>
          닫기
        </button>
      </div>
    </section>
  );
}

export default function AdminMembers() {
  const [filter, setFilter] = useState({ email: '', name: '', membershipActive: '' });
  const [page, setPage] = useState(0);
  const [message, setMessage] = useState(null);
  const [actionError, setActionError] = useState(null);
  const [historyMember, setHistoryMember] = useState(null);
  // 지급하면 올려서, 열려 있는 이력도 새로 읽게 한다
  const [historyVersion, setHistoryVersion] = useState(0);
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
      setHistoryVersion((value) => value + 1);
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
      {historyMember && (
        <MembershipHistory
          key={historyMember.memberId}
          member={historyMember}
          version={historyVersion}
          onClose={() => setHistoryMember(null)}
        />
      )}
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
                  <button type="button" className="chip" onClick={() => setHistoryMember(member)}>
                    이력
                  </button>{' '}
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
