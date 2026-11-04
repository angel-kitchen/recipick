import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import Pictogram from '../components/Pictogram';
import StewArt from '../components/StewArt';

// 멤버십이 무엇인지 소개하는 공개 페이지. 가입·결제는 /membership(내 멤버십)에서 한다
// 이미지는 public/images/products 의 Recipick 상품 사진(출처는 폴더 CREDITS.md)과 직접 그린 그림이다
const PRODUCTS = {
  kimchi: { src: '/images/products/ingredient-001.webp', name: '김치 500g' },
  pork: { src: '/images/products/ingredient-002.webp', name: '돼지고기 500g' },
  carrot: { src: '/images/products/ingredient-020.webp', name: '당근 2개' },
  pumpkin: { src: '/images/products/ingredient-050.webp', name: '단호박 1개' },
};

const COMPARE = [
  { label: '레시피 보기 · 직접 장보기', normal: '가능', member: '가능' },
  { label: '자동 장바구니', normal: '이용 불가', member: '이용 가능' },
  { label: 'AI 검색', normal: '이용 불가', member: '이용 가능' },
  { label: '무료배송 기준', normal: '4만 원 이상', member: '1만 5천 원 이상' },
  { label: '포인트 적립', normal: '0.5%', member: '1%' },
];

const FAQS = [
  {
    q: '멤버십은 얼마예요?',
    a: '월 3,900원이에요. 처음 한 번은 7일 동안 무료로 체험해 볼 수 있어요.',
  },
  {
    q: '무료 체험은 누구나 할 수 있나요?',
    a: '멤버십을 이용한 적이 없는 회원이면 한 번 할 수 있어요. 이미 이용한 적이 있거나, 탈퇴 전에 이용 기록이 있는 휴대전화 번호로 다시 가입한 경우에는 탈퇴 후 1년 동안 체험이 제한돼요.',
  },
  {
    q: '체험을 시작하면 바로 결제되나요?',
    a: '아니요. 체험을 시작할 때는 결제되지 않아요. 다만 체험이 끝나기 전에 해지 예약을 하지 않으면 첫 결제가 진행되고 월 3,900원 멤버십으로 바뀌어요. 시작하기 전에 결제 카드를 한 번 등록해야 해요.',
  },
  {
    q: '어떻게 해지하나요?',
    a: '"내 멤버십"에서 자동 갱신 해지를 예약하면 다음 결제부터 멈춰요. 이미 낸 기간은 끝까지 그대로 이용할 수 있어요. 체험 중에 해지를 예약하면 첫 결제 없이 남은 체험 기간만 쓰고 일반 회원으로 돌아가요.',
  },
  {
    q: '결제한 뒤에 바로 그만두면 환불받을 수 있나요?',
    a: '유료로 이용 중이라면 이용 기간 안에 "즉시 해지·환불"을 요청할 수 있어요. 환불금은 이용한 날과 위약금을 뺀 금액이고, 환불이 끝나면 바로 멤버십 혜택이 사라져요.',
  },
  {
    q: '멤버십이 없으면 Recipick을 못 쓰나요?',
    a: '아니요. 레시피 보기, 상품 검색, 직접 장바구니 담기와 주문은 멤버십 없이도 쓸 수 있어요. 자동 장바구니와 AI 검색만 멤버십 회원 전용이에요.',
  },
  {
    q: '우리 동네에서도 쓸 수 있나요?',
    a: '배송지가 서비스 지역 안에 있어야 해요. 홈 화면 지도에서 가맹 마트를 확인하거나, 배송지를 등록해 배송 가능한 마트가 있는지 먼저 알아볼 수 있어요.',
  },
];

function ProductCard({ product, className = '' }) {
  return (
    <figure className={`mi-product ${className}`}>
      <img src={product.src} alt={product.name} loading="lazy" />
      <figcaption>{product.name}</figcaption>
    </figure>
  );
}

