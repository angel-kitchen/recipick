import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import BandScene from '../components/BandScenes';
import DealsStrip from '../components/DealsStrip';
import Icon from '../components/Icon';
import ImagePlaceholder from '../components/ImagePlaceholder';
import MembershipInfoModal from '../components/MembershipInfoModal';
import Pictogram from '../components/Pictogram';
import KoreaMap from '../components/KoreaMap';
import { CUISINE, DIFFICULTY, formatWon } from '../labels';

// 띠 한 장마다 글·링크·그림(BandScenes)이 있고 바탕색은 모두 같다
const BAND_SLIDES = [
  {
    kicker: '오늘 저녁, 뭐 먹지?',
    title: '메뉴만 고르세요\n재료는 알아서 담아드려요',
    text: '인분 수에 맞춰 필요한 재료를 계산해요.',
    link: { to: '/recipes', guestTo: '/start', label: '레시피 보러 가기' },
    scene: 'pick',
  },
  {
    kicker: '멤버십 7일 무료 체험',
    title: '자동 장바구니\n처음 한 번은 무료예요',
    text: '체험을 시작할 때는 결제되지 않아요.',
    link: { to: '/membership-info', guestTo: '/membership-info', label: '멤버십 알아보기' },
    scene: 'free',
  },
  {
    kicker: '우리 동네 마트에서',
    title: '집 앞까지\n가까운 마트가 보내요',
    text: '배송지를 정하면 배송 가능한 마트를 알려 드려요.',
    link: { to: '/addresses', guestTo: '/start', label: '배송지 확인하기' },
    scene: 'ship',
  },
];

const FEATURES = [
  { icon: 'recipe', title: '정확한 재료 계산', text: '고른 요리와 인분 수에 맞춰 필요한 재료량을 계산해요.' },
  { icon: 'product', title: '판매 단위에 맞춘 수량', text: '필요한 양을 상품 판매 단위로 맞춰 장바구니에 담아요.' },
  { icon: 'clock', title: '5분 안에 결제', text: '주문을 만들고 5분 안에 결제를 마치면 돼요.' },
  { icon: 'shield', title: '마트 접수 전 취소', text: '마트가 접수하기 전(접수중)에는 직접 전체 취소할 수 있어요.' },
];

const MEMBERSHIP_STEPS = [
  { title: '메뉴·인분 선택', text: '레시피에서 요리와 인분 수만 골라요.' },
  { title: '재료 자동 담기', text: '필요한 재료가 우리 동네 마트 상품으로 담겨요.' },
  { title: '장바구니 확인', text: '집에 있는 재료는 빼고 주문해요.' },
];

// 화면 폭 끝까지 이어지는 띠 배너. 5초마다 넘어가고 일시정지·이전·다음을 누를 수 있다
function BandBanner({ member }) {
  const [index, setIndex] = useState(0);
  const [playing, setPlaying] = useState(true);
  const move = (step) => setIndex((current) => (current + step + BAND_SLIDES.length) % BAND_SLIDES.length);

  useEffect(() => {
    if (!playing) return undefined;
    const timer = setInterval(() => setIndex((current) => (current + 1) % BAND_SLIDES.length), 5000);
    return () => clearInterval(timer);
  }, [playing, index]);

  return (
    <section className="hm-band" aria-roledescription="carousel" aria-label="Recipick 소개">
      {BAND_SLIDES.map((slide, i) => (
        <div
          key={slide.scene}
          className={`hm-band__slide ${i === index ? 'is-on' : ''}`}
          aria-hidden={i !== index}
        >
          <div className="container hm-band__inner">
            <div className="hm-band__text">
              <p className="hm-band__kicker">{slide.kicker}</p>
              <h1 className="hm-band__title">{slide.title}</h1>
              <p className="hm-band__sub">{slide.text}</p>
              <Link
                to={member ? slide.link.to : slide.link.guestTo}
                className="hm-btn hm-btn--fill"
                tabIndex={i === index ? 0 : -1}
              >
                {slide.link.label} →
              </Link>
            </div>
            <div className="hm-band__scene" aria-hidden="true">
              <BandScene type={slide.scene} />
            </div>
          </div>
        </div>
      ))}
      <button type="button" className="hm-band__arrow hm-band__arrow--prev" aria-label="이전 띠" onClick={() => move(-1)}>
        ‹
      </button>
      <button type="button" className="hm-band__arrow hm-band__arrow--next" aria-label="다음 띠" onClick={() => move(1)}>
        ›
      </button>
      <div className="hm-band__ctrl">
        <button type="button" aria-label={playing ? '자동 넘김 멈춤' : '자동 넘김 시작'} onClick={() => setPlaying((v) => !v)}>
          {playing ? '❚❚' : '▶'}
        </button>
        <span>
          <b>{String(index + 1).padStart(2, '0')}</b> | {String(BAND_SLIDES.length).padStart(2, '0')}
        </span>
      </div>
    </section>
  );
}

