import { Fragment, useMemo, useState } from 'react';
import { api } from '../../api/client';
import ErrorNotice from '../../components/ErrorNotice';
import { AI_REQUEST_TYPE, AI_RESULT_STATUS, formatDateTime } from '../../labels';
import useLoad from '../useLoad';

// 서버는 요청 1건마다 회원 ID와 토큰을 주고 회원별 합계는 주지 않는다. 그래서 요청 이력을 읽어 와 화면에서 회원별로 합산한다.
// 이 API는 호출 1번마다 관리자 감사 기록을 쓰기 트랜잭션으로 남기므로(#266) 호출 수를 줄이려고 상한을 낮게 잡는다
const PAGE_SIZE = 100; // API가 허용하는 최대 크기
const MAX_PAGES = 20; // 최신 2,000건까지만 합산한다
const PARALLEL = 5;

const formatCount = (value) => (value ?? 0).toLocaleString('ko-KR');

async function loadRequests(filter) {
  const first = await api.get('/api/admin/ai-requests', { ...filter, page: 0, size: PAGE_SIZE });
  const lastPage = Math.min(first.totalPages, MAX_PAGES);
  const rows = [...first.content];
  // 첫 쪽에서 전체 쪽수를 알았으니 나머지는 몇 쪽씩 묶어서 함께 읽는다
  for (let start = 1; start < lastPage; start += PARALLEL) {
    const pages = [];
    for (let page = start; page < Math.min(start + PARALLEL, lastPage); page += 1) pages.push(page);
    const results = await Promise.all(pages.map((page) => api.get('/api/admin/ai-requests', { ...filter, page, size: PAGE_SIZE })));
    results.forEach((result) => rows.push(...result.content));
  }
  return { rows, total: first.totalElements, truncated: first.totalPages > MAX_PAGES };
}

function summarize(rows) {
  const byMember = new Map();
  rows.forEach((row) => {
    const key = row.memberId ?? 'none';
    if (!byMember.has(key)) {
      byMember.set(key, { memberId: row.memberId, requests: 0, failed: 0, input: 0, output: 0, cache: 0, logs: [] });
    }
    const entry = byMember.get(key);
    entry.requests += 1;
    if (!row.success) entry.failed += 1;
    // AI 응답을 받지 못한 요청은 토큰이 비어 있어서 0으로 센다
    entry.input += row.inputTokens ?? 0;
    entry.output += row.outputTokens ?? 0;
    entry.cache += row.cacheReadTokens ?? 0;
    entry.logs.push(row);
  });
  return [...byMember.values()].map((entry) => ({ ...entry, total: entry.input + entry.output }));
}

// 기본은 최근 7일(한국 시간, 오늘 포함)이다. 기간 없이 전체를 읽으면 호출 수와 감사 기록이 크게 늘기 때문이다
const seoulDate = (offsetDays = 0) =>
  new Date(Date.now() + offsetDays * 86400000).toLocaleDateString('sv-SE', { timeZone: 'Asia/Seoul' });
const defaultFilter = () => ({ requestType: '', from: seoulDate(-6), to: seoulDate() });

const SORTS = {
  total: { label: '총 토큰 많은 순', compare: (a, b) => b.total - a.total },
  requests: { label: '요청 수 많은 순', compare: (a, b) => b.requests - a.requests },
  member: { label: '회원 ID 순', compare: (a, b) => (a.memberId ?? Infinity) - (b.memberId ?? Infinity) },
};

