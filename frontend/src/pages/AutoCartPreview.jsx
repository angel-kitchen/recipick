import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { api } from '../api/client';
import AutoCartReview from '../components/AutoCartReview';
import ErrorNotice from '../components/ErrorNotice';
import { ListSkeleton } from '../components/Skeleton';
import { youtubeThumbnail } from '../youtube';

// AI 입력에서 넘어오는 미리보기. 레시피 상세 화면의 자동 장바구니 패널과 같은 확인 부품을 쓴다
export default function AutoCartPreview() {
  const { autoCartId } = useParams();
  const [preview, setPreview] = useState(null);
  const [error, setError] = useState(null);
  // 레시피 번호 → 썸네일 주소. 못 불러온 레시피는 사진 없이 이름만 보인다
  const [thumbnails, setThumbnails] = useState({});

  useEffect(() => {
    api
      .get(`/api/auto-carts/${autoCartId}`)
      .then(setPreview)
      .catch(setError);
  }, [autoCartId]);

  useEffect(() => {
    if (!preview) return;
    let ignore = false;
    const ids = [...new Set(preview.menus.map((menu) => menu.recipeId))];
    Promise.all(
      ids.map((id) =>
        api
          .get(`/api/recipes/${id}/metadata`)
          .then((recipe) => [id, youtubeThumbnail(recipe.youtubeUrl)])
          .catch(() => [id, null]),
      ),
    ).then((pairs) => !ignore && setThumbnails(Object.fromEntries(pairs)));
    return () => {
      ignore = true;
    };
  }, [preview]);

  if (!preview) {
    return (
      <div className="container page">
        <ErrorNotice error={error} />
        {!error && <ListSkeleton rows={4} />}
      </div>
    );
  }

  return (
    <div className="container page">
      <h1 className="page__title">자동 장바구니</h1>
      <p className="page__sub">이용 마트 {preview.martName}</p>
      <div className="menu-chips">
        {preview.menus.map((menu, index) => (
          <div key={`${menu.recipeId}-${index}`} className="menu-chip">
            {thumbnails[menu.recipeId] ? (
              <img className="menu-chip__image" src={thumbnails[menu.recipeId]} alt="" />
            ) : (
              <span className="menu-chip__image" />
            )}
            <span>
              <strong>{menu.name}</strong> {menu.servings}인분
            </span>
          </div>
        ))}
      </div>
      <section className="card">
        <AutoCartReview preview={preview} />
      </section>
    </div>
  );
}
