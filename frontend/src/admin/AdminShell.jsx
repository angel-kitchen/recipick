import { Outlet } from 'react-router-dom';
import { AdminProvider } from './AdminContext';

export default function AdminShell() {
  return (
    <AdminProvider>
      <Outlet />
    </AdminProvider>
  );
}