export default function AdminAiUsage() {
  // 조건을 바꿀 때마다 읽으면 호출과 감사 기록이 쌓이므로, 입력 중인 조건(draft)은 "조회"를 눌러야 반영한다
  const [draft, setDraft] = useState(defaultFilter);
  const [filter, setFilter] = useState(draft);
  const [memberQuery, setMemberQuery] = useState('');
  const [sortKey, setSortKey] = useState('total');
  const [openId, setOpenId] = useState(null);
  const { data, error, loading } = useLoad(() => loadRequests(filter), [filter]);

  const setFilterValue = (key) => (e) => setDraft((prev) => ({ ...prev, [key]: e.target.value }));
  // 같은 조건이어도 새 객체로 바꿔 다시 읽는다(새로고침 역할)
  const search = () => {
    setOpenId(null);
    setFilter({ ...draft });
  };
  const rangeInvalid = Boolean(draft.from && draft.to && draft.from > draft.to);

  const members = useMemo(() => (data ? summarize(data.rows) : []), [data]);
  const visible = useMemo(() => {
    const query = memberQuery.trim();
    return members.filter((m) => !query || String(m.memberId ?? '').includes(query)).sort(SORTS[sortKey].compare);
  }, [members, memberQuery, sortKey]);
  const totals = useMemo(
    () =>
      members.reduce(
        (sum, m) => ({ requests: sum.requests + m.requests, input: sum.input + m.input, output: sum.output + m.output, cache: sum.cache + m.cache }),
        { requests: 0, input: 0, output: 0, cache: 0 },
      ),
    [members],
  );

  return (
    <>
      <div className="admin__head">
        <h1 className="page__title">AI 토큰 사용량</h1>
      </div>
      <div className="filters">
        <select className="field__input" aria-label="요청 유형" value={draft.requestType} onChange={setFilterValue('requestType')}>
          <option value="">유형 전체</option>
          {Object.entries(AI_REQUEST_TYPE).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
        <input className="field__input" type="date" aria-label="시작일" value={draft.from} onChange={setFilterValue('from')} />
        <input className="field__input" type="date" aria-label="종료일" value={draft.to} onChange={setFilterValue('to')} />
        <button type="button" className="btn" disabled={loading || rangeInvalid} onClick={search}>
          {loading ? '읽는 중...' : '조회'}
        </button>
        <input
          className="field__input"
          inputMode="numeric"
          placeholder="회원 ID 검색"
          aria-label="회원 ID 검색"
          value={memberQuery}
          onChange={(e) => setMemberQuery(e.target.value.replace(/\D/g, ''))}
        />
        <select className="field__input" aria-label="정렬" value={sortKey} onChange={(e) => setSortKey(e.target.value)}>
          {Object.entries(SORTS).map(([value, sort]) => (
            <option key={value} value={value}>
              {sort.label}
            </option>
          ))}
        </select>
      </div>
      <p className="field__hint" style={{ margin: '0 0 8px' }}>
        {rangeInvalid ? '시작일이 종료일보다 늦어요.' : '기간과 유형을 정하고 "조회"를 눌러 주세요. 조회할 때마다 관리자 감사 기록이 남아요.'}
      </p>
      <ErrorNotice error={error} />
      {data?.truncated && (
        <div className="notice" role="status">
          요청이 {formatCount(data.total)}건이라 최신 {formatCount(MAX_PAGES * PAGE_SIZE)}건만 합산했어요. 기간을 좁히면 전체를 볼 수 있어요.
        </div>
      )}
      {data && (
        <p className="field__hint" style={{ margin: '4px 0 12px' }}>
          회원 {formatCount(members.length)}명 · 요청 {formatCount(totals.requests)}건 · 입력 {formatCount(totals.input)} + 출력 {formatCount(totals.output)} = 총{' '}
          {formatCount(totals.input + totals.output)} 토큰 (캐시 읽기 {formatCount(totals.cache)}는 따로 표시)
        </p>
      )}
      <div className="card table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>회원 ID</th>
              <th>요청</th>
              <th>실패</th>
              <th>입력 토큰</th>
              <th>출력 토큰</th>
              <th>총 토큰</th>
              <th>캐시 읽기</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {!loading &&
              visible.map((member) => {
                const key = member.memberId ?? 'none';
                const opened = openId === key;
                return (
                  <Fragment key={key}>
                    <tr className="is-clickable" onClick={() => setOpenId(opened ? null : key)}>
                      <td>{member.memberId ?? '회원 없음'}</td>
                      <td>{formatCount(member.requests)}</td>
                      <td>{formatCount(member.failed)}</td>
                      <td>{formatCount(member.input)}</td>
                      <td>{formatCount(member.output)}</td>
                      <td>
                        <strong>{formatCount(member.total)}</strong>
                      </td>
                      <td>{formatCount(member.cache)}</td>
                      <td>
                        <button
                          type="button"
                          className="chip"
                          aria-expanded={opened}
                          aria-label={`회원 ${member.memberId ?? '없음'} 요청 내역 ${opened ? '접기' : '보기'}`}
                          onClick={(e) => {
                            e.stopPropagation();
                            setOpenId(opened ? null : key);
                          }}
                        >
                          {opened ? '접기' : '내역'}
                        </button>
                      </td>
                    </tr>
                    {opened && (
                      <tr>
                        <td colSpan={8}>
                          <table className="table">
                            <thead>
                              <tr>
                                <th>시각</th>
                                <th>유형</th>
                                <th>입력 문장</th>
                                <th>결과</th>
                                <th>입력</th>
                                <th>출력</th>
                                <th>캐시</th>
                              </tr>
                            </thead>
                            <tbody>
                              {member.logs.slice(0, 20).map((log) => (
                                <tr key={log.aiRequestId}>
                                  <td>{formatDateTime(log.createdAt)}</td>
                                  <td>{AI_REQUEST_TYPE[log.requestType] ?? log.requestType}</td>
                                  <td>{log.rawText}</td>
                                  <td>{AI_RESULT_STATUS[log.resultStatus] ?? log.resultStatus}</td>
                                  <td>{log.inputTokens == null ? '-' : formatCount(log.inputTokens)}</td>
                                  <td>{log.outputTokens == null ? '-' : formatCount(log.outputTokens)}</td>
                                  <td>{log.cacheReadTokens == null ? '-' : formatCount(log.cacheReadTokens)}</td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                          {member.logs.length > 20 && <p className="field__hint">최신 20건만 보여 줘요. 전체는 &quot;AI 요청&quot; 화면에서 볼 수 있어요.</p>}
                        </td>
                      </tr>
                    )}
                  </Fragment>
                );
              })}
          </tbody>
        </table>
        {loading && <p className="state-message">요청 이력을 읽어 합산하는 중입니다.</p>}
        {!loading && data && visible.length === 0 && <p className="state-message">조건에 맞는 기록이 없습니다.</p>}
      </div>
    </>
  );
}
