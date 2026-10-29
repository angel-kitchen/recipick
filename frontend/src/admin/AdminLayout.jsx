import { useState } from 'react';
import { Link, NavLink, Navigate, Outlet } from 'react-router-dom';
import { ADMIN_ROLE } from '../labels';
import { useAdmin } from './AdminContext';

const SUPER = 'SUPER_ADMIN';
const ITEMS = [
  { to: '/admin/orders', label: '주문', roles: [SUPER, 'MART_ADMIN'] },
  { to: '/admin/products', label: '상품·재고', roles: [SUPER, 'MART_ADMIN'] },
  { to: '/admin/marts', label: '마트', roles: [SUPER, 'MART_ADMIN'] },
  { to: '/admin/recipes', label: '레시피', roles: [SUPER] },
  { to: '/admin/ingredients', label: '재료', roles: [SUPER] },
  { to: '/admin/categories', label: '카테고리', roles: [SUPER] },
  { to: '/admin/members', label: '회원·멤버십', roles: [SUPER] },
  { to: '/admin/payments', label: '결제', roles: [SUPER] },
  { to: '/admin/audit', label: '감사 로그', roles: [SUPER] },
];

export default function AdminLayout() {
  const { admin, loading, logout } = useAdmin();
  const [logoutError, setLogoutError] = useState(null);
  if (loading) return <p className="state-message">불러오는 중입니다.</p>;
  if (!admin) return <Navigate to="/admin/login" replace />;

  return (
    <div className="admin">
      <aside className="admin__side">
        <Link to="/admin" className="admin__brand">
          Recipick 관리자
        </Link>
        <nav className="admin__nav">
          {ITEMS.filter((item) => item.roles.includes(admin.role)).map((item) => (
            <NavLink key={item.to} to={item.to}>
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="admin__me">
          <div>{admin.loginId ?? `관리자 #${admin.adminId}`}</div>
          <div className="field__hint">{ADMIN_ROLE[admin.role] ?? admin.role}</div>
          {logoutError && <div className="error-text">{logoutError.message}</div>}
          <button
            type="button"
            className="btn btn--outline btn--small"
            onClick={() => {
              setLogoutError(null);
              logout().catch(setLogoutError);
            }}
          >
            로그아웃
          </button>
          <Link to="/" className="field__hint">
            서비스 화면으로
          </Link>
        </div>
      </aside>
      <main className="admin__main">
        <Outlet />
      </main>
    </div>
  );
}
