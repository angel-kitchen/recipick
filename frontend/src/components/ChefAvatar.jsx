// AI 레시피 도우미 "레피". 요리사 모자를 쓴 얼굴을 직접 그린 그림이다
export default function ChefAvatar({ size = 44 }) {
  return (
    <span className="chat-avatar" style={{ '--size': `${size}px` }} aria-hidden="true">
      <svg viewBox="0 0 64 64">
        <circle cx="24" cy="17" r="7" fill="#fff" stroke="#0d4c3c" strokeWidth="1.6" />
        <circle cx="32" cy="12" r="8" fill="#fff" stroke="#0d4c3c" strokeWidth="1.6" />
        <circle cx="40" cy="17" r="7" fill="#fff" stroke="#0d4c3c" strokeWidth="1.6" />
        <rect x="21" y="18" width="22" height="9" fill="#fff" />
        <circle cx="32" cy="39" r="15" fill="#f8ddc0" stroke="#0d4c3c" strokeWidth="1.6" />
        <rect x="19" y="24" width="26" height="6" rx="2" fill="#0d4c3c" />
        <circle cx="26.5" cy="39" r="1.9" fill="#0d4c3c" />
        <circle cx="37.5" cy="39" r="1.9" fill="#0d4c3c" />
        <path d="M27.5 44.5 Q32 49 36.5 44.5" stroke="#0d4c3c" strokeWidth="1.8" fill="none" strokeLinecap="round" />
        <circle cx="22.5" cy="44" r="2.6" fill="#f4b6a0" opacity=".75" />
        <circle cx="41.5" cy="44" r="2.6" fill="#f4b6a0" opacity=".75" />
        <path d="M21 54 Q32 60 43 54 L41 64 L23 64Z" fill="#7ba05b" />
      </svg>
    </span>
  );
}
