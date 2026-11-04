import { useEffect, useState } from 'react';
import { api } from '../api/client';
import AutoCartReview from '../components/AutoCartReview';
import ErrorNotice from '../components/ErrorNotice';
import Pager from '../components/Pager';

export default function AutoCartBuilder() {
    const [keyword, setKeyword] = useState('');
    const [page, setPage] = useState(0);
    const [data, setData] = useState(null);
    const [menus, setMenus] = useState([]);
    const [preview, setPreview] = useState(null);
    const [error, setError] = useState(null);
    const [busy, setBusy] = useState(false);
    useEffect(() => {
        let ignore = false;
        api.get('/api/recipes', { keyword, page, size: 10 })
            .then((value) => !ignore && setData(value))
            .catch((e) => !ignore && setError(e));
        return () => {
            ignore = true;
        };
    }, [keyword, page]);
    const changeMenus = (next) => {
        setMenus(next);
        setPreview(null);
    };
    const createPreview = async () => {
        setError(null);
        setBusy(true);
        setPreview(null);
        try {
            setPreview(
                await api.post('/api/auto-carts/preview', {
                    menus: menus.map(({ recipeId, servings }) => ({ recipeId, servings, optionalIngredientIds: [] })),
                }),
            );
        } catch (e) {
            setError(e);
        } finally {
            setBusy(false);
        }
    };
    return (
        <div className="container page">
            <h1 className="page__title">메뉴를 골라 장보기</h1>
            <p>메뉴 1~5개와 각 1~10인분을 골라 필요한 재료를 함께 계산해요.</p>
            <ErrorNotice error={error} />
            <fieldset disabled={busy} style={{ border: 0, padding: 0 }}>
                <input
                    className="field__input"
                    placeholder="레시피 이름 검색"
                    value={keyword}
                    onChange={(e) => {
                        setKeyword(e.target.value);
                        setPage(0);
                    }}
                />
                <ul className="list-plain">
                    {data?.content.map((recipe) => (
                        <li key={recipe.recipeId}>
                            {recipe.name}{' '}
                            <button
                                className="chip"
                                disabled={
                                    !recipe.autoCartAvailable ||
                                    menus.length >= 5 ||
                                    menus.some((item) => item.recipeId === recipe.recipeId)
                                }
                                onClick={() =>
                                    changeMenus([
                                        ...menus,
                                        {
                                            recipeId: recipe.recipeId,
                                            name: recipe.name,
                                            servings: Math.max(1, Math.min(10, recipe.baseServings)),
                                        },
                                    ])
                                }
                            >
                                메뉴 선택
                            </button>
                        </li>
                    ))}
                </ul>
                {data && <Pager page={data.page} totalPages={data.totalPages} onChange={setPage} />}
                <section className="card">
                    <h2 className="tile__title">선택한 메뉴 ({menus.length}/5)</h2>
                    {menus.map((menu) => (
                        <div className="filters" key={menu.recipeId}>
                            <span>{menu.name}</span>
                            <label>
                                인분{' '}
                                <select
                                    className="field__input"
                                    value={menu.servings}
                                    onChange={(e) =>
                                        changeMenus(
                                            menus.map((item) =>
                                                item.recipeId === menu.recipeId
                                                    ? { ...item, servings: Number(e.target.value) }
                                                    : item,
                                            ),
                                        )
                                    }
                                >
                                    {Array.from({ length: 10 }, (_, i) => (
                                        <option key={i + 1} value={i + 1}>
                                            {i + 1}인분
                                        </option>
                                    ))}
                                </select>
                            </label>
                            <button
                                className="chip"
                                onClick={() => changeMenus(menus.filter((item) => item.recipeId !== menu.recipeId))}
                            >
                                제외
                            </button>
                        </div>
                    ))}
                    <button className="btn" disabled={menus.length === 0} onClick={createPreview}>
                        합산 재료 미리보기
                    </button>
                </section>
            </fieldset>
            {preview && (
                <section className="card">
                    <AutoCartReview key={preview.autoCartRequestId} preview={preview} />
                </section>
            )}
        </div>
    );
}