const PERIODS = { DAILY: '일간', WEEKLY: '주간', MONTHLY: '월간', ALL: '전체' };

// 찜 많은 순 랭킹. 서버(/api/recipes/popular)가 기간별 찜 수로 정렬해 준다
function PopularRanking() {
  const [period, setPeriod] = useState('WEEKLY');
  const [recipes, setRecipes] = useState(null);
  const listRef = useRef(null);

  useEffect(() => {
    let ignore = false;
    setRecipes(null);
    api
      .get('/api/recipes/popular', { period, page: 0, size: 10 })
      .then((data) => !ignore && setRecipes(data.content))
      .catch(() => !ignore && setRecipes([]));
    return () => {
      ignore = true;
    };
  }, [period]);

  return (
    <section className="hm-section hm-rank">
      <div className="container">
        <div className="hm-head hm-head--center">
          <div>
            <h2 className="hm-title">실시간 인기랭킹</h2>
            <p className="hm-lead">가장 많이 찜한 레시피만 모아보세요!</p>
            <span className="hm-chips hm-chips--center" role="group" aria-label="랭킹 기간">
              {Object.entries(PERIODS).map(([key, text]) => (
                <button
                  key={key}
                  type="button"
                  className={`hm-chip ${period === key ? 'is-on' : ''}`}
                  aria-pressed={period === key}
                  onClick={() => setPeriod(key)}
                >
                  {text}
                </button>
              ))}
            </span>
          </div>
        </div>
        {recipes?.length === 0 && <p className="state-message">이 기간에 찜한 레시피가 아직 없어요.</p>}
        {recipes?.length > 0 && (
          <div className="hm-rank__wrap">
            <ol className="hm-rank__list" ref={listRef}>
              {recipes.map((recipe, i) => (
                <li key={recipe.recipeId} className="hm-rank__item">
                  <Link to={`/recipes/${recipe.recipeId}`} className="hm-rank__card">
                    <span className="hm-rank__image">
                      {recipe.thumbnailUrl ? <img src={recipe.thumbnailUrl} alt="" loading="lazy" /> : <ImagePlaceholder />}
                    </span>
                    <span className="hm-rank__body">
                      <span className="hm-rank__no">{i + 1}</span>
                      <span className="hm-rank__info">
                        <strong>{recipe.name}</strong>
                        <small>
                          {[CUISINE[recipe.cuisineType], recipe.cookingTimeMinutes ? `${recipe.cookingTimeMinutes}분` : null]
                            .filter(Boolean)
                            .join(' · ')}
                        </small>
                      </span>
                    </span>
                    <span className="hm-rank__likes">찜 {recipe.bookmarkCount.toLocaleString('ko-KR')}</span>
                  </Link>
                </li>
              ))}
            </ol>
            <button
              type="button"
              className="hm-rank__next"
              aria-label="다음 레시피 보기"
              onClick={() => listRef.current?.scrollBy({ left: 480, behavior: 'smooth' })}
            >
              ›
            </button>
          </div>
        )}
        <div className="hm-rank__all">
          <Link to="/recipes/rankings" className="hm-btn hm-btn--line hm-btn--wide">
            전체보기 ›
          </Link>
        </div>
      </div>
    </section>
  );
}

