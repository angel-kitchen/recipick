import Pictogram from './Pictogram';

// 홈 배너 문구에 맞춰 직접 그린 장면 3개. 정글 그린 바탕 위에 흰색·리프 그린·픽토그램만 쓴다.
// 설명용 그림이라 스크린리더는 건너뛴다
function CartArt() {
  return (
    <svg className="bs-cart" viewBox="0 0 220 180" aria-hidden="true" focusable="false">
      <path d="M52 44h140l-20 70H64z" fill="#7ba05b" />
      <path d="M62 66h122M70 90h106" stroke="#0d4c3c" strokeWidth="3" opacity=".25" strokeLinecap="round" />
      <path d="M8 14h30l22 100h110l22-70H50" fill="none" stroke="#fff" strokeWidth="9" strokeLinecap="round" strokeLinejoin="round" />
      <circle cx="82" cy="148" r="14" fill="#fff" />
      <circle cx="160" cy="148" r="14" fill="#fff" />
      <circle cx="82" cy="148" r="5" fill="#0d4c3c" />
      <circle cx="160" cy="148" r="5" fill="#0d4c3c" />
    </svg>
  );
}

function Spark({ className }) {
  return (
    <svg className={`bs-spark ${className}`} viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      <path d="M12 1l2.6 8.4L23 12l-8.4 2.6L12 23l-2.6-8.4L1 12l8.4-2.6z" fill="currentColor" />
    </svg>
  );
}

// 1) 메뉴만 고르세요, 재료는 알아서 담아드려요: 레시피 카드에서 인분을 고르면 재료가 장바구니로 들어간다
function PickScene() {
  return (
    <div className="bs bs--pick">
      <div className="bs-card bs-recipe">
        <div className="bs-recipe__top">
          <Pictogram name="bowl" size={64} />
          <span>
            <b>김치찌개</b>
            <small>한식 · 30분</small>
          </span>
        </div>
        <div className="bs-recipe__step">
          <span>인분</span>
          <span className="bs-stepper">
            <i>−</i>
            <b>2</b>
            <i>+</i>
          </span>
        </div>
        <div className="bs-recipe__btn">재료 자동 담기</div>
      </div>
      <svg className="bs-arrow" viewBox="0 0 120 40" aria-hidden="true" focusable="false">
        <path d="M4 22C34 -4 70 -4 108 20" fill="none" stroke="#fff" strokeWidth="4" strokeLinecap="round" strokeDasharray="2 10" />
        <path d="M98 10l14 10-16 8" fill="none" stroke="#fff" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      <span className="bs-item bs-item--a">
        <Pictogram name="kimchi" size={56} />
      </span>
      <span className="bs-item bs-item--b">
        <Pictogram name="tofu" size={52} />
      </span>
      <span className="bs-item bs-item--c">
        <Pictogram name="scallion" size={50} />
      </span>
      <CartArt />
      <Spark className="bs-spark--a" />
      <Spark className="bs-spark--b" />
    </div>
  );
}

// 2) 자동 장바구니, 처음 한 번은 무료예요: 7일 무료 이용권과 장바구니
function FreeScene() {
  return (
    <div className="bs bs--free">
      <div className="bs-ticket">
        <div className="bs-ticket__stub">
          <b>7</b>
          <span>일</span>
        </div>
        <div className="bs-ticket__main">
          <strong>무료 체험권</strong>
          <small>처음 한 번 · 시작할 때 결제 없음</small>
          <em>FREE</em>
        </div>
      </div>
      <div className="bs-free__cart">
        <CartArt />
      </div>
      <span className="bs-item bs-item--d">
        <Pictogram name="bowl" size={54} />
      </span>
      <span className="bs-item bs-item--e">
        <Pictogram name="kimchi" size={44} />
      </span>
      <Spark className="bs-spark--c" />
      <Spark className="bs-spark--d" />
      <Spark className="bs-spark--e" />
    </div>
  );
}

// 3) 집 앞까지 가까운 마트가 보내요: 마트에서 트럭이 출발해 집 앞에 도착한다
function ShipScene() {
  return (
    <div className="bs bs--ship">
      <span className="bs-cloud bs-cloud--a" />
      <span className="bs-cloud bs-cloud--b" />
      <svg className="bs-mart" viewBox="0 0 150 150" aria-hidden="true" focusable="false">
        <rect x="14" y="58" width="122" height="84" rx="6" fill="#fff" />
        <path d="M6 58l10-34h118l10 34z" fill="#7ba05b" />
        <path d="M30 24l-6 34M54 24l-2 34M78 24v34M102 24l2 34M126 24l6 34" stroke="#fff" strokeWidth="6" opacity=".9" />
        <rect x="58" y="88" width="34" height="54" rx="4" fill="#0d4c3c" />
        <rect x="24" y="76" width="26" height="26" rx="3" fill="#daf1de" />
        <rect x="100" y="76" width="26" height="26" rx="3" fill="#daf1de" />
        <text x="75" y="14" textAnchor="middle" fontSize="15" fontWeight="700" fill="#fff">
          MART
        </text>
      </svg>
      <svg className="bs-route" viewBox="0 0 300 40" aria-hidden="true" focusable="false">
        <path d="M4 30C70 6 150 6 214 26" fill="none" stroke="#fff" strokeWidth="4" strokeLinecap="round" strokeDasharray="2 10" />
      </svg>
      <span className="bs-truck">
        <Pictogram name="truck" size={112} />
      </span>
      <span className="bs-house">
        <Pictogram name="house" size={104} />
      </span>
      <span className="bs-pin">
        <Pictogram name="pin" size={52} />
      </span>
      <span className="bs-chip">배송 가능</span>
      <span className="bs-road" />
    </div>
  );
}

const SCENES = { pick: PickScene, free: FreeScene, ship: ShipScene };

export default function BandScene({ type }) {
  const Scene = SCENES[type];
  return Scene ? <Scene /> : null;
}
