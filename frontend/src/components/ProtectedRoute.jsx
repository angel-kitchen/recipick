import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { ListSkeleton } from './Skeleton';

export default function ProtectedRoute() {
  const { member, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return (
      <div className="container page">
        <ListSkeleton rows={3} />
      </div>
    );
  }
  if (!member) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  return <Outlet />;
}