// 레시피·상품 API는 회원만 부를 수 있어서 로그인했을 때만 목록을 보여 준다
function WeeklyRecipes() {
  const [cuisine, setCuisine] = useState('');
  const [recipes, setRecipes] = useState(null);

  useEffect(() => {
    let ignore = false;
    setRecipes(null);
    api
      .get('/api/recipes', { cuisineType: cuisine, page: 0, size: 8 })
      .then((data) => !ignore && setRecipes(data.content))
      .catch(() => !ignore && setRecipes([]));
    return () => {
      ignore = true;
    };
  }, [cuisine]);

  return (
    <section className="hm-section">
      <div className="container">
        <div className="hm-head">
          <div>
            <p className="hm-eyebrow">THIS WEEK</p>
            <h2 className="hm-title">이번 주, 장보기 가능한 레시피</h2>
          </div>
          <div className="hm-head__side">
            <span className="hm-chips" role="group" aria-label="분류">
              {[['', '전체'], ...Object.entries(CUISINE)].map(([key, text]) => (
                <button
                  key={key || 'all'}
                  type="button"
                  className={`hm-chip ${cuisine === key ? 'is-on' : ''}`}
                  aria-pressed={cuisine === key}
                  onClick={() => setCuisine(key)}
                >
                  {text}
                </button>
              ))}
            </span>
            <Link to="/recipes" className="hm-more">
              전체 보기 →
            </Link>
          </div>
        </div>
        {recipes?.length === 0 && <p className="state-message">보여 드릴 레시피가 없어요.</p>}
        <div className="hm-grid">
          {(recipes ?? []).map((recipe) => (
            <Link key={recipe.recipeId} to={`/recipes/${recipe.recipeId}`} className="hm-card">
              <div className="hm-card__image">
                {recipe.thumbnailUrl ? (
                  <img src={recipe.thumbnailUrl} alt="" loading="lazy" />
                ) : (
                  <ImagePlaceholder />
                )}
                <span className="hm-card__badge">{CUISINE[recipe.cuisineType]}</span>
              </div>
              <span className="hm-card__meta">
                {[recipe.cookingTimeMinutes ? `조리 ${recipe.cookingTimeMinutes}분` : null, DIFFICULTY[recipe.difficulty]]
                  .filter(Boolean)
                  .join(' · ')}
              </span>
              <strong className="hm-card__name">{recipe.name}</strong>
            </Link>
          ))}
        </div>
      </div>
    </section>
  );
}

function FreshProducts() {
  const [products, setProducts] = useState([]);

  useEffect(() => {
    let ignore = false;
    api
      .get('/api/products', { page: 0, size: 4 })
      .then((data) => !ignore && setProducts(data.content))
      .catch(() => !ignore && setProducts([]));
    return () => {
      ignore = true;
    };
  }, []);

  if (products.length === 0) return null;
  return (
    <section className="hm-section">
      <div className="container">
        <div className="hm-head">
          <div>
            <p className="hm-eyebrow">FRESH TODAY</p>
            <h2 className="hm-title">오늘의 식재료</h2>
          </div>
          <Link to="/products" className="hm-more">
            장보기 바로가기 →
          </Link>
        </div>
        <div className="hm-grid">
          {products.map((product) => (
            <Link key={product.productId} to={`/products/${product.productId}`} className="hm-card">
              <div className="hm-card__image">
                {product.imageUrl ? <img src={product.imageUrl} alt="" loading="lazy" /> : <ImagePlaceholder />}
              </div>
              <span className="hm-card__meta">{product.brand}</span>
              <strong className="hm-card__name">{product.name}</strong>
              <span className="hm-card__price">{formatWon(product.price)}</span>
            </Link>
          ))}
        </div>
      </div>
    </section>
  );
}

