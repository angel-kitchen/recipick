import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import KoreaMap from '../components/KoreaMap';
import Pictogram from '../components/Pictogram';

// 처음 이용하는 사람도 흐름을 알 수 있게, 단계마다 실제 화면을 단순하게 줄인 그림과 풀어 쓴 설명을 함께 보여 준다
const STEPS = [
  {
    title: '먹고 싶은 요리를 골라요',
    text: '레시피 목록에서 요리와 인분 수만 정해요. 그 요리에 필요한 재료는 Recipick이 알아서 정리해요.',
  },
  {
    title: '재료가 알아서 담겨요',
    text: '필요한 양을 계산해서 우리 동네 마트 상품으로 장바구니를 채워 줘요. 집에 있는 재료는 체크만 빼면 돼요.',
  },
  {
    title: '집 앞까지 배송돼요',
    text: '주문하면 우리 동네 마트가 재료를 챙겨서 보내 줘요. 처음에 배송받을 동네만 정해 두면 돼요.',
  },
];

// 아래 세 그림은 눌러 볼 수 있는 화면이 아니라 설명용이라 스크린리더가 읽지 않게 숨긴다
function RecipeMock() {
  return (
    <div className="mock" aria-hidden="true">
      <div className="mock__bar">
        <i />
        <i />
        <i />
      </div>
      <div className="mock__body">
        <div className="mock__recipe">
          <Pictogram name="bowl" size={46} />
          <div>
            <b>김치찌개</b>
            <span>한식 · 30분</span>
          </div>
        </div>
        <div className="mock__line">
          <span>인분</span>
          <span className="mock__stepper">
            <i>−</i>
            <b>2</b>
            <i>+</i>
          </span>
        </div>
        <div className="mock__button">재료 자동 담기</div>
      </div>
    </div>
  );
}

function CartMock() {
  return (
    <div className="mock" aria-hidden="true">
      <div className="mock__bar">
        <i />
        <i />
        <i />
      </div>
      <div className="mock__body">
        <div className="mock__item">
          <span className="mock__check is-on" />
          <Pictogram name="kimchi" size={32} />
          <span className="mock__name">김치 500g</span>
          <b>6,900원</b>
        </div>
        <div className="mock__item">
          <span className="mock__check is-on" />
          <Pictogram name="tofu" size={32} />
          <span className="mock__name">두부 300g</span>
          <b>2,300원</b>
        </div>
        <div className="mock__item is-off">
          <span className="mock__check" />
          <Pictogram name="scallion" size={32} />
          <span className="mock__name">대파 (집에 있어요)</span>
          <b>-</b>
        </div>
        <div className="mock__button">2개 장바구니에 담기</div>
      </div>
    </div>
  );
}

function DeliveryMock() {
  return (
    <div className="mock" aria-hidden="true">
      <div className="mock__bar">
        <i />
        <i />
        <i />
      </div>
      <div className="mock__body">
        <div className="mock__place">
          <Pictogram name="pin" size={30} />
          <span>서울 종로구 삼청동</span>
        </div>
        <div className="mock__chip">배송 가능 · 삼청 마트에서 보내요</div>
        <div className="mock__route">
          <Pictogram name="truck" size={44} />
          <span className="mock__dots" />
          <Pictogram name="house" size={44} />
        </div>
      </div>
    </div>
  );
}

const MOCKS = [RecipeMock, CartMock, DeliveryMock];

export default function Home() {
  const { member } = useAuth();
  // public/videos/hero.mp4가 없으면 단색 바탕과 문구만 보인다
  const [videoFailed, setVideoFailed] = useState(false);

  return (
    <>
      <section className="hero">
        {!videoFailed && (
          <video
            className="hero__video"
            src="/videos/hero.mp4"
            autoPlay
            muted
            loop
            playsInline
            onError={() => setVideoFailed(true)}
          />
        )}
        <div className="hero__overlay" />
        <div className="hero__content">
          <h1 className="hero__title">오늘 뭐 먹지, 재료까지 한 번에</h1>
          <p className="hero__subtitle">레시피를 고르면 장보기는 Recipick이 해 드려요.</p>
          <div className="hero__actions">
            {member ? (
              <>
                <Link to="/recipes" className="tbtn tbtn--primary">
                  레시피 보기
                </Link>
                <Link to="/cart" className="tbtn tbtn--secondary">
                  장바구니
                </Link>
              </>
            ) : (
              <>
                <Link to="/start" className="tbtn tbtn--primary">
                  시작하기
                </Link>
                <Link to="/login" className="tbtn tbtn--secondary">
                  로그인
                </Link>
              </>
            )}
          </div>
        </div>
      </section>

      <section className="section section--steps">
        <div className="container">
          <h2 className="section__title">세 단계면 끝나요</h2>
          <p className="section__lead">장보기 목록을 적거나 재료를 하나씩 찾지 않아도 돼요.</p>
          <ol className="steps">
            {STEPS.map((step, index) => {
              const Mock = MOCKS[index];
              return (
                <li key={step.title} className="step">
                  <Mock />
                  <span className="steps__number">{index + 1}</span>
                  <h3 className="steps__title">{step.title}</h3>
                  <p className="steps__text">{step.text}</p>
                </li>
              );
            })}
          </ol>
          <div className="steps__cta">
            {member ? (
              <>
                <p className="steps__cta-text">오늘 만들고 싶은 요리를 골라 볼까요?</p>
                <Link to="/recipes" className="tbtn tbtn--primary">
                  레시피 보러 가기
                </Link>
              </>
            ) : (
              <>
                <p className="steps__cta-text">먼저 우리 동네가 서비스 지역인지 확인해 보세요.</p>
                <Link to="/start" className="tbtn tbtn--primary">
                  내 동네 확인하기
                </Link>
              </>
            )}
          </div>
        </div>
      </section>

      <section className="section section--alt">
        <div className="container">
          <h2 className="section__title">우리 동네 가맹 마트</h2>
          <p className="section__lead">빨간 점은 Recipick과 함께하는 마트예요. 우리 동네에도 있는지 확인해 보세요.</p>
          <KoreaMap />
          <p className="map-legend">
            <i /> 가맹 마트
          </p>
        </div>
      </section>
    </>
  );
}
