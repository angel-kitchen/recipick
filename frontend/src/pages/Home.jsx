import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import KoreaMap from '../components/KoreaMap';

const STEPS = [
  { title: '레시피를 고릅니다', text: '오늘 만들고 싶은 요리와 인분 수를 정합니다.' },
  { title: '재료가 담깁니다', text: '필요한 재료가 가까운 마트의 상품으로 장바구니에 담깁니다.' },
  { title: '마트에서 배송합니다', text: '우리 동네 가맹 마트가 신선한 재료를 보내드립니다.' },
];

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
          <p className="hero__subtitle">레시피를 고르면 장보기는 Recipick이 합니다.</p>
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

      <section className="section">
        <div className="container">
          <h2 className="section__title">세 단계면 끝나요</h2>
          <p className="section__lead">장보기 목록을 적을 필요가 없습니다.</p>
          <ol className="steps">
            {STEPS.map((step, index) => (
              <li key={step.title}>
                <span className="steps__number">0{index + 1}</span>
                <h3 className="steps__title">{step.title}</h3>
                <p className="steps__text">{step.text}</p>
              </li>
            ))}
          </ol>
        </div>
      </section>

      <section className="section section--alt">
        <div className="container">
          <h2 className="section__title">우리 동네 가맹 마트</h2>
          <p className="section__lead">빨간 점은 Recipick과 함께하는 마트, 연한 원은 배송 가능 지역입니다.</p>
          <KoreaMap />
          <p className="map-legend">
            <i /> 가맹 마트
          </p>
        </div>
      </section>
    </>
  );
}
