import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import ErrorNotice from '../components/ErrorNotice';
import Pager from '../components/Pager';
import { formatWon } from '../labels';

const PERIODS = { DAILY: '최근 24시간', WEEKLY: '최근 7일', MONTHLY: '최근 30일', ALL: '전체' };
export default function Rankings({ products = false }) {
    const [mode, setMode] = useState('popular');
    const [period, setPeriod] = useState('WEEKLY');
    const [page, setPage] = useState(0);
    const [data, setData] = useState(null);
    const [error, setError] = useState(null);
    useEffect(() => {
        let ignore = false;
        setData(null);
        setError(null);
        api.get(products ? '/api/products/best' : `/api/recipes/${mode}`, {
            period: products || mode === 'popular' ? period : undefined,
            page,
            size: 20,
        })
            .then((value) => !ignore && setData(value))
            .catch((e) => !ignore && setError(e));
        return () => {
            ignore = true;
        };
    }, [products, mode, period, page]);
    return (
        <div className="container page">
            <h1 className="page__title">{products ? '베스트 상품' : '레시피 순위'}</h1>
            <div className="filters">
                {!products && (
                    <select
                        className="field__input"
                        aria-label="순위 기준"
                        value={mode}
                        onChange={(e) => {
                            setMode(e.target.value);
                            setPage(0);
                        }}
                    >
                        <option value="popular">찜 많은 순</option>
                        <option value="most-viewed">전체 조회수 순</option>
                    </select>
                )}
                {(products || mode === 'popular') && (
                    <select
                        className="field__input"
                        aria-label="순위 기간"
                        value={period}
                        onChange={(e) => {
                            setPeriod(e.target.value);
                            setPage(0);
                        }}
                    >
                        {Object.entries(PERIODS).map(([key, label]) => (
                            <option key={key} value={key}>
                                {label}
                            </option>
                        ))}
                    </select>
                )}
            </div>
            <ErrorNotice error={error} />
            <ol className="list-plain" start={page * 20 + 1}>
                {data?.content.map((item, index) => (
                    <li className="card" key={products ? item.productId : item.recipeId}>
                        <span>{page * 20 + index + 1}위 </span>
                        <Link to={products ? `/products/${item.productId}` : `/recipes/${item.recipeId}`}>
                            {item.name}
                        </Link>
                        <p>
                            {products
                                ? `${formatWon(item.salePrice ?? item.price)}${item.status === 'STOPPED' ? ' · 판매 중지' : item.soldOut ? ' · 품절' : ''}`
                                : mode === 'popular'
                                  ? `찜 ${item.bookmarkCount}개`
                                  : `조회 ${item.viewCount}회`}
                        </p>
                    </li>
                ))}
            </ol>
            {data?.content.length === 0 && <p className="state-message">해당 기간의 순위가 없어요.</p>}
            {data && <Pager page={data.page} totalPages={data.totalPages} onChange={setPage} />}
        </div>
    );
}
