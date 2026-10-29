import { api } from './client';

export async function hasPreviousMartItems() {
  const cart = await api.get('/api/cart-items');
  return cart.hasPreviousMartItems;
}

export async function clearPreviousMartItems() {
  const cart = await api.get('/api/cart-items');
  const ids = cart.items.filter((item) => item.status === 'PREVIOUS_MART').map((item) => item.cartItemId);
  if (ids.length > 0) await api.post('/api/cart-items/bulk-delete', { cartItemIds: ids });
}
