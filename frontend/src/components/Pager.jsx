// 서버 페이지는 0부터 시작하지만 화면에는 1부터 보여준다
export default function Pager({ page, totalPages, onChange }) {
  if (totalPages <= 1) return null;
  return (
    <div className="pager">
      <button type="button" className="btn btn--outline" disabled={page <= 0} onClick={() => onChange(page - 1)}>
        이전
      </button>
      <span>
        {page + 1} / {totalPages}
      </span>
      <button
        type="button"
        className="btn btn--outline"
        disabled={page + 1 >= totalPages}
        onClick={() => onChange(page + 1)}
      >
        다음
      </button>
    </div>
  );
}
