# 주문 도메인 API 명세

레시픽(Recipick) · 주문 도메인(장바구니 · 주문 · 관리자 주문) · 기준: `feature/order-admin-accept` 브랜치 구현(2026-10-24)

## 공통

### 인증

| 구분 | 경로 | 권한 | 헤더 |
| --- | --- | --- | --- |
| 회원 API | `/api/cart-items`, `/api/orders` | MEMBER | `Authorization: Bearer {회원 accessToken}` |
| 관리자 API | `/api/admin/orders` | SUPER_ADMIN, MART_ADMIN | `Authorization: Bearer {관리자 accessToken}` |

- 회원 ID · 관리자 ID · 소속 마트 ID는 토큰에서 꺼내며 경로나 본문으로 받지 않습니다.
- 토큰이 없으면 401 `AUTH-001`, 회원 토큰으로 관리자 API를 호출하거나 그 반대면 403 `AUTH-004`로 거절합니다. 아래 API별 예외 표에서는 이 둘을 생략합니다.

### 응답 형식

성공

```json
{
  "success": true,
  "code": "OK",
  "message": "요청이 처리되었습니다.",
  "data": { }
}
```

실패

```json
{
  "success": false,
  "code": "CART-001",
  "message": "장바구니 상품을 찾을 수 없습니다.",
  "errors": [],
  "timestamp": "2026-10-14T21:30:00+09:00"
}
```

- 성공 응답의 `code`는 201 Created를 포함해 항상 `OK`입니다.
- `errors`는 입력값 검증 실패(`COMMON-001`)일 때만 `[{ "field": "quantity", "reason": "..." }]`로 채워지고, 그 밖에는 빈 배열입니다.
- 본문 JSON이 깨졌거나 타입이 맞지 않으면 400 `COMMON-002`입니다.

### 값 형식

- 시각: 한국 시간 ISO-8601 (`2026-10-14T21:30:00+09:00`)
- 금액: 원 단위 정수(Long)
- 판매 단위 수치(`saleUnitAmount`): 소수 문자열 (`"1000.00"`)
- enum: UPPER_SNAKE_CASE, JSON 필드: camelCase

### 목록(페이지) 규칙

- 요청: `page`(0부터, 기본 0), `size`(기본 20, 최대 100. 넘기면 100으로 잘림). `sort`는 받아도 무시하고 항상 `id` 내림차순(최신순)입니다.
- 응답 `data`: `{ content, page, size, totalElements, totalPages }`
- 장바구니는 페이지 없이 전체를 반환합니다.

### 상태 값

| 구분 | 값 | 설명 |
| --- | --- | --- |
| 주문 상태 `status` | PENDING_PAYMENT | 결제 대기(생성 직후) |
|  | PAID | 결제 완료 |
|  | CANCEL_REQUESTED | 취소 · 환불 진행 중(회원 취소 또는 마트 거절) |
|  | CANCELED | 결제 전 종료(결제 실패 · 5분 만료 · 결제 전 취소 · 금액 불일치) |
|  | REFUNDED | 환불 완료로 종료 |
| 마트 처리 상태 `martStatus` | NONE | 결제 전 |
|  | RECEIVED | 접수중(결제 완료) |
|  | PREPARING | 배송 준비중(마트 수락) |
|  | SHIPPING | 배송중 |
|  | DELIVERED | 배송 완료 |
|  | REJECTED | 마트 거절 |
| 취소 사유 `cancelReasonCode` | MEMBER_CANCELED | 회원 직접 취소 |
|  | PAYMENT_TIMEOUT | 결제 대기 5분 만료 |
|  | PAYMENT_FAILED | 결제 실패 확정 |
|  | AMOUNT_MISMATCH | 결제 금액 불일치 |
|  | MART_REJECTED | 마트 주문 거절 |
|  | EXTERNAL_CANCELED | PortOne 콘솔 등 시스템 밖에서 일어난 전액 취소 |

### API 목록

| # | API | Method / Path | 권한 |
| --- | --- | --- | --- |
| 1 | 장바구니 조회 | `GET /api/cart-items` | MEMBER |
| 2 | 장바구니 담기 | `POST /api/cart-items` | MEMBER |
| 3 | 장바구니 수량 변경 | `PATCH /api/cart-items/{cartItemId}` | MEMBER |
| 4 | 장바구니 개별 삭제 | `DELETE /api/cart-items/{cartItemId}` | MEMBER |
| 5 | 장바구니 선택 항목 삭제 | `POST /api/cart-items/bulk-delete` | MEMBER |
| 6 | 주문 미리보기 | `POST /api/orders/preview` | MEMBER |
| 7 | 주문 생성 | `POST /api/orders` | MEMBER |
| 8 | 주문 목록 조회 | `GET /api/orders` | MEMBER |
| 9 | 주문 상세 조회 | `GET /api/orders/{orderId}` | MEMBER |
| 10 | 관리자 주문 목록 조회 | `GET /api/admin/orders` | SUPER_ADMIN, MART_ADMIN |
| 11 | 관리자 주문 상세 조회 | `GET /api/admin/orders/{orderId}` | SUPER_ADMIN, MART_ADMIN |
| 12 | 주문 수락 | `POST /api/admin/orders/{orderId}/accept` | SUPER_ADMIN, MART_ADMIN |

---

## 1. 장바구니 조회

### API: 장바구니 조회

### Method / Path

`GET /api/cart-items`

### 인증

- 회원 인증 필요

| 헤더 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | O | Bearer {accessToken} |

### Request

없음

### Response `200 OK`

