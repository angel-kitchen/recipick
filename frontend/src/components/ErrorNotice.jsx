// 서버 message를 그대로 보여주고, 입력 검증 실패면 항목별 사유(field, reason)도 함께 보여준다
export default function ErrorNotice({ error }) {
  if (!error) return null;
  return (
    <div className="notice" role="alert">
      {error.message}
      {error.errors?.length > 0 && (
        <ul>
          {error.errors.map((item) => (
            <li key={`${item.field}-${item.reason}`}>
              {item.field}: {item.reason}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