export default function MembershipIntro() {
  const { member } = useAuth();
  const startTo = member ? '/membership' : '/login';
  const startLabel = member?.trialEligible === false ? '멤버십 시작하기' : '7일 무료 체험하기';

  return (
    <div className="mi">
      <section className="mi-hero">
        <div className="container mi-hero__inner">
          <div className="mi-hero__text">
            <p className="mi-eyebrow">RECIPICK MEMBERSHIP</p>
            <h1 className="mi-hero__title">
              장보기는 레시픽에게
              <br />
              요리만 즐기세요
            </h1>
            <p className="mi-hero__lead">
              메뉴와 인분만 고르면 재료가 알아서 장바구니에 담겨요.
              <br />
              처음 한 번, 7일 무료로 시작해 보세요.
            </p>
            <div className="mi-hero__cta">
              <Link to={startTo} className="mi-btn mi-btn--light">
                {startLabel}
              </Link>
              <a href="#benefits" className="mi-link">
                혜택 둘러보기 ↓
              </a>
            </div>
            <p className="mi-hero__note">체험을 시작할 때는 결제되지 않아요 · 이후 월 3,900원</p>
          </div>
          <div className="mi-hero__media" aria-hidden="true">
            <div className="mi-hero__art">
              <StewArt />
            </div>
            <ProductCard product={PRODUCTS.kimchi} className="mi-float mi-float--a" />
            <ProductCard product={PRODUCTS.pork} className="mi-float mi-float--b" />
            <span className="mi-chip">
              <i /> 재료가 장바구니에 담겼어요
            </span>
          </div>
        </div>
      </section>

      <section className="mi-section" id="benefits">
        <div className="container">
          <h2 className="mi-title">멤버십이면, 장보기가 달라져요</h2>
          <p className="mi-lead">레시피를 고르는 순간부터 집 앞 배송까지, 번거로운 일은 Recipick이 맡아요.</p>

          <div className="mi-row">
            <div className="mi-row__text">
              <span className="mi-tag">자동 장바구니</span>
              <h3>메뉴만 고르면 재료가 알아서 담겨요</h3>
              <p>
                필요한 양을 인분 수에 맞춰 계산하고 우리 동네 마트 상품으로 장바구니를 채워 줘요. 집에 있는 재료는 체크만 빼면
                돼요.
              </p>
            </div>
            <div className="mi-row__media">
              <div className="mi-cartcard" aria-hidden="true">
                <div className="mi-cartcard__head">
                  <b>김치찌개</b>
                  <small>한식 · 2인분</small>
                </div>
                {['kimchi', 'carrot', 'pumpkin'].map((key) => (
                  <div key={key} className="mi-cartcard__row">
                    <img src={PRODUCTS[key].src} alt="" loading="lazy" />
                    <span>{PRODUCTS[key].name}</span>
                    <i className="mi-check" />
                  </div>
                ))}
                <div className="mi-cartcard__bar">재료 3개가 장바구니에 담겼어요</div>
              </div>
            </div>
          </div>

          <div className="mi-row mi-row--reverse">
            <div className="mi-row__text">
              <span className="mi-tag">AI 검색</span>
              <h3>말로 물어보면 레시피를 찾아 줘요</h3>
              <p>
                &quot;김치찌개 2인분&quot;처럼 말로 입력하거나, &quot;매운 음식 30분 이내&quot;처럼 조건만 적어도 돼요. 냉장고에 있는
                재료로 만들 수 있는 요리도 추천받을 수 있어요.
              </p>
            </div>
            <div className="mi-row__media">
              <div className="mi-cartcard mi-chat" aria-hidden="true">
                <div className="mi-cartcard__head">
                  <b>AI 검색</b>
                  <small>예시</small>
                </div>
                <p className="mi-chat__ask">매운 음식 30분 이내로 추천해 줘</p>
                <p className="mi-chat__hint">조건에 맞는 레시피를 찾았어요</p>
                {[
                  ['bowl', '김치찌개', '한식 · 25분'],
                  ['plate', '제육볶음', '한식 · 20분'],
                  ['kimchi', '김치볶음밥', '한식 · 15분'],
                ].map(([icon, name, meta]) => (
                  <div key={name} className="mi-cartcard__row">
                    <span className="mi-chat__icon">
                      <Pictogram name={icon} size={36} />
                    </span>
                    <span>
                      {name}
                      <small>{meta}</small>
                    </span>
                  </div>
                ))}
              </div>
            </div>
          </div>

          <div className="mi-row">
            <div className="mi-row__text">
              <span className="mi-tag">배송비·포인트 혜택</span>
              <h3>더 적은 금액에도 무료배송, 포인트는 두 배</h3>
              <p>
                일반 회원은 4만 원 이상부터 무료배송이지만 멤버십 회원은 1만 5천 원 이상이면 배송비가 없어요. 포인트도 일반 회원
                0.5%보다 많은 1%를 적립해 드려요.
              </p>
            </div>
            <div className="mi-row__media">
              <div className="mi-cartcard mi-order" aria-hidden="true">
                <div className="mi-cartcard__head">
                  <b>주문 예시</b>
                  <Pictogram name="truck" size={44} />
                </div>
                <dl>
                  <div>
                    <dt>상품 금액</dt>
                    <dd>15,000원</dd>
                  </div>
                  <div>
                    <dt>배송비</dt>
                    <dd>
                      <em>무료배송</em> 0원
                    </dd>
                  </div>
                  <div>
                    <dt>포인트 적립</dt>
                    <dd className="is-point">+150P</dd>
                  </div>
                </dl>
                <div className="mi-cartcard__bar">1만 5천 원 이상이라 배송비가 없어요</div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section className="mi-section mi-section--tint">
        <div className="container">
          <h2 className="mi-title">일반 회원과 비교해 보세요</h2>
          <div className="mi-table" role="table" aria-label="일반 회원과 멤버십 비교">
            <div className="mi-table__row mi-table__row--head" role="row">
              <span role="columnheader" />
              <span role="columnheader">일반 회원</span>
              <span role="columnheader" className="is-member">
                멤버십
              </span>
            </div>
            {COMPARE.map((row) => (
              <div key={row.label} className="mi-table__row" role="row">
                <span role="rowheader">{row.label}</span>
                <span role="cell">{row.normal}</span>
                <span role="cell" className="is-member">
                  {row.member}
                </span>
              </div>
            ))}
          </div>
          <p className="mi-fine">무료 체험 중에는 포인트가 0.5% 적립돼요. 유료 이용이 시작되면 1%가 적용돼요.</p>

          <div className="mi-plan">
            <div>
              <p className="mi-plan__tag">멤버십</p>
              <p className="mi-plan__price">
                월 <b>3,900원</b>
              </p>
              <p className="mi-plan__trial">처음 한 번 7일 무료</p>
            </div>
            <ul>
              <li>자동 장바구니와 AI 검색을 마음껏</li>
              <li>1만 5천 원 이상 무료배송, 포인트 1% 적립</li>
              <li>자동 갱신 해지는 언제든 예약할 수 있어요</li>
            </ul>
          </div>
          <p className="mi-fine">
            체험이 끝나기 전에 해지 예약을 하지 않으면 첫 결제가 진행돼요. 유료 이용 중에는 이용 기간 안에 즉시 해지·환불도 요청할
            수 있어요.
          </p>
        </div>
      </section>

      <section className="mi-cta">
        <div className="container mi-cta__inner">
          <ProductCard product={PRODUCTS.pumpkin} className="mi-cta__img mi-cta__img--l" />
          <div className="mi-cta__text">
            <h2>멤버십을 사용해 보시겠어요?</h2>
            <p>처음 7일은 무료예요. 마음에 들지 않으면 체험이 끝나기 전에 해지를 예약하면 돼요.</p>
            <div className="mi-hero__cta mi-cta__buttons">
              <Link to={startTo} className="mi-btn mi-btn--light">
                {startLabel}
              </Link>
              {member && (
                <Link to="/membership" className="mi-link">
                  내 멤버십 보기 →
                </Link>
              )}
            </div>
          </div>
          <ProductCard product={PRODUCTS.carrot} className="mi-cta__img mi-cta__img--r" />
        </div>
      </section>

      <section className="mi-section mi-faq">
        <div className="container mi-faq__inner">
          <h2 className="mi-title">자주 묻는 질문</h2>
          <div className="mi-faq__list">
            {FAQS.map((item) => (
              <details key={item.q} className="mi-faq__item">
                <summary>{item.q}</summary>
                <p>{item.a}</p>
              </details>
            ))}
          </div>
        </div>
      </section>
    </div>
  );
}
