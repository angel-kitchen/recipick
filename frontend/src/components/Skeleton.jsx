// 불러오는 동안 글자 대신 회색 뼈대를 깜빡여 보여 준다. 카드 목록용과 줄 목록용 두 가지다
export function CardSkeletons({ count = 6 }) {
  return (
    <div className="grid" aria-busy="true" aria-label="불러오는 중">
      {Array.from({ length: count }, (_, index) => (
        <div key={index} className="tile skeleton-card">
          <div className="tile__image skeleton" />
          <div className="tile__body">
            <span className="skeleton skeleton--line" style={{ width: '70%' }} />
            <span className="skeleton skeleton--line" style={{ width: '45%' }} />
          </div>
        </div>
      ))}
    </div>
  );
}

export function ListSkeleton({ rows = 4 }) {
  return (
    <section className="card" aria-busy="true" aria-label="불러오는 중">
      {Array.from({ length: rows }, (_, index) => (
        <div key={index} className="order-row">
          <span className="skeleton skeleton--line" style={{ width: '60%' }} />
          <span className="skeleton skeleton--line" style={{ width: 80 }} />
        </div>
      ))}
    </section>
  );
}
