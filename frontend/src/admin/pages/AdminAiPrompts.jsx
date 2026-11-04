import { useEffect, useState } from 'react';
import { api } from '../../api/client';
import ErrorNotice from '../../components/ErrorNotice';
import { AI_PROMPT_TYPE, formatDateTime } from '../../labels';
import useLoad from '../useLoad';

// 저장한 버전은 고칠 수 없어서, 편집칸에 기본 지시문이나 기존 버전을 불러와 고친 뒤 새 버전으로 저장한다.
// 저장만으로는 바뀌지 않고 활성화해야 다음 AI 요청부터 쓰인다
export default function AdminAiPrompts() {
  const [type, setType] = useState('MENU');
  const { data, error, reload } = useLoad(() => api.get('/api/admin/ai-prompts', { type }), [type]);
  const [draft, setDraft] = useState('');
  const [memo, setMemo] = useState('');
  const [opened, setOpened] = useState(null);
  const [actionError, setActionError] = useState(null);
  const [message, setMessage] = useState(null);
  const [saving, setSaving] = useState(false);

  // 종류를 바꾸면 그 종류에서 지금 쓰는 지시문으로 편집칸을 채운다
  useEffect(() => {
    if (!data || data.type !== type) return;
    const active = data.versions.find((version) => version.active);
    setDraft(active ? active.content : data.defaultContent);
    setMemo('');
    setOpened(null);
  }, [data, type]);

  const act = async (action, doneMessage) => {
    setActionError(null);
    setMessage(null);
    setSaving(true);
    try {
      await action();
      setMessage(doneMessage);
      reload();
      return true;
    } catch (e) {
      setActionError(e);
      return false;
    } finally {
      setSaving(false);
    }
  };

  const save = async (e) => {
    e.preventDefault();
    const saved = await act(
      () => api.post('/api/admin/ai-prompts', { type, content: draft, memo: memo.trim() || null }),
      '새 버전을 저장했어요. 활성화해야 AI 요청에 쓰여요.',
    );
    if (saved) setMemo('');
  };

  const activate = (version) => {
    if (!window.confirm(`버전 ${version.version}을 활성화할까요? 다음 AI 요청부터 이 지시문을 써요.`)) return;
    act(() => api.post(`/api/admin/ai-prompts/${version.promptId}/activate`), `버전 ${version.version}을 활성화했어요.`);
  };

  const reset = () => {
    if (!window.confirm('기본 지시문으로 되돌릴까요? 다음 AI 요청부터 코드에 있는 지시문을 써요.')) return;
    act(() => api.post(`/api/admin/ai-prompts/types/${type}/reset`), '기본 지시문으로 되돌렸어요.');
  };

  return (
    <>
      <div className="admin__head">
        <h1 className="page__title">AI 프롬프트</h1>
      </div>
      <div className="chips" style={{ marginBottom: 16 }}>
        {Object.entries(AI_PROMPT_TYPE).map(([value, label]) => (
          <button key={value} type="button" className={`chip ${type === value ? 'chip--solid' : ''}`} onClick={() => setType(value)}>
            {label}
          </button>
        ))}
      </div>
      <ErrorNotice error={error ?? actionError} />
      {message && <p className="field__hint">{message}</p>}
      {data && (
        <>
          <section className="card admin__section">
            <div className="row-actions" style={{ marginTop: 0, justifyContent: 'space-between' }}>
              <span className="tile__title">
                {data.activeVersion ? `버전 ${data.activeVersion} 사용 중` : '기본 지시문 사용 중'}
              </span>
              <button type="button" className="btn btn--outline btn--small" disabled={!data.activeVersion || saving} onClick={reset}>
                기본값으로 되돌리기
              </button>
            </div>
          </section>
          <form className="card admin__section stack" onSubmit={save}>
            <h2 className="tile__title">새 버전 작성</h2>
            <p className="field__hint">
              지시문 뒤에는 레시피·재료 목록이 자동으로 붙어요. 마지막 줄의 목록 머리말(예: &quot;레시피 목록(ID: 이름):&quot;)은 지우지
              마세요. 응답 형식은 코드가 정해서 지시문으로 바꿀 수 없어요.
            </p>
            <label className="field">
              <span className="field__label">지시문</span>
              <textarea className="field__input" rows={16} value={draft} maxLength={10000} onChange={(e) => setDraft(e.target.value)} required />
            </label>
            <label className="field">
              <span className="field__label">변경 메모 (선택)</span>
              <input className="field__input" value={memo} maxLength={200} placeholder="예: 예산 조건 추가" onChange={(e) => setMemo(e.target.value)} />
            </label>
            <div className="row-actions" style={{ marginTop: 0 }}>
              <button type="submit" className="btn" disabled={saving || !draft.trim()}>
                {saving ? '저장 중...' : '새 버전으로 저장'}
              </button>
              <button type="button" className="btn btn--outline" onClick={() => setDraft(data.defaultContent)}>
                기본 지시문 불러오기
              </button>
            </div>
          </form>
          <div className="card table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>버전</th>
                  <th>메모</th>
                  <th>저장 관리자</th>
                  <th>저장 시각</th>
                  <th>상태</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {data.versions.map((version) => (
                  <VersionRow
                    key={version.promptId}
                    version={version}
                    opened={opened === version.promptId}
                    saving={saving}
                    onToggle={() => setOpened(opened === version.promptId ? null : version.promptId)}
                    onLoad={() => setDraft(version.content)}
                    onActivate={() => activate(version)}
                  />
                ))}
              </tbody>
            </table>
            {data.versions.length === 0 && <p className="state-message">저장한 버전이 없어요. 기본 지시문을 쓰고 있어요.</p>}
          </div>
        </>
      )}
    </>
  );
}

function VersionRow({ version, opened, saving, onToggle, onLoad, onActivate }) {
  return (
    <>
      <tr>
        <td>{version.version}</td>
        <td>{version.memo ?? '-'}</td>
        <td>{version.createdByAdminId}</td>
        <td>{formatDateTime(version.createdAt)}</td>
        <td>{version.active ? '사용 중' : '-'}</td>
        <td>
          <div className="chips">
            <button type="button" className="chip" aria-expanded={opened} onClick={onToggle}>
              {opened ? '본문 닫기' : '본문 보기'}
            </button>
            <button type="button" className="chip" onClick={onLoad}>
              편집칸에 불러오기
            </button>
            {!version.active && (
              <button type="button" className="chip chip--solid" disabled={saving} onClick={onActivate}>
                활성화
              </button>
            )}
          </div>
        </td>
      </tr>
      {opened && (
        <tr>
          <td colSpan={6}>
            <pre className="json" style={{ whiteSpace: 'pre-wrap' }}>
              {version.content}
            </pre>
          </td>
        </tr>
      )}
    </>
  );
}
