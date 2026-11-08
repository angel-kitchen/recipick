import { useEffect, useRef, useState } from 'react';

const WINDOW_SIZE = 10;

// 서버 페이지는 0부터 시작하지만 화면에는 1부터 보여준다.
// 번호는 10개씩 묶어 보여 주고, 10쪽이 넘으면 "…"을 눌러 원하는 쪽으로 바로 갈 수 있다
export default function Pager({ page, totalPages, onChange }) {
  const [jumpOpen, setJumpOpen] = useState(false);
  const [jumpValue, setJumpValue] = useState('');
  const jumpRef = useRef(null);

  // 입력 창 밖을 누르거나 Esc를 누르면 닫는다
  useEffect(() => {
    if (!jumpOpen) return undefined;
    const close = (e) => {
      if (e.type === 'keydown' ? e.key === 'Escape' : !jumpRef.current?.contains(e.target)) setJumpOpen(false);
    };
    document.addEventListener('mousedown', close);
    document.addEventListener('keydown', close);
    return () => {
      document.removeEventListener('mousedown', close);
      document.removeEventListener('keydown', close);
    };
  }, [jumpOpen]);

  if (totalPages <= 1) return null;

  const start = Math.floor(page / WINDOW_SIZE) * WINDOW_SIZE;
  const numbers = Array.from({ length: Math.min(WINDOW_SIZE, totalPages - start) }, (_, i) => start + i);

  // 범위(1~전체)를 벗어나거나 소수·빈 값이면 입력 칸의 기본 검증이 제출을 막는다
  const jump = (e) => {
    e.preventDefault();
    onChange(Number(jumpValue) - 1);
    setJumpOpen(false);
  };

  return (
    <nav className="pager" aria-label="페이지 이동">
      <button type="button" className="pager__step" disabled={page <= 0} onClick={() => onChange(page - 1)}>
        ‹ 이전
      </button>
      {numbers.map((n) => (
        <button
          key={n}
          type="button"
          className={n === page ? 'pager__num pager__num--current' : 'pager__num'}
          aria-label={`${n + 1}쪽`}
          aria-current={n === page ? 'page' : undefined}
          onClick={() => onChange(n)}
        >
          {n + 1}
        </button>
      ))}
      {totalPages > WINDOW_SIZE && (
        <span className="pager__jump" ref={jumpRef}>
          <button
            type="button"
            className="pager__num"
            aria-label="원하는 쪽으로 이동"
            aria-expanded={jumpOpen}
            onClick={() => {
              setJumpValue(String(page + 1));
              setJumpOpen((open) => !open);
            }}
          >
            …
          </button>
          {jumpOpen && (
            <form className="pager__popover" onSubmit={jump}>
              <input
                type="number"
                className="pager__input"
                min={1}
                max={totalPages}
                required
                value={jumpValue}
                onChange={(e) => setJumpValue(e.target.value)}
                aria-label="이동할 쪽 번호"
                autoFocus
              />
              <span>/ {totalPages}</span>
              <button type="submit" className="btn btn--outline">
                이동
              </button>
            </form>
          )}
        </span>
      )}
      <button type="button" className="pager__step" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>
        다음 ›
      </button>
    </nav>
  );
}
