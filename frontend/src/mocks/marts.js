// GET /api/config/marts가 머지되면 KoreaMap이 이 데이터 대신 API 응답을 쓴다. 응답 모양을 같게 맞춰 둔 임시 데이터.
export const mockMarts = [
  { martId: 1, name: '삼청 마트', latitude: 37.586, longitude: 126.982, serviceRadiusM: 2000, status: 'OPEN' },
  { martId: 2, name: '하안 마트', latitude: 37.471, longitude: 126.876, serviceRadiusM: 2000, status: 'OPEN' },
];
