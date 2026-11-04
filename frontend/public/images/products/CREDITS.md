# 상품 이미지

2026-11-04 사용자가 제공한 Recipick 패키지 이미지 165장을 웹용 WebP로 변환해 사용합니다. 데스크탑의 원본은 유지했습니다. 최대 768px·품질 82로 크롭 없이 비율을 유지했으며, 전체 용량은 약 280.94MiB에서 7.74MiB로 97.2% 감소했습니다. 기존 Wikimedia 상품 사진은 교체했습니다.

파일명의 번호는 `scripts/recipe-seed/ingredients.csv`의 재료 순번이며, 실제 DB의 재료 ID와 일치한다고 가정하지 않습니다. 상품 연결은 재료명·상품명·브랜드로 합니다. 같은 재료의 용량별·마트별 상품은 하나의 대표 이미지를 공유하므로 사진의 표기 용량보다 상품 정보의 판매 단위·가격을 기준으로 확인합니다.

## 기존 DB 반영

이미지 경로가 `.jpg`에서 새 `.webp` 경로로 변경됐습니다. 신규 로컬 DB는 생성된 상품 시드가 새 경로로 등록합니다. 기존 로컬 DB도 시드를 읽을 때 기존 시연 상품의 이미지 UPDATE가 적용됩니다.

상품 브랜드는 `products.csv`에서 관리하며, `previous_brand`는 기존 시드 상품의 ID를 유지하며 브랜드를 갱신하기 위한 이전 브랜드입니다. 상품명 앞에는 브랜드를 붙이지 않습니다.

운영처럼 시드를 자동 실행하지 않는 환경은 `src/main/resources/data-product-local.sql`의 중복 상품 정리(스파게티면 500g 4,500원 상품 숨김)와 브랜드 갱신 UPDATE 묶음을 순서대로 적용한 뒤 프론트 이미지 배포와 함께 이미지 갱신 UPDATE 묶음을 적용해야 합니다. 각각 `-- 중복 시연 상품 정리:`, `-- 기존 시연 상품의 브랜드를 갱신한다.`, `-- 기존 시연 상품의 이미지를 갱신한다.` 주석으로 구분됩니다. INSERT나 다른 시드 구간은 제외합니다. 전체 시드는 카테고리·레시피 설정도 바꾸므로 이미지 교체 목적으로 운영에서 통째로 실행하지 않습니다. 대상은 시연 마트 1·2의 재료명·상품명·브랜드가 일치하는 상품이며, 이미지가 이미 같으면 UPDATE하지 않습니다. 운영 적용 전 기존 image_url을 보관하고 가격·재고·판매 상태가 유지되는지 확인합니다.

## 재료별 연결

