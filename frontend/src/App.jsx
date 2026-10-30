import { Navigate, Route, Routes } from 'react-router-dom';
import AdminLayout from './admin/AdminLayout';
import AdminShell from './admin/AdminShell';
import AdminAiRequests from './admin/pages/AdminAiRequests';
import AdminAudit from './admin/pages/AdminAudit';
import AdminCategories from './admin/pages/AdminCategories';
import AdminIngredients from './admin/pages/AdminIngredients';
import AdminLogin from './admin/pages/AdminLogin';
import AdminMarts from './admin/pages/AdminMarts';
import AdminMembers from './admin/pages/AdminMembers';
import AdminOrders from './admin/pages/AdminOrders';
import AdminPayments from './admin/pages/AdminPayments';
import AdminProducts from './admin/pages/AdminProducts';
import AdminRecipes from './admin/pages/AdminRecipes';
import Layout from './components/Layout';
import ProtectedRoute from './components/ProtectedRoute';
import Addresses from './pages/Addresses';
import AutoCartHistory from './pages/AutoCartHistory';
import AutoCartPreview from './pages/AutoCartPreview';
import Bookmarks from './pages/Bookmarks';
import Cart from './pages/Cart';
import Checkout from './pages/Checkout';
import Home from './pages/Home';
import Login from './pages/Login';
import Membership from './pages/Membership';
import MyPage from './pages/MyPage';
import OrderDetail from './pages/OrderDetail';
import Orders from './pages/Orders';
import ProductDetail from './pages/ProductDetail';
import Products from './pages/Products';
import RecipeDetail from './pages/RecipeDetail';
import Recipes from './pages/Recipes';
import Signup from './pages/Signup';

export default function App() {
  return (
    <Routes>
      <Route path="/admin" element={<AdminShell />}>
        <Route path="login" element={<AdminLogin />} />
        <Route element={<AdminLayout />}>
          <Route index element={<Navigate to="orders" replace />} />
          <Route path="orders" element={<AdminOrders />} />
          <Route path="products" element={<AdminProducts />} />
          <Route path="marts" element={<AdminMarts />} />
          <Route path="recipes" element={<AdminRecipes />} />
          <Route path="ingredients" element={<AdminIngredients />} />
          <Route path="categories" element={<AdminCategories />} />
          <Route path="members" element={<AdminMembers />} />
          <Route path="payments" element={<AdminPayments />} />
          <Route path="ai-requests" element={<AdminAiRequests />} />
          <Route path="audit" element={<AdminAudit />} />
        </Route>
      </Route>
      <Route element={<Layout />}>
        <Route path="/" element={<Home />} />
        <Route path="/login" element={<Login />} />
        <Route path="/signup" element={<Signup />} />
        <Route element={<ProtectedRoute />}>
          <Route path="/recipes" element={<Recipes />} />
          <Route path="/recipes/:recipeId" element={<RecipeDetail />} />
          <Route path="/auto-carts" element={<AutoCartHistory />} />
          <Route path="/auto-carts/:autoCartId" element={<AutoCartPreview />} />
          <Route path="/bookmarks" element={<Bookmarks />} />
          <Route path="/mypage" element={<MyPage />} />
          <Route path="/membership" element={<Membership />} />
          <Route path="/addresses" element={<Addresses />} />
          <Route path="/products" element={<Products />} />
          <Route path="/products/:productId" element={<ProductDetail />} />
          <Route path="/cart" element={<Cart />} />
          <Route path="/checkout" element={<Checkout />} />
          <Route path="/orders" element={<Orders />} />
          <Route path="/orders/:orderId" element={<OrderDetail />} />
        </Route>
        <Route path="*" element={<p className="state-message">페이지를 찾을 수 없습니다.</p>} />
      </Route>
    </Routes>
  );
}
