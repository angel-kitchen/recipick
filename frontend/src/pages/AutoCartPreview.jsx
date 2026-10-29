import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { api } from '../api/client';
import AutoCartReview from '../components/AutoCartReview';
import ErrorNotice from '../components/ErrorNotice';

// AI 입력에서 넘어오는 미리보기. 레시피 상세 화면의 자동 장바구니 패널과 같은 확인 부품을 쓴다
export default function AutoCartPreview() {
  const { autoCartId } = useParams();
  const [preview, setPreview] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    api
      .get(`/api/auto-carts/${autoCartId}`)
      .then(setPreview)
      .catch(setError);
  }, [autoCartId]);

  if (!preview) {
    return (
      <div className="container page">
        <ErrorNotice error={error} />
        {!error && <p className="state-message">불러오는 중입니다.</p>}
      </div>
    );
  }

  return (
    <div className="container page">
      <h1 className="page__title">자동 장바구니</h1>
      <p className="page__sub">
        {preview.menus.map((menu) => `${menu.name} ${menu.servings}인분`).join(', ')} · 이용 마트 {preview.martName}
      </p>
      <section className="card">
        <AutoCartReview preview={preview} />
      </section>
    </div>
  );
}
