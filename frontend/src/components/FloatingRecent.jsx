import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { readRecent } from '../recentViews';

const SHOWN = 4;

// 화면 오른쪽에 붙어서 스크롤을 따라다니는 "최근 본 상품·레시피". 넓은 화면(CSS)에서만 보인다.
// 목록은 서버가 아니라 이 브라우저에 저장된 값이고, 화면을 옮길 때마다 다시 읽는다
export default function FloatingRecent() {
  const { member } = useAuth();
  const { pathname } = useLocation();
  const [recent, setRecent] = useState({ product: [], recipe: [] });
  // 처음에는 둘 다 접어 두고, 누른 쪽만 펼친다
  const [opened, setOpened] = useState({});

  useEffect(() => {
    if (!member) return;
    setRecent({
      product: readRecent(member.memberId, 'product').slice(0, SHOWN),
      recipe: readRecent(member.memberId, 'recipe').slice(0, SHOWN),
    });
  }, [member, pathname]);

  if (!member || (recent.product.length === 0 && recent.recipe.length === 0)) return null;

  const group = (title, type, items, toPath) => {
    if (items.length === 0) return null;
    const open = Boolean(opened[type]);
    return (
      <section className="floating-recent__group" aria-label={title}>
        <h2 className="floating-recent__title">
          <button
            type="button"
            className="floating-recent__toggle"
            aria-expanded={open}
            onClick={() => setOpened((prev) => ({ ...prev, [type]: !prev[type] }))}
          >
            {title} {open ? '▴' : '▾'}
          </button>
        </h2>
        {open && (
          <ul className="floating-recent__list">
            {items.map((item) => (
              <li key={`${type}-${item.id}`}>
                <Link to={toPath(item.id)} className="floating-recent__item" title={item.name}>
                  {item.imageUrl ? <img src={item.imageUrl} alt="" /> : null}
                  <span>{item.name}</span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>
    );
  };

  return (
    <aside className="floating-recent" aria-label="최근 본 항목">
      {group('최근 본 레시피', 'recipe', recent.recipe, (id) => `/recipes/${id}`)}
      {group('최근 본 상품', 'product', recent.product, (id) => `/products/${id}`)}
    </aside>
  );
}