export default function Home() {
  const { member } = useAuth();
  const [infoOpen, setInfoOpen] = useState(false);
  const isActiveMember = Boolean(member?.membership?.active);
  const membershipLabel = member?.trialEligible === false ? '멤버십 가입하기' : '멤버십 무료 체험하기';

  return (
    <>
      <div className="hm-notice">
        4만 원 이상 무료배송 · 멤버십은 1만 5천 원 이상 무료배송 · 결제 후 마트 접수 전까지 직접 취소할 수 있어요
      </div>

      <BandBanner member={member} />

      <section className="hm-features">
        <div className="container hm-features__inner">
          {FEATURES.map((feature) => (
            <div key={feature.title} className="hm-feature">
              <Icon name={feature.icon} size={30} />
              <strong>{feature.title}</strong>
              <span>{feature.text}</span>
            </div>
          ))}
        </div>
      </section>

      {member && <WeeklyRecipes />}

      {member && <PopularRanking />}

      {member && (
        <section className="hm-section">
          <div className="container">
            <DealsStrip limit={8} moreTo="/products" />
          </div>
        </section>
      )}

      {!isActiveMember && (
        <section className="hm-apple" aria-labelledby="hm-apple-title">
          <div className="container">
            <p className="hm-apple__tag">Recipick Membership</p>
            <h2 className="hm-apple__title" id="hm-apple-title">
              고르는 건 메뉴뿐,
              <br />
              담는 건 레시픽이 합니다.
            </h2>
            <p className="hm-apple__lead">
              메뉴와 인분만 고르면 장바구니가 알아서 채워져요.
              <br />
              상품 검색과 직접 장보기는 멤버십 없이도 쓸 수 있어요.
            </p>
            <div className="hm-apple__cta">
              <Link to="/membership-info" className="hm-pill hm-pill--fill">
                월 3,900원 · {membershipLabel}
              </Link>
              <button type="button" className="hm-pill hm-pill--ghost" onClick={() => setInfoOpen(true)}>
                혜택 자세히 보기
              </button>
            </div>

            <div className="hm-apple__render" aria-hidden="true">
              <div className="hm-apple__recipe">
                <Pictogram name="bowl" size={48} />
                <span>
                  <b>김치찌개</b>
                  <small>한식 · 2인분</small>
                </span>
              </div>
              <ul>
                <li>
                  <Pictogram name="kimchi" size={34} />
                  <span>김치 500g</span>
                  <i className="hm-apple__check" />
                </li>
                <li>
                  <Pictogram name="tofu" size={34} />
                  <span>두부 300g</span>
                  <i className="hm-apple__check" />
                </li>
                <li className="is-off">
                  <Pictogram name="scallion" size={34} />
                  <span>대파 · 집에 있어요</span>
                  <i className="hm-apple__check hm-apple__check--off" />
                </li>
              </ul>
              <div className="hm-apple__bar">재료 2개가 장바구니에 담겼어요</div>
            </div>

            <ol className="hm-apple__steps">
              {MEMBERSHIP_STEPS.map((step, i) => (
                <li key={step.title}>
                  <span>{String(i + 1).padStart(2, '0')}</span>
                  <strong>{step.title}</strong>
                  <p>{step.text}</p>
                </li>
              ))}
            </ol>
          </div>
        </section>
      )}
      {infoOpen && <MembershipInfoModal onClose={() => setInfoOpen(false)} />}

      {member && <FreshProducts />}

      <section className="hm-section">
        <div className="container">
          <div className="hm-head hm-head--center">
            <div>
              <p className="hm-eyebrow">PARTNER MART</p>
              <h2 className="hm-title">우리 동네 가맹 마트</h2>
              <p className="hm-lead">빨간 점은 가맹 마트, 회색 점은 준비 중인 마트예요. 우리 동네에도 있는지 확인해 보세요.</p>
            </div>
          </div>
          <KoreaMap />
          <p className="map-legend">
            <span>
              <i /> 가맹 마트
            </span>
            <span>
              <i className="map-legend__closed" /> 준비 중 마트
            </span>
          </p>
        </div>
      </section>
    </>
  );
}
