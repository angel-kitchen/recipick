import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { clearRecent, readRecent } from '../recentViews';

// 목록 화면 위에 "최근 본 레시피/상품"을 한 줄로 보여 준다. 본 적이 없으면 아무것도 그리지 않는다
export default function RecentViews({ type, title, toPath }) {
  const { member } = useAuth();
  const [items, setItems] = useState(() => readRecent(member.memberId, type));

  if (items.length === 0) return null;

  return (
    <section className="recent" aria-label={title}>
      <span className="recent__title">{title}</span>
      <span className="chips">
        {items.map((item) => (
          <Link key={item.id} to={toPath(item.id)} className="chip">
            {item.name}
          </Link>
        ))}
      </span>
      <button
        type="button"
        className="header__link-button recent__clear"
        onClick={() => {
          clearRecent(member.memberId, type);
          setItems([]);
        }}
      >
        지우기
      </button>
    </section>
  );
}
