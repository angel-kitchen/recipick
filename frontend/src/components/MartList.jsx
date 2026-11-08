import { useEffect, useState } from 'react';
import { useAuth } from '../auth/AuthContext';

// 가맹 마트 목록(GET /api/config/marts, 로그인 불필요). 이용 가능한 곳인지는 사용자가 목록을 보고 직접 확인한다
export default function MartList() {
  const { member } = useAuth();
  const [marts, setMarts] = useState(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let ignore = false;
    fetch('/api/config/marts')
      .then((res) => {
        if (!res.ok) throw new Error(String(res.status));
        return res.json();
      })
      .then((json) => {
        if (!ignore) setMarts(json.data ?? []);
      })
      .catch(() => {
        if (!ignore) setFailed(true);
      });
    return () => {
      ignore = true;
    };
  }, []);

  if (failed) return <p className="field__hint">마트 목록을 불러오지 못했어요. 잠시 뒤에 다시 시도해 주세요.</p>;
  if (!marts) return <p className="field__hint">마트 목록을 불러오는 중이에요.</p>;
  if (marts.length === 0) return <p className="field__hint">표시할 마트가 아직 없어요.</p>;

  return (
    <ul className="mart-list">
      {marts.map((mart) => {
        const open = mart.status === 'OPEN';
        return (
          <li key={mart.martId} className="mart-list__item">
            <strong>{mart.name}</strong>
            <span className="mart-list__tags">
              {member?.mart?.martId === mart.martId && <span className="badge">내 이용 마트</span>}
              <span className={open ? 'badge' : 'badge badge--warn'}>{open ? '가맹 마트 운영 중' : '준비 중'}</span>
            </span>
          </li>
        );
      })}
    </ul>
  );
}
