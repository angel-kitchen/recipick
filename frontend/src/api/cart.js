import { api } from './client';

export async function hasPreviousMartItems() {
  const cart = await api.get('/api/cart-items');
  return cart.hasPreviousMartItems;
}

export async function clearPreviousMartItems() {
  const cart = await api.get('/api/cart-items');
  const ids = cart.items.filter((item) => item.status === 'PREVIOUS_MART').map((item) => item.cartItemId);
  // 한 번에 지울 수 있는 개수가 100개라 나눠서 보낸다
  for (let i = 0; i < ids.length; i += 100) {
    await api.post('/api/cart-items/bulk-delete', { cartItemIds: ids.slice(i, i + 100) });
  }
}