| 파일 | 재료명 |
| --- | --- |
| ingredient-001.webp | 김치 |
| ingredient-002.webp | 돼지고기 앞다리살 |
| ingredient-003.webp | 두부 |
| ingredient-004.webp | 대파 |
| ingredient-005.webp | 양파 |
| ingredient-006.webp | 다진마늘 |
| ingredient-007.webp | 고춧가루 |
| ingredient-008.webp | 된장 |
| ingredient-009.webp | 애호박 |
| ingredient-010.webp | 청양고추 |
| ingredient-011.webp | 국물용 멸치 |
| ingredient-012.webp | 건미역 |
| ingredient-013.webp | 소고기 국거리 |
| ingredient-014.webp | 국간장 |
| ingredient-015.webp | 참기름 |
| ingredient-016.webp | 고추장 |
| ingredient-017.webp | 간장 |
| ingredient-018.webp | 설탕 |
| ingredient-019.webp | 계란 |
| ingredient-020.webp | 당근 |
| ingredient-021.webp | 소금 |
| ingredient-022.webp | 식용유 |
| ingredient-023.webp | 새우젓 |
| ingredient-024.webp | 스파게티면 |
| ingredient-025.webp | 베이컨 |
| ingredient-026.webp | 파마산치즈 가루 |
| ingredient-027.webp | 생크림 |
| ingredient-028.webp | 후추 |
| ingredient-029.webp | 올리브유 |
| ingredient-030.webp | 토마토소스 |
| ingredient-031.webp | 통마늘 |
| ingredient-032.webp | 생강 |
| ingredient-033.webp | 홍고추 |
| ingredient-034.webp | 건고추 |
| ingredient-035.webp | 꽈리고추 |
| ingredient-036.webp | 감자 |
| ingredient-037.webp | 무 |
| ingredient-038.webp | 콩나물 |
| ingredient-039.webp | 시금치 |
| ingredient-040.webp | 오이 |
| ingredient-041.webp | 양배추 |
| ingredient-042.webp | 고구마 |
| ingredient-043.webp | 깻잎 |
| ingredient-044.webp | 표고버섯 |
| ingredient-045.webp | 양송이버섯 |
| ingredient-046.webp | 파프리카 |
| ingredient-047.webp | 피망 |
| ingredient-048.webp | 토마토 |
| ingredient-049.webp | 방울토마토 |
| ingredient-050.webp | 단호박 |
| ingredient-051.webp | 고사리 |
| ingredient-052.webp | 숙주 |
| ingredient-053.webp | 우거지 |
| ingredient-054.webp | 곤드레 |
| ingredient-055.webp | 쑥갓 |
| ingredient-056.webp | 미나리 |
| ingredient-057.webp | 우엉 |
| ingredient-058.webp | 밤 |
| ingredient-059.webp | 대추 |
| ingredient-060.webp | 인삼 |
| ingredient-061.webp | 셀러리 |
| ingredient-062.webp | 로메인 |
| ingredient-063.webp | 양상추 |
| ingredient-064.webp | 아보카도 |
| ingredient-065.webp | 옥수수 |
| ingredient-066.webp | 레몬 |
| ingredient-067.webp | 바질 |
| ingredient-068.webp | 파슬리 |
| ingredient-069.webp | 로즈마리 |
| ingredient-070.webp | 피클 |
| ingredient-071.webp | 견과류 |
| ingredient-072.webp | 쪽파 |
| ingredient-073.webp | 다진 돼지고기 |
| ingredient-074.webp | 다진 소고기 |
| ingredient-075.webp | 소고기 불고기용 |
| ingredient-076.webp | 소갈비 |
| ingredient-077.webp | 소고기 홍두깨살 |
| ingredient-078.webp | 소고기 스테이크용 |
| ingredient-079.webp | 돼지 등뼈 |
| ingredient-080.webp | 닭볶음탕용 닭 |
| ingredient-081.webp | 닭다리살 |
| ingredient-082.webp | 닭가슴살 |
| ingredient-083.webp | 영계 |
| ingredient-084.webp | 햄 |
| ingredient-085.webp | 소시지 |
| ingredient-086.webp | 동태 |
| ingredient-087.webp | 오징어 |
| ingredient-088.webp | 낙지 |
| ingredient-089.webp | 쭈꾸미 |
| ingredient-090.webp | 북어채 |
| ingredient-091.webp | 고등어 |
| ingredient-092.webp | 새우 |
| ingredient-093.webp | 바지락 |
| ingredient-094.webp | 홍합 |
| ingredient-095.webp | 어묵 |
| ingredient-096.webp | 맛살 |
| ingredient-097.webp | 잔멸치 |
| ingredient-098.webp | 다시마 |
| ingredient-099.webp | 김가루 |
| ingredient-100.webp | 김밥김 |
| ingredient-101.webp | 안초비 |
| ingredient-102.webp | 순두부 |
| ingredient-103.webp | 우유 |
| ingredient-104.webp | 버터 |
| ingredient-105.webp | 모차렐라 치즈 |
| ingredient-106.webp | 체다 치즈 |
| ingredient-107.webp | 요거트 |
| ingredient-108.webp | 쌀 |
| ingredient-109.webp | 찹쌀 |
| ingredient-110.webp | 백태 |
| ingredient-111.webp | 밀가루 |
| ingredient-112.webp | 부침가루 |
| ingredient-113.webp | 빵가루 |
| ingredient-114.webp | 전분 |
| ingredient-115.webp | 소면 |
| ingredient-116.webp | 칼국수면 |
| ingredient-117.webp | 냉면사리 |
| ingredient-118.webp | 쫄면사리 |
| ingredient-119.webp | 메밀면 |
| ingredient-120.webp | 당면 |
| ingredient-121.webp | 라면사리 |
| ingredient-122.webp | 펜네 |
| ingredient-123.webp | 마카로니 |
| ingredient-124.webp | 라자냐면 |
| ingredient-125.webp | 떡국떡 |
| ingredient-126.webp | 떡볶이떡 |
| ingredient-127.webp | 식빵 |
| ingredient-128.webp | 바게트 |
| ingredient-129.webp | 번 |
| ingredient-130.webp | 잉글리시 머핀 |
| ingredient-131.webp | 또띠아 |
| ingredient-132.webp | 유부 |
| ingredient-133.webp | 후레이크 |
| ingredient-134.webp | 단무지 |
| ingredient-135.webp | 통조림 콩 |
| ingredient-136.webp | 참치캔 |
| ingredient-137.webp | 청국장 |
| ingredient-138.webp | 액젓 |
| ingredient-139.webp | 케첩 |
| ingredient-140.webp | 마요네즈 |
| ingredient-141.webp | 머스터드 |
| ingredient-142.webp | 겨자 |
| ingredient-143.webp | 굴소스 |
| ingredient-144.webp | 우스터소스 |
| ingredient-145.webp | 냉면육수 |
| ingredient-146.webp | 들기름 |
| ingredient-147.webp | 고추기름 |
| ingredient-148.webp | 식초 |
| ingredient-149.webp | 맛술 |
| ingredient-150.webp | 물엿 |
| ingredient-151.webp | 통깨 |
| ingredient-152.webp | 들깻가루 |
| ingredient-153.webp | 페페론치노 |
| ingredient-154.webp | 월계수잎 |
| ingredient-155.webp | 통후추 |
| ingredient-156.webp | 시나몬 |
| ingredient-157.webp | 바닐라익스트랙 |
| ingredient-158.webp | 사프란 |
| ingredient-159.webp | 파프리카 가루 |
| ingredient-160.webp | 슈가파우더 |
| ingredient-161.webp | 화이트와인 |
| ingredient-162.webp | 카레가루 |
| ingredient-163.webp | 매실청 |
| ingredient-164.webp | 메이플시럽 |
| ingredient-165.webp | 배즙 |