```json
{
  "success": true,
  "code": "OK",
  "message": "요청이 처리되었습니다.",
  "data": {
    "items": [
      {
        "cartItemId": 11,
        "productId": 101,
        "productName": "양파 1망",
        "imageUrl": "https://cdn.example.com/products/101.jpg",
        "price": 2000,
        "saleUnitAmount": "1000.00",
        "saleUnit": "G",
        "quantity": 2,
        "lineAmount": 4000,
        "stock": 30,
        "status": "AVAILABLE"
      },
      {
        "cartItemId": 12,
        "productId": 102,
        "productName": "국산 두부",
        "imageUrl": "https://cdn.example.com/products/102.jpg",
        "price": 3000,
        "saleUnitAmount": "300.00",
        "saleUnit": "G",
        "quantity": 1,
        "lineAmount": 3000,
        "stock": 0,
        "status": "SOLD_OUT"
      }
    ],
    "hasPreviousMartItems": false,
    "deliveryPolicy": {
      "freeDeliveryThreshold": 40000,
      "deliveryFee": 3000
    }
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| items | Array | 담은 순서(오래된 순). 비어 있으면 `[]` |
| items[].cartItemId | Long | 장바구니 항목 ID. 수량 변경 · 삭제 · 주문 미리보기 · 주문 생성에 사용 |
| items[].productId | Long | 상품 ID |
| items[].productName / imageUrl | String | 현재 상품 정보 |
| items[].price | Long | 현재 단가(원) |
| items[].saleUnitAmount | String | 판매 단위 수치(소수 문자열) |
| items[].saleUnit | String | 판매 단위: G, ML, EA |
| items[].quantity | Integer | 담은 수량 |
| items[].lineAmount | Long | price × quantity |
| items[].stock | Integer | 현재 재고 |
| items[].status | String | 항목 상태(아래 표) |
| hasPreviousMartItems | Boolean | 이전 마트 상품이 하나라도 있으면 true |
| deliveryPolicy.freeDeliveryThreshold | Long | 무료배송 기준 금액(40,000원) |
| deliveryPolicy.deliveryFee | Long | 기준 미만일 때 배송비(3,000원) |

항목 상태(`status`)는 아래 순서로 판정하며, 여러 조건이 겹치면 앞선 상태가 됩니다.

| 값 | 조건 | 화면 허용 동작 |
| --- | --- | --- |
| PREVIOUS_MART | 상품의 마트가 현재 이용 마트와 다름(이용 마트가 없는 회원은 전부 해당) | 삭제만 |
| DELETED | 상품이 삭제됨 | 삭제만 |
| STOPPED | 판매 중이 아님 | 삭제만 |
| SOLD_OUT | 재고 0 | 삭제만 |
| INSUFFICIENT_STOCK | 담은 수량이 재고보다 많음 | 재고 이하로 줄이는 수량 변경, 삭제 |
| AVAILABLE | 구매 가능 | 선택 · 수량 변경 · 삭제 |

### 예외처리

없음(공통 인증 오류만)

### 비즈니스 규칙

- 페이지 없이 본인 장바구니 전체를 반환합니다. 다른 회원의 항목은 조회되지 않습니다.
- 삭제 · 판매 중지 · 품절된 상품도 장바구니에서 지우지 않고 상태로 표시합니다.
- 주문할 상품 선택은 서버에 저장하지 않습니다. 결제 금액 패널은 프론트가 `lineAmount`와 `deliveryPolicy`로 계산하고, 실제 금액은 주문 미리보기 · 주문 생성에서 서버가 다시 계산합니다.
- 장바구니 조회 · 담기 · 수량 변경은 재고를 차감하지 않습니다.

---

## 2. 장바구니 담기

### API: 장바구니 담기

### Method / Path

`POST /api/cart-items`

### 인증

- 회원 인증 필요

| 헤더 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | O | Bearer {accessToken} |

### Request

```json
{
  "productId": 101,
  "quantity": 2
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| productId | Long | O | 담을 상품 ID |
| quantity | Integer | O | 이번에 추가할 수량, 1~99 |

### Response `200 OK`

```json
{
  "success": true,
  "code": "OK",
  "message": "요청이 처리되었습니다.",
  "data": {
    "cartItemId": 11,
    "productId": 101,
    "quantity": 4
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| cartItemId | Long | 장바구니 항목 ID. 이미 담긴 상품이면 기존 항목 ID |
| productId | Long | 상품 ID |
| quantity | Integer | 합산 후 최종 수량 |

### 예외처리

| HTTP Status | Error Code | 발생 조건 |
| --- | --- | --- |
| 400 | COMMON-001 (INVALID_INPUT) | productId 누락, quantity가 1~99 범위 밖 |
| 400 | CART-003 (INVALID_CART_QUANTITY) | 기존 수량 + 이번 수량이 99를 넘음 |
| 404 | PRODUCT-001 (PRODUCT_NOT_FOUND) | 상품이 없거나 삭제됨 |
| 409 | ORDER-004 (MART_NOT_SELECTED) | 이용 마트가 지정되지 않음 |
| 409 | CART-004 (CART_ITEM_NOT_PURCHASABLE) | 다른 마트 상품, 판매 중 아님, 품절(재고 0) |
| 409 | CART-005 (CART_INSUFFICIENT_STOCK) | 기존 수량 + 이번 수량이 재고보다 많음 |
| 409 | CART-006 (CART_HAS_PREVIOUS_MART_ITEMS) | 장바구니에 이전 마트 상품이 남아 있음 |
| 409 | CART-007 (CART_CONCURRENT_MODIFICATION) | 같은 상품을 동시에 담아 충돌(처음 담기 연타, 읽은 뒤 수량이 바뀜) |

### 비즈니스 규칙

- 검증 순서: 이용 마트 → 상품 존재 → 구매 가능(마트 일치 · 판매 중 · 재고 있음) → 이전 마트 상품 유무 → 수량(99 · 재고).
- 같은 상품을 다시 담으면 새 행을 만들지 않고 기존 수량에 합산합니다. 회원 · 상품 조합은 UNIQUE로 한 행만 둡니다.
- 재고 비교는 "이미 담긴 수량 + 이번 수량"으로 합니다. 담아도 재고는 차감하지 않습니다.
- 이전 마트 상품이 하나라도 있으면 담지 않습니다. 화면은 일괄 삭제 여부를 확인한 뒤 `POST /api/cart-items/bulk-delete` → 담기 순서로 호출합니다.
- 담기 성공 시 `CART_ADD_HISTORY`에 `source=DIRECT`로 회원 · 상품 · 이번 추가 수량을 기록합니다. 수량 변경 · 삭제는 이력을 만들지 않고, 삭제해도 기존 이력은 남깁니다.
- CART-007은 재시도하면 두 번 합산될 수 있으므로, 클라이언트는 재시도 대신 장바구니를 다시 조회해 보여 줍니다.

---

## 3. 장바구니 수량 변경

### API: 장바구니 수량 변경

### Method / Path

`PATCH /api/cart-items/{cartItemId}`

### 인증

- 회원 인증 필요

| 헤더 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | O | Bearer {accessToken} |

### Request

```json
{
  "quantity": 3
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| quantity | Integer | O | 변경 후 최종 수량, 1~99 |

### Response `200 OK`

```json
{
  "success": true,
  "code": "OK",
  "message": "요청이 처리되었습니다.",
  "data": {
    "cartItemId": 11,
    "quantity": 3
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| cartItemId | Long | 장바구니 항목 ID |
| quantity | Integer | 변경된 최종 수량 |

### 예외처리

| HTTP Status | Error Code | 발생 조건 |
| --- | --- | --- |
| 400 | COMMON-001 (INVALID_INPUT) | quantity 누락, 1~99 범위 밖 |
| 404 | CART-001 (CART_ITEM_NOT_FOUND) | 항목이 없거나 다른 회원의 항목 |
| 409 | ORDER-004 (MART_NOT_SELECTED) | 이용 마트가 지정되지 않음 |
| 409 | CART-004 (CART_ITEM_NOT_PURCHASABLE) | 삭제된 상품, 이전 마트 상품, 판매 중 아님, 품절 |
| 409 | CART-005 (CART_INSUFFICIENT_STOCK) | 최종 수량이 재고보다 많음 |

### 비즈니스 규칙

- 증감이 아니라 최종 수량으로 덮어씁니다. 같은 요청이 반복되거나 겹쳐도 마지막 요청의 수량이 남습니다.
- 재고가 부족한 항목(INSUFFICIENT_STOCK)은 재고 이하로 줄이는 변경만 성공합니다.
- 재고를 차감하지 않고 담기 이력도 만들지 않습니다.

---

## 4. 장바구니 개별 삭제

### API: 장바구니 개별 삭제

### Method / Path

`DELETE /api/cart-items/{cartItemId}`

### 인증

- 회원 인증 필요

| 헤더 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | O | Bearer {accessToken} |

### Request

없음

### Response `200 OK`

```json
{
  "success": true,
  "code": "OK",
  "message": "요청이 처리되었습니다.",
  "data": null
}
```

### 예외처리

| HTTP Status | Error Code | 발생 조건 |
| --- | --- | --- |
| 404 | CART-001 (CART_ITEM_NOT_FOUND) | 항목이 없거나 다른 회원의 항목 |

### 비즈니스 규칙

- 이전 마트 · 삭제 · 판매 중지 · 품절 상품도 삭제할 수 있습니다.
- 삭제해도 그 상품의 담기 이력(`CART_ADD_HISTORY`)은 남깁니다.

---

## 5. 장바구니 선택 항목 삭제

### API: 장바구니 선택 항목 삭제

### Method / Path

`POST /api/cart-items/bulk-delete`

### 인증

- 회원 인증 필요

| 헤더 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | O | Bearer {accessToken} |

### Request

```json
{
  "cartItemIds": [11, 12]
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| cartItemIds | Array<Long> | O | 삭제할 항목 ID, 1~100개 |

### Response `200 OK`

```json
{
  "success": true,
  "code": "OK",
  "message": "요청이 처리되었습니다.",
  "data": null
}
```

### 예외처리

| HTTP Status | Error Code | 발생 조건 |
| --- | --- | --- |
| 400 | COMMON-001 (INVALID_INPUT) | cartItemIds가 비어 있음, 100개 초과, null 포함 |

### 비즈니스 규칙

- 본인 항목만 삭제합니다. 이미 없거나 다른 회원의 ID가 섞여 있으면 건너뛰고 성공 처리합니다(멱등). 개별 삭제와 달리 404를 내지 않습니다.
- 이전 마트 상품 일괄 삭제에 이 API를 사용합니다.
- DELETE 본문을 버리는 클라이언트 · 프록시가 있어 ID 목록을 본문으로 받는 일괄 삭제는 POST 하위 경로로 둡니다.

---

## 6. 주문 미리보기

### API: 주문 미리보기

### Method / Path

`POST /api/orders/preview`

### 인증

- 회원 인증 필요

| 헤더 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | O | Bearer {accessToken} |

### Request

```json
{
  "cartItemIds": [11, 12],
  "legalDongCode": "1168010100"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| cartItemIds | Array<Long> | O | 주문할 본인 장바구니 항목, 1~100개 · 중복 불가 |
| legalDongCode | String | O | 배송지 법정동 코드, 숫자 10자리 |

### Response `200 OK`

```json
{
  "success": true,
  "code": "OK",
  "message": "요청이 처리되었습니다.",
  "data": {
    "orderable": true,
    "unorderableReasons": [],
    "delivery": {
      "status": "AVAILABLE",
      "martName": "예시마트 역삼점"
    },
    "items": [
      {
        "cartItemId": 11,
        "productId": 101,
        "productName": "양파 1망",
        "imageUrl": "https://cdn.example.com/products/101.jpg",
        "price": 2000,
        "saleUnitAmount": "1000.00",
        "saleUnit": "G",
        "quantity": 2,
        "lineAmount": 4000,
        "stock": 30,
        "status": "AVAILABLE"
      },
      {
        "cartItemId": 12,
        "productId": 102,
        "productName": "국산 두부",
        "imageUrl": "https://cdn.example.com/products/102.jpg",
        "price": 3000,
        "saleUnitAmount": "300.00",
        "saleUnit": "G",
        "quantity": 1,
        "lineAmount": 3000,
        "stock": 0,
        "status": "SOLD_OUT"
      }
    ],
    "productAmount": 4000,
    "deliveryFee": 3000,
    "payAmount": 7000,
    "pendingOrder": null
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| orderable | Boolean | unorderableReasons가 비어 있으면 true |
| unorderableReasons | Array<String> | 주문 불가 사유(아래 표). 선언 순서대로 내려줌 |
| delivery.status | String | AVAILABLE(이용 마트 지역) / OTHER_MART_AREA(다른 마트 지역) / OUTSIDE_SERVICE_AREA(서비스 지역 밖) |
| delivery.martName | String / null | AVAILABLE이면 이용 마트 이름, OTHER_MART_AREA면 그 지역의 운영 중인 마트 이름, 서비스 지역 밖이면 null |
| items | Array | 요청한 cartItemIds 순서. 구조 · 상태 값은 장바구니 조회의 items와 같음 |
| productAmount | Long | AVAILABLE 상품의 lineAmount 합 |
| deliveryFee | Long | productAmount가 40,000원 이상이면 0, 아니면 3,000. 구매 가능 상품이 없으면 0 |
| payAmount | Long | productAmount + deliveryFee |
| pendingOrder | Object / null | 결제 대기 중인 기존 주문. 없으면 null |
| pendingOrder.orderId / orderNumber | Long / String | 기존 주문 |
| pendingOrder.paymentId | Long | 기존 결제 ID |
| pendingOrder.orderName | String | 주문명(첫 상품명, 2개 이상이면 `첫 상품명 외 N건`) |
| pendingOrder.amount | Long | 결제 금액 |
| pendingOrder.paymentExpiresAt | String | 결제 만료 시각(생성 + 5분) |
| pendingOrder.payable | Boolean | 현재 시각이 만료 전이면 true(이어서 결제 가능) |

| unorderableReasons 값 | 조건 |
| --- | --- |
| MART_NOT_SELECTED | 이용 마트가 지정되지 않음 |
| MART_NOT_OPEN | 이용 마트가 없거나 운영 중이 아님 |
| DELIVERY_UNAVAILABLE | delivery.status가 AVAILABLE이 아님 |
| HAS_PREVIOUS_MART_ITEMS | 선택 항목에 이전 마트 상품이 있음 |
| NO_PURCHASABLE_ITEMS | AVAILABLE 상품이 하나도 없음 |
| PENDING_ORDER_EXISTS | 결제 대기 주문이 있음 |

### 예외처리

| HTTP Status | Error Code | 발생 조건 |
| --- | --- | --- |
| 400 | COMMON-001 (INVALID_INPUT) | cartItemIds가 비어 있음, 100개 초과, null 포함 |
| 400 | ORDER-002 (DUPLICATE_ORDER_ITEM) | cartItemIds에 중복 항목 |
| 400 | ORDER-006 (INVALID_DELIVERY_INFORMATION) | legalDongCode 누락 또는 숫자 10자리가 아님 |
| 404 | CART-001 (CART_ITEM_NOT_FOUND) | 항목이 없거나 다른 회원의 항목이 하나라도 있음 |

### 비즈니스 규칙

- 판정 결과(배송 가능 여부 · 주문 불가 사유 · 상품 상태)는 오류가 아니라 상태 필드로 내려줍니다. 같은 조건의 최종 거절은 주문 생성이 합니다.
- 재고를 잠그거나 차감하지 않고 현재 값으로 판정합니다. 미리보기 뒤 상황이 바뀌면 주문 생성에서 거절될 수 있습니다.
- 배송 가능 여부는 배송지 법정동이 이용 마트의 법정동(MART.region_id)과 같은지로 판정합니다. 좌표 · 거리는 쓰지 않습니다. 법정동이 REGION에 없으면 OUTSIDE_SERVICE_AREA입니다.
- 구매 불가 상품은 금액에서 제외하고 items[].status로 알립니다. 단, 이전 마트 상품은 제외하지 않고 주문 불가로 처리합니다.
- 이용 마트가 없는 회원은 담긴 상품이 모두 PREVIOUS_MART로 판정되어 MART_NOT_SELECTED · HAS_PREVIOUS_MART_ITEMS · NO_PURCHASABLE_ITEMS가 함께 내려갑니다.
- 금액 계산은 주문 생성과 같은 규칙을 씁니다.
- 수령인 · 연락처 · 주소 문자열은 판정과 무관해 받지 않습니다(주문 생성에서만 받음).
- 결제 대기 주문의 결제는 항상 IN_PROGRESS(주문 · 결제가 같은 트랜잭션에서 생성 · 종료)라 만료 시각만으로 payable을 판단합니다.
- 이어서 결제는 pendingOrder에 PortOne 결제 식별자를 담지 않고, 결제 버튼을 누를 때 결제 조회로 가져옵니다. payable은 미리보기 시점 값이라 그사이 만료 · 결제 · 취소됐을 수 있고, 주문 목록 · 상세에서 이어서 결제할 때도 같은 흐름을 쓰기 위해서입니다.
  1. `pendingOrder.orderId`로 `GET /api/payments/orders/{orderId}`를 호출합니다.
  2. `paymentStatus`가 IN_PROGRESS이고 현재 시각이 `expiresAt` 전이면 응답의 `portonePaymentId` · `amount`와 `pendingOrder.orderName`으로 PortOne 결제창을 다시 엽니다. 결제 결과는 신규 주문과 같이 결제 확정 API · 웹훅이 처리합니다.
  3. 그 밖의 상태이거나 만료됐으면 결제창을 열지 않고 미리보기를 다시 조회해 화면을 갱신합니다.

---

## 7. 주문 생성

### API: 주문 생성

### Method / Path

`POST /api/orders`

### 인증

- 회원 인증 필요

| 헤더 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | O | Bearer {accessToken} |
| Idempotency-Key | O | 주문하기를 누를 때마다 새로 만드는 UUID, 최대 64자. 재시도할 때만 같은 값을 보냄. 대소문자는 구분하지 않음(소문자로 정규화) |

### Request

```json
{
  "cartItemIds": [11],
  "receiverName": "홍길동",
  "receiverPhone": "01012345678",
  "address": "서울특별시 강남구 테헤란로 10",
  "detailAddress": "101동 1001호",
  "legalDongCode": "1168010100"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| cartItemIds | Array<Long> | O | 미리보기 후 실제 구매 확정한 본인 항목, 1~100개 · 중복 불가 |
| receiverName | String | O | 받는 사람 이름, 공백 제외 1~50자 |
| receiverPhone | String | O | 숫자만 10~11자리 |
| address | String | O | 기본 배송 주소, 최대 255자 |
| detailAddress | String | X | 상세 주소, 최대 255자 |
| legalDongCode | String | O | 배송지 법정동 코드, 숫자 10자리 |

### Response `201 Created` (신규 생성) / `200 OK` (같은 Idempotency-Key 재시도)

```json
{
  "success": true,
  "code": "OK",
  "message": "요청이 처리되었습니다.",
  "data": {
    "orderId": 1001,
    "orderNumber": "20261014-7K3M8Q2RZX",
    "status": "PENDING_PAYMENT",
    "orderName": "양파 1망",
    "productAmount": 4000,
    "deliveryFee": 3000,
    "amount": 7000,
    "paymentId": 2001,
    "portonePaymentId": "pay_3f9c1e2a-5b7d-4c8e-9a1f-2b3c4d5e6f70",
    "expiresAt": "2026-10-14T21:35:00+09:00",
    "receiverName": "홍길동",
    "receiverPhone": "01012345678"
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| orderId | Long | 내부 주문 PK. 결제 확정 · 취소 · 조회 등 후속 API에 사용 |
| orderNumber | String | 고객 표시용 주문번호, `yyyyMMdd-영숫자 10자리`. 생성 후 불변 |
| status | String | 주문 상태. 신규는 PENDING_PAYMENT, 재시도 응답은 그 시점의 현재 상태 |
| orderName | String | PortOne 결제창에 쓰는 주문명. 첫 상품명, 2개 이상이면 `첫 상품명 외 N건` |
| productAmount / deliveryFee | Long | 서버가 확정한 상품 합계 · 배송비 |
| amount | Long | 결제 금액(productAmount + deliveryFee) |
| paymentId | Long | 결제 테이블 ID |
| portonePaymentId | String | PortOne 결제 식별자(`pay_` + UUID) |
| expiresAt | String | 결제 만료 시각(생성 시점 + 5분) |
| receiverName / receiverPhone | String | 주문에 저장된 수령인 이름 · 연락처 |

### 예외처리

| HTTP Status | Error Code | 발생 조건 |
| --- | --- | --- |
| 400 | COMMON-001 (INVALID_INPUT) | 필수값 누락 · 형식 오류(수령인 · 연락처 · 주소 길이, cartItemIds 비어 있음 · 100개 초과), Idempotency-Key 64자 초과 |
| 400 | COMMON-007 (MISSING_IDEMPOTENCY_KEY) | Idempotency-Key 헤더 누락 또는 빈 값 |
| 400 | ORDER-002 (DUPLICATE_ORDER_ITEM) | cartItemIds에 중복 항목 |
| 400 | ORDER-006 (INVALID_DELIVERY_INFORMATION) | legalDongCode 누락 또는 숫자 10자리가 아님 |
| 400 | ORDER-008 (OUTSIDE_DELIVERY_AREA) | 배송지 법정동이 이용 마트 지역과 다르거나 서비스 지역(REGION)에 없음 |
| 404 | CART-001 (CART_ITEM_NOT_FOUND) | 장바구니 항목이 없거나 다른 회원의 항목 |
| 404 | PRODUCT-001 (PRODUCT_NOT_FOUND) | 재고 차감 시점에 상품이 삭제됐거나 이용 마트 상품이 아님 |
| 409 | ORDER-003 (PENDING_ORDER_EXISTS) | 결제 대기 주문이 이미 있음(동시 생성으로 인한 UNIQUE 충돌 포함) |
| 409 | ORDER-004 (MART_NOT_SELECTED) | 이용 마트가 지정되지 않음 |
| 409 | ORDER-005 (MART_NOT_OPEN) | 이용 마트가 없거나 운영 중이 아님 |
| 409 | CART-006 (CART_HAS_PREVIOUS_MART_ITEMS) | 선택 항목에 이전 마트 상품이 있음 |
| 409 | PRODUCT-003 (PRODUCT_NOT_SELLING) | 판매 중이 아닌 상품이 있음 |
| 409 | STOCK-001 (INSUFFICIENT_STOCK) | 재고가 부족한 상품이 있음 |
| 409 | COMMON-006 (DUPLICATE_REQUEST_IN_PROGRESS) | 같은 Idempotency-Key의 첫 요청이 아직 처리 중 |
| 409 | COMMON-011 (IDEMPOTENCY_KEY_PAYLOAD_MISMATCH) | 같은 Idempotency-Key에 다른 요청 본문 |
| 409 | COMMON-010 (LOCK_ACQUISITION_TIMEOUT) | 주문 · 재고 락 대기 시간 초과 |

### 비즈니스 규칙

- 검증 · 처리 순서(모두 한 DB 트랜잭션):
  1. Idempotency-Key 정규화(소문자), cartItemIds 중복 검사, 요청 본문 해시(request_hash) 계산
  2. 같은 회원 · 같은 키의 주문이 있으면: 해시가 같으면 재고 차감 · 결제 생성 없이 기존 주문의 현재 상태를 200으로 반환, 다르면 COMMON-011
  3. 결제 대기 주문이 있으면 ORDER-003. 기존 대기 주문을 자동 종료하지 않으며, 기존 결제를 계속하거나 명시적으로 취소하도록 안내
  4. 이용 마트 확인(ORDER-004) → 운영 중 확인(ORDER-005)
  5. 법정동 형식(ORDER-006) → 이용 마트 지역과 일치(ORDER-008)
  6. 장바구니 항목 존재 · 소유 확인(CART-001) → 상품 마트가 이용 마트와 같은지 확인(CART-006)
  7. 주문 행 저장(PENDING_PAYMENT, martStatus NONE, pending_member_id = 회원 ID) 후 즉시 flush. UNIQUE 충돌이면 멱등 키 충돌은 COMMON-006, 그 밖은 ORDER-003
  8. 상품 ID 오름차순으로 비관적 락을 잡고 삭제 · 마트(PRODUCT-001), 판매 상태(PRODUCT-003), 재고(STOCK-001)를 검증한 뒤 실제 재고를 즉시 차감(예약 재고 없음)
  9. 잠근 시점의 상품명 · 단가 · 판매 단위 · 수량 · 줄 금액을 ORDER_ITEM에 스냅샷으로 저장하고 금액 확정(상품 합계 40,000원 이상이면 배송비 0, 아니면 3,000원)
  10. 결제 행 생성(IN_PROGRESS, 만료 = 생성 + 5분)
- 하나라도 실패하면 주문 · 항목 · 재고 · 결제가 모두 롤백되고 Idempotency-Key도 저장되지 않습니다. 같은 키로 재시도하면 처음부터 다시 검증합니다.
- 금액은 서버가 계산하며 클라이언트가 보낸 금액을 받지 않습니다. 미리보기 금액과 달라졌는지는 클라이언트가 응답 금액으로 확인합니다.
- 미리보기와 달리 구매 불가 상품이 하나라도 있으면 제외하지 않고 거절합니다. 사용자가 확인하지 않은 금액으로 결제가 만들어지지 않게 하기 위함입니다.
- request_hash는 정렬한 cartItemIds, receiverName, receiverPhone, address, detailAddress, legalDongCode의 SHA-256(hex 64자)입니다. 항목 순서만 다른 재시도는 같은 요청으로 봅니다.
- 회원당 결제 대기 주문은 1건입니다. pending_member_id는 PENDING_PAYMENT 동안 회원 ID, 그 밖의 상태에서는 null이며 UNIQUE로 동시 생성을 최종 방어합니다.
- 주문에 저장한 수령인 · 주소 · 법정동은 이후 회원 배송지가 바뀌어도 변하지 않습니다. 주문서에서 입력한 주소는 회원 배송지 목록에 반영하지 않습니다.
- 이후 상태 변경은 결제 도메인이 주도합니다. 결제 성공 → PAID / RECEIVED, 결제 실패 · 5분 만료 · 결제 전 취소 · 금액 불일치 → CANCELED + 재고 복구. 결제창 초기화 · 결제 확정 · 취소는 결제 API 명세를 따릅니다.
- 사용 포인트는 1차에서 받지 않으며 0으로 저장합니다.

---

## 8. 주문 목록 조회

### API: 주문 목록 조회

### Method / Path

`GET /api/orders`

### 인증

- 회원 인증 필요

| 헤더 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | O | Bearer {accessToken} |

### Request (Query)

`GET /api/orders?statusGroup=ONGOING&page=0&size=20`

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| statusGroup | String | X | ONGOING(진행 중) / DELIVERED(배송완료) / CANCELED_REFUNDED(취소 · 환불). 생략하면 전체 |
| page | Integer | X | 0부터, 기본 0 |
| size | Integer | X | 기본 20, 최대 100 |

### Response `200 OK`

```json
{
  "success": true,
  "code": "OK",
  "message": "요청이 처리되었습니다.",
  "data": {
    "content": [
      {
        "orderId": 1001,
        "orderNumber": "20261014-7K3M8Q2RZX",
        "createdAt": "2026-10-14T21:30:00+09:00",
        "status": "PAID",
        "martStatus": "RECEIVED",
        "orderName": "양파 1망",
        "payAmount": 7000
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| content[].orderId | Long | 주문 ID |
| content[].orderNumber | String | 주문번호 |
| content[].createdAt | String | 주문 생성 일시 |
| content[].status / martStatus | String | 주문 상태 · 마트 처리 상태 |
| content[].orderName | String | 주문명(첫 상품명, 2개 이상이면 `첫 상품명 외 N건`) |
| content[].payAmount | Long | 결제 금액 |
| page / size / totalElements / totalPages | Integer / Integer / Long / Integer | 페이지 정보 |

### 예외처리

| HTTP Status | Error Code | 발생 조건 |
| --- | --- | --- |
| 400 | COMMON-001 (INVALID_INPUT) | 없는 statusGroup 값 |

### 비즈니스 규칙

- 최신순(id 내림차순)으로 고정하며 sort 파라미터는 무시합니다.
- 탭(statusGroup) 조건:
  - ONGOING: PENDING_PAYMENT 또는 (PAID이면서 martStatus가 DELIVERED가 아님)
  - DELIVERED: PAID이면서 martStatus가 DELIVERED
  - CANCELED_REFUNDED: CANCEL_REQUESTED, CANCELED, REFUNDED
- 결제 대기 주문도 목록에 포함됩니다. 결제 정보(만료 시각 · 결제 가능 여부)는 결제 조회 API에서 가져옵니다.

---

## 9. 주문 상세 조회

### API: 주문 상세 조회

### Method / Path

`GET /api/orders/{orderId}`

### 인증

- 회원 인증 필요

| 헤더 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | O | Bearer {accessToken} |

### Request

없음

### Response `200 OK`

```json
{
  "success": true,
  "code": "OK",
  "message": "요청이 처리되었습니다.",
  "data": {
    "orderId": 1001,
    "orderNumber": "20261014-7K3M8Q2RZX",
    "createdAt": "2026-10-14T21:30:00+09:00",
    "status": "PAID",
    "martStatus": "RECEIVED",
    "orderName": "양파 1망",
    "cancelable": true,
    "items": [
      {
        "productId": 101,
        "productName": "양파 1망",
        "unitPrice": 2000,
        "saleUnitAmount": "1000.00",
        "saleUnit": "G",
        "quantity": 2,
        "lineAmount": 4000
      }
    ],
    "productAmount": 4000,
    "deliveryFee": 3000,
    "payAmount": 7000,
    "receiverName": "홍길동",
    "receiverPhone": "01012345678",
    "address": "서울특별시 강남구 테헤란로 10",
    "detailAddress": "101동 1001호",
    "cancelReasonCode": null,
    "cancelReasonDetail": null
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| orderId / orderNumber / createdAt | Long / String / String | 주문 식별 · 생성 일시 |
| status / martStatus | String | 주문 상태 · 마트 처리 상태 |
| orderName | String | 주문명. 이어서 결제할 때 PortOne 결제창에 사용 |
| cancelable | Boolean | 취소 버튼 표시 여부. PENDING_PAYMENT(결제 전 취소) 또는 PAID이면서 RECEIVED(결제 후 취소)일 때 true |
| items[].productId | Long | 상품 ID |
| items[].productName | String | 주문 당시 상품명 |
| items[].unitPrice | Long | 주문 당시 단가 |
| items[].saleUnitAmount / saleUnit | String / String | 주문 당시 판매 단위 수치 · 단위 |
| items[].quantity | Integer | 주문 수량 |
| items[].lineAmount | Long | unitPrice × quantity |
| productAmount / deliveryFee / payAmount | Long | 확정 금액 |
| receiverName / receiverPhone / address / detailAddress | String | 주문에 저장된 배송 정보 |
| cancelReasonCode | String / null | 취소 사유 코드. 취소되지 않은 주문은 null |
| cancelReasonDetail | String / null | 취소 사유 상세 |

### 예외처리

| HTTP Status | Error Code | 발생 조건 |
| --- | --- | --- |
| 404 | ORDER-012 (ORDER_NOT_FOUND) | 주문이 없거나 다른 회원의 주문 |

### 비즈니스 규칙

- 다른 회원의 주문은 403이 아니라 404로 응답해 존재 여부를 알리지 않습니다.
- 결제 정보(PortOne 결제 ID · 만료 시각 · 결제 가능 여부)는 넣지 않습니다. `GET /api/payments/orders/{orderId}`가 그 값의 출처라 같은 값을 두 곳에 두지 않습니다.
- items는 주문 당시 값(ORDER_ITEM 스냅샷)이라 이후 상품 정보가 바뀌거나 삭제돼도 변하지 않습니다.
- 마트가 준비를 시작(PREPARING)하면 회원 취소를 받지 않으므로 cancelable이 false가 됩니다.

---

## 10. 관리자 주문 목록 조회

### API: 관리자 주문 목록 조회

### Method / Path

`GET /api/admin/orders`

### 인증

- 관리자 인증 필요(SUPER_ADMIN, MART_ADMIN)

| 헤더 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | O | Bearer {관리자 accessToken} |

### Request (Query)

`GET /api/admin/orders?martStatus=RECEIVED&martId=1&page=0&size=20`

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| martStatus | String | X | RECEIVED / PREPARING / SHIPPING / DELIVERED / REJECTED. 생략하면 결제 전(NONE)을 뺀 전체 |
| martId | Long | X | SUPER_ADMIN 전용. 생략하면 전체 마트. MART_ADMIN은 토큰의 마트로 고정되며 다른 마트를 지정하면 403 |
| page | Integer | X | 0부터, 기본 0 |
| size | Integer | X | 기본 20, 최대 100 |

### Response `200 OK`

```json
{
  "success": true,
  "code": "OK",
  "message": "요청이 처리되었습니다.",
  "data": {
    "content": [
      {
        "orderId": 1001,
        "orderNumber": "20261014-7K3M8Q2RZX",
        "createdAt": "2026-10-14T21:30:00+09:00",
        "status": "PAID",
        "martStatus": "RECEIVED",
        "orderName": "양파 1망",
        "payAmount": 7000,
        "receiverName": "홍길동",
        "martId": 1
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| content[].orderId / orderNumber / createdAt | Long / String / String | 주문 식별 · 생성 일시 |
| content[].status / martStatus | String | 주문 상태 · 마트 처리 상태 |
| content[].orderName | String | 주문명 |
| content[].payAmount | Long | 결제 금액 |
| content[].receiverName | String | 수령인 이름 |
| content[].martId | Long | 처리 마트 ID |
| page / size / totalElements / totalPages | Integer / Integer / Long / Integer | 페이지 정보 |

### 예외처리

| HTTP Status | Error Code | 발생 조건 |
| --- | --- | --- |
| 400 | COMMON-001 (INVALID_INPUT) | 없는 martStatus 값 |
| 403 | AUTH-005 (MART_ACCESS_DENIED) | MART_ADMIN이 다른 마트의 martId를 지정 |

### 비즈니스 규칙

- 결제 전(martStatus NONE) 주문은 마트가 처리할 대상이 아니라 항상 제외합니다. `martStatus=NONE`으로 조회해도 빈 결과입니다.
- SUPER_ADMIN이 없는 마트 ID를 지정하면 오류 없이 빈 페이지를 반환합니다.
- 최신순(id 내림차순) 고정, sort 무시.

---

## 11. 관리자 주문 상세 조회

### API: 관리자 주문 상세 조회

### Method / Path

`GET /api/admin/orders/{orderId}`

### 인증

- 관리자 인증 필요(SUPER_ADMIN, MART_ADMIN)

| 헤더 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | O | Bearer {관리자 accessToken} |

### Request

없음

### Response `200 OK`

```json
{
  "success": true,
  "code": "OK",
  "message": "요청이 처리되었습니다.",
  "data": {
    "orderId": 1001,
    "orderNumber": "20261014-7K3M8Q2RZX",
    "createdAt": "2026-10-14T21:30:00+09:00",
    "status": "PAID",
    "martStatus": "PREPARING",
    "memberId": 7,
    "martId": 1,
    "orderName": "양파 1망",
    "items": [
      {
        "productId": 101,
        "productName": "양파 1망",
        "unitPrice": 2000,
        "saleUnitAmount": "1000.00",
        "saleUnit": "G",
        "quantity": 2,
        "lineAmount": 4000
      }
    ],
    "productAmount": 4000,
    "deliveryFee": 3000,
    "payAmount": 7000,
    "receiverName": "홍길동",
    "receiverPhone": "01012345678",
    "address": "서울특별시 강남구 테헤란로 10",
    "detailAddress": "101동 1001호",
    "cancelReasonCode": null,
    "cancelReasonDetail": null,
    "histories": [
      {
        "statusType": "ORDER",
        "fromStatus": null,
        "toStatus": "PENDING_PAYMENT",
        "actor": "MEMBER",
        "changedByAdminId": null,
        "reason": null,
        "createdAt": "2026-10-14T21:30:00+09:00"
      },
      {
        "statusType": "ORDER",
        "fromStatus": "PENDING_PAYMENT",
        "toStatus": "PAID",
        "actor": "SYSTEM",
        "changedByAdminId": null,
        "reason": null,
        "createdAt": "2026-10-14T21:31:10+09:00"
      },
      {
        "statusType": "MART",
        "fromStatus": "NONE",
        "toStatus": "RECEIVED",
        "actor": "SYSTEM",
        "changedByAdminId": null,
        "reason": null,
        "createdAt": "2026-10-14T21:31:10+09:00"
      },
      {
        "statusType": "MART",
        "fromStatus": "RECEIVED",
        "toStatus": "PREPARING",
        "actor": "MART",
        "changedByAdminId": 5,
        "reason": null,
        "createdAt": "2026-10-14T21:40:00+09:00"
      }
    ]
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| orderId / orderNumber / createdAt | Long / String / String | 주문 식별 · 생성 일시 |
| status / martStatus | String | 주문 상태 · 마트 처리 상태 |
| memberId | Long | 주문 회원 ID |
| martId | Long | 처리 마트 ID |
| orderName | String | 주문명 |
| items | Array | 주문 상품 스냅샷. 구조는 회원 주문 상세와 같음 |
| productAmount / deliveryFee / payAmount | Long | 확정 금액 |
| receiverName / receiverPhone / address / detailAddress | String | 배송 정보 |
| cancelReasonCode / cancelReasonDetail | String / null | 취소 사유. 취소되지 않은 주문은 null |
| histories | Array | 상태 변경 이력, 오래된 순 |
| histories[].statusType | String | ORDER(주문 상태) / MART(마트 처리 상태) |
| histories[].fromStatus / toStatus | String / null | 변경 전 · 후 상태. 생성 이력은 fromStatus가 null |
| histories[].actor | String | MEMBER / SYSTEM / MART / ADMIN |
| histories[].changedByAdminId | Long / null | 관리자가 처리한 변경이면 그 관리자 ID |
| histories[].reason | String / null | 사유(취소 사유 코드 등) |
| histories[].createdAt | String | 변경 시각 |

### 예외처리

| HTTP Status | Error Code | 발생 조건 |
| --- | --- | --- |
| 403 | AUTH-005 (MART_ACCESS_DENIED) | MART_ADMIN이 다른 마트의 주문을 조회 |
| 404 | ORDER-012 (ORDER_NOT_FOUND) | 주문이 없거나 결제 전(martStatus NONE) 주문 |

### 비즈니스 규칙

- 결제 전 주문은 마트가 처리할 대상이 아니라 없는 주문과 같이 404로 응답합니다.
- 존재 여부를 먼저 확인한 뒤 마트 범위를 검사하므로, 다른 마트의 결제 후 주문은 403입니다.
- 결제 정보는 넣지 않습니다. 결제 관리자 API에서 조회합니다.
- 수락 · 거절 가능 여부 필드는 없습니다. 클라이언트는 status=PAID, martStatus=RECEIVED로 판단합니다.

---

## 12. 주문 수락

### API: 주문 수락

### Method / Path

`POST /api/admin/orders/{orderId}/accept`

### 인증

- 관리자 인증 필요(SUPER_ADMIN, MART_ADMIN)

| 헤더 | 필수 | 설명 |
| --- | --- | --- |
| Authorization | O | Bearer {관리자 accessToken} |

### Request

없음(본문 없음)

### Response `200 OK`

```json
{
  "success": true,
  "code": "OK",
  "message": "요청이 처리되었습니다.",
  "data": {
    "orderId": 1001,
    "status": "PAID",
    "martStatus": "PREPARING"
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| orderId | Long | 주문 ID |
| status | String | 주문 상태(변하지 않음, PAID) |
| martStatus | String | 수락 후 마트 처리 상태(PREPARING) |

### 예외처리

| HTTP Status | Error Code | 발생 조건 |
| --- | --- | --- |
| 403 | AUTH-005 (MART_ACCESS_DENIED) | MART_ADMIN이 다른 마트의 주문을 수락 |
| 404 | ORDER-012 (ORDER_NOT_FOUND) | 주문이 없거나 결제 전(martStatus NONE) 주문 |
| 409 | ORDER-014 (ORDER_NOT_ACCEPTABLE) | PAID · RECEIVED가 아님: 취소 진행 중(CANCEL_REQUESTED), 종료(CANCELED · REFUNDED), 이미 배송중 · 배송완료, 마트 거절 |
| 409 | COMMON-010 (LOCK_ACQUISITION_TIMEOUT) | 주문 행 락 대기 시간 초과 |

### 비즈니스 규칙

- status=PAID이고 martStatus=RECEIVED인 주문만 수락할 수 있고, 수락하면 martStatus가 PREPARING이 됩니다. 주문 상태(status)는 바뀌지 않습니다.
- 같은 주문을 다시 수락하면(이미 PAID · PREPARING) 409가 아니라 200으로 현재 상태를 돌려주고 이력은 추가하지 않습니다.
- 주문 행에 비관적 락을 잡은 뒤 상태를 확인하므로, 회원 취소와 동시에 들어와도 한쪽만 성공합니다. 회원 취소가 먼저면 ORDER-014, 수락이 먼저면 회원 취소가 거절됩니다.
- 이력은 statusType=MART, actor=MART, changedByAdminId=처리한 관리자 ID로 남깁니다. SUPER_ADMIN이 대신 처리해도 주체는 MART이고 관리자 ID로 누가 처리했는지 구분합니다.
- SUPER_ADMIN은 모든 마트의 주문을, MART_ADMIN은 소속 마트 주문만 수락할 수 있습니다.
- 수락 이후의 PREPARING → SHIPPING → DELIVERED 상태 변경은 아직 구현되지 않았습니다. 마트 거절은 주문 쪽 접수 메서드(`OrderService.requestMartRejection`, RECEIVED → REJECTED 전이)까지 구현됐고, `POST /api/admin/orders/{orderId}/reject` API는 결제 Facade(ADMIN Refund) 연결 후 추가합니다.

---

## 부록. 주문 도메인 에러 코드

| 코드 | HTTP | 메시지 | 발생 API |
| --- | --- | --- | --- |
| CART-001 | 404 | 장바구니 상품을 찾을 수 없습니다. | 수량 변경, 개별 삭제, 주문 미리보기, 주문 생성 |
| CART-002 | 400 | 장바구니가 비어 있습니다. | 정의만, 현재 미사용 |
| CART-003 | 400 | 상품 수량이 올바르지 않습니다. | 장바구니 담기 |
| CART-004 | 409 | 현재 장바구니에 담거나 수량을 변경할 수 없는 상품입니다. | 장바구니 담기, 수량 변경 |
| CART-005 | 409 | 요청한 수량보다 상품 재고가 부족합니다. | 장바구니 담기, 수량 변경 |
| CART-006 | 409 | 장바구니에 이전 마트 상품이 있습니다. 삭제한 뒤 다시 담아 주세요. | 장바구니 담기, 주문 생성 |
| CART-007 | 409 | 다른 요청과 동시에 처리되어 장바구니에 담지 못했습니다. 장바구니를 확인해 주세요. | 장바구니 담기 |
| ORDER-001 | 400 | 주문할 상품을 선택해 주세요. | 정의만(빈 목록은 COMMON-001로 응답) |
| ORDER-002 | 400 | 주문할 상품이 중복되었습니다. | 주문 미리보기, 주문 생성 |
| ORDER-003 | 409 | 결제 대기 중인 주문이 있습니다. 기존 주문을 확인해 주세요. | 주문 생성 |
| ORDER-004 | 409 | 이용 마트가 지정되지 않았습니다. | 장바구니 담기, 수량 변경, 주문 생성 |
| ORDER-005 | 409 | 현재 이용 마트에서 주문할 수 없습니다. | 주문 생성 |
| ORDER-006 | 400 | 배송지 정보를 확인해 주세요. | 주문 미리보기, 주문 생성 |
| ORDER-007 | 404 | 배송지 지역을 찾을 수 없습니다. | 정의만(없는 법정동은 ORDER-008로 응답) |
| ORDER-008 | 400 | 배송 가능한 지역이 아닙니다. | 주문 생성 |
| ORDER-009 | 409 | 주문 가능한 상품이 없습니다. | 정의만(구매 불가 상품은 PRODUCT-001 · PRODUCT-003 · STOCK-001로 응답) |
| ORDER-010 | 409 | 상품 재고가 부족하여 주문을 생성할 수 없습니다. | 정의만(STOCK-001로 응답) |
| ORDER-011 | 409 | 주문 금액이 변경되었습니다. 다시 확인해 주세요. | 정의만(클라이언트 금액을 받지 않음) |
| ORDER-012 | 404 | 주문을 찾을 수 없습니다. | 주문 상세, 관리자 주문 상세, 주문 수락 |
| ORDER-013 | 409 | 현재 취소할 수 없는 주문입니다. | 결제 도메인의 회원 취소 흐름(엔티티 방어) |
| ORDER-014 | 409 | 현재 수락할 수 없는 주문입니다. | 주문 수락 |
| ORDER-015 | 409 | 현재 거절할 수 없는 주문입니다. | 마트 거절(엔티티 방어). 거절 접수 메서드는 NOT_REJECTABLE · ALREADY_REJECTED를 반환값으로 돌려주고 API 응답 코드는 결제 Facade 연결 시 확정 |
| ORDER-016 | 409 | 허용되지 않는 주문 상태 변경입니다. | 결제 도메인 흐름(엔티티 방어) |
| ORDER-017 | 409 | 허용되지 않는 주문의 마트 처리 상태 변경입니다. | 결제 도메인 흐름(엔티티 방어) |
| ORDER-018 | 409 | 동시 처리 충돌로 요청을 완료하지 못했습니다. 주문 상태를 확인한 뒤 다시 시도해 주세요. | 정의만, 현재 미사용 |

주문 API에서 함께 쓰는 다른 도메인 · 공통 코드

| 코드 | HTTP | 메시지 | 발생 API |
| --- | --- | --- | --- |
| PRODUCT-001 | 404 | 상품을 찾을 수 없습니다. | 장바구니 담기, 주문 생성 |
| PRODUCT-003 | 409 | 판매 중인 상품이 아닙니다. | 주문 생성 |
| STOCK-001 | 409 | 재고가 부족합니다. | 주문 생성 |
| AUTH-005 | 403 | 소속 마트의 데이터만 처리할 수 있습니다. | 관리자 주문 목록 · 상세 · 수락 |
| COMMON-001 | 400 | 입력값이 올바르지 않습니다. | 전체(입력 검증, `errors` 포함) |
| COMMON-002 | 400 | 요청 형식이 올바르지 않습니다. | 전체(JSON 파싱 · 타입 오류) |
| COMMON-006 | 409 | 같은 요청을 처리하고 있습니다. 잠시 후 다시 확인해 주세요. | 주문 생성 |
| COMMON-007 | 400 | Idempotency-Key 헤더가 필요합니다. | 주문 생성 |
| COMMON-010 | 409 | 다른 요청이 같은 자원을 처리하고 있습니다. 잠시 후 다시 시도해 주세요. | 주문 생성, 주문 수락 |
| COMMON-011 | 409 | 같은 Idempotency-Key로 다른 요청을 보낼 수 없습니다. | 주문 생성 |
