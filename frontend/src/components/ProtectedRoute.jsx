import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

export default function ProtectedRoute() {
  const { member, loading } = useAuth();
  const location = useLocation();

  if (loading) return <p className="state-message">불러오는 중입니다.</p>;
  if (!member) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  return <Outlet />;
}
