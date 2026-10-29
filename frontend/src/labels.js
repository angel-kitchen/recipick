// 서버 enum 값을 화면 글자로 바꾼다
export const CUISINE = { KOREAN: '한식', WESTERN: '양식' };

export const DISH_TYPE = {
  SOUP: '국',
  STEW: '찌개',
  STIR_FRY: '볶음',
  RICE: '밥',
  NOODLE: '면',
  SIDE: '반찬',
  PASTA: '파스타',
  ETC: '기타',
};

export const DIFFICULTY = { EASY: '쉬움', NORMAL: '보통', HARD: '어려움' };

export const SALE_UNIT = { G: 'g', ML: 'ml', EA: '개' };

// 장바구니 항목이 AVAILABLE이 아닐 때 이유를 알려 준다
export const CART_STATUS = {
  PREVIOUS_MART: '이전 마트 상품',
  DELETED: '삭제된 상품',
  STOPPED: '판매 중지',
  SOLD_OUT: '품절',
  INSUFFICIENT_STOCK: '재고 부족',
};

export const UNAVAILABLE_REASON = {
  NOT_SOLD_IN_MART: '이 마트에서 팔지 않음',
  SALE_STOPPED: '판매 중지',
  SOLD_OUT: '품절',
  INSUFFICIENT_STOCK: '재고 부족',
};

export const UNORDERABLE_REASON = {
  MART_NOT_SELECTED: '이용 마트가 정해지지 않았습니다.',
  MART_NOT_OPEN: '마트가 현재 운영 중이 아닙니다.',
  DELIVERY_UNAVAILABLE: '이 주소로는 배송할 수 없습니다.',
  HAS_PREVIOUS_MART_ITEMS: '이전 마트 상품이 담겨 있습니다. 삭제 후 다시 시도해 주세요.',
  NO_PURCHASABLE_ITEMS: '주문할 수 있는 상품이 없습니다.',
  PENDING_ORDER_EXISTS: '결제 대기 중인 주문이 있습니다.',
};

export const ORDER_STATUS = {
  PENDING_PAYMENT: '결제 대기',
  PAID: '결제 완료',
  CANCELED: '취소됨',
  CANCEL_REQUESTED: '취소 요청',
  REFUNDED: '환불됨',
};

export const formatWon = (amount) => `${Number(amount).toLocaleString('ko-KR')}원`;

// 판매 단위 수치는 소수 문자열이라 불필요한 0을 지운다
export const formatAmount = (amount, unit) => `${Number(amount)}${SALE_UNIT[unit] ?? ''}`;

// 관리자 화면용
export const ADMIN_ROLE = { SUPER_ADMIN: '서비스 운영자', MART_ADMIN: '마트 관리자' };
export const MART_STATUS = { OPEN: '운영 중', CLOSED: '운영 중지' };
export const ORDER_MART_STATUS = {
  NONE: '대기',
  RECEIVED: '접수',
  PREPARING: '준비 중',
  SHIPPING: '배송 중',
  DELIVERED: '배송 완료',
  REJECTED: '거절',
};
export const PRODUCT_STATUS = { SELLING: '판매 중', STOPPED: '판매 중지' };
export const RECIPE_STATUS = { ACTIVE: '공개', INACTIVE: '비공개' };
export const PAYMENT_STATUS = {
  IN_PROGRESS: '진행 중',
  PAID: '결제 완료',
  FAILED: '실패',
  CANCEL_REQUESTED: '취소 요청',
  CANCELED: '취소됨',
};

export const formatDateTime = (value) => (value ? String(value).slice(0, 16).replace('T', ' ') : '-');

// 재고 조정 사유. 서버는 문자열로 받으므로 선택한 문구를 그대로 보낸다
export const STOCK_REASON = {
  RECEIVING: '재고 입고',
  RETURN: '반품 입고',
  COUNT_DIFF: '재고 실사 차이',
  MISTAKE: '단순 실수 정정',
  DAMAGED: '파손·폐기',
  OTHER: '기타',
};

export const AUDIT_TARGET = {
  MEMBER: '회원',
  MEMBERSHIP: '멤버십',
  MART: '마트',
  CATEGORY: '카테고리',
  PRODUCT: '상품',
  RECIPE: '레시피',
  INGREDIENT: '재료',
  ORDER: '주문',
  PAYMENT: '결제',
};

export const AUDIT_ACTION = {
  MEMBERSHIP_GRANT: '멤버십 지급',
  MEMBER_LIST_VIEW: '회원 목록 조회',
  MEMBER_MEMBERSHIP_HISTORY_VIEW: '멤버십 이력 조회',
  MART_CREATE: '마트 등록',
  MART_UPDATE: '마트 수정',
  MART_STATUS_CHANGE: '마트 상태 변경',
  CATEGORY_CREATE: '카테고리 등록',
  CATEGORY_UPDATE: '카테고리 수정',
  CATEGORY_DELETE: '카테고리 삭제',
  PRODUCT_CREATE: '상품 등록',
  PRODUCT_UPDATE: '상품 수정',
  PRODUCT_DELETE: '상품 삭제',
  PRODUCT_PRICE_CHANGE: '상품 가격 변경',
  PRODUCT_STATUS_CHANGE: '상품 판매 상태 변경',
  PRODUCT_INGREDIENT_CHANGE: '상품 재료 연결 변경',
  STOCK_ADJUST: '재고 조정',
  RECIPE_CREATE: '레시피 등록',
  RECIPE_UPDATE: '레시피 수정',
  RECIPE_STATUS_CHANGE: '레시피 공개 상태 변경',
  RECIPE_INGREDIENTS_CHANGE: '레시피 재료 변경',
  RECIPE_AUTO_CART_CHANGE: '자동 장바구니 설정 변경',
  INGREDIENT_CREATE: '재료 등록',
  INGREDIENT_UPDATE: '재료 수정',
  ORDER_ACCEPT: '주문 접수',
  ORDER_REJECT: '주문 거절',
  ORDER_DELIVERY_CHANGE: '배송 상태 변경',
  ORDER_LIST_VIEW: '주문 목록 조회',
  ORDER_DETAIL_VIEW: '주문 상세 조회',
  REFUND_RETRY: '환불 재시도',
  PAYMENT_LIST_VIEW: '결제 목록 조회',
  PAYMENT_DETAIL_VIEW: '결제 상세 조회',
  POINT_BALANCE_VIEW: '포인트 잔액 조회',
  POINT_HISTORY_VIEW: '포인트 이력 조회',
};
