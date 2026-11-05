// AI 레시피 도우미 "레피". 요리사 모자를 쓴 얼굴을 직접 그린 그림이다
// 정글·리프 색은 recipe-chat.css에서 토큰으로 칠해, 색을 바꿀 때 theme.css 한 곳만 고치면 된다
export default function ChefAvatar({ size = 44 }) {
  return (
    <span className="chat-avatar" style={{ '--size': `${size}px` }} aria-hidden="true">
      <svg viewBox="0 0 64 64">
        <circle className="chef-line" cx="24" cy="17" r="7" fill="#fff" strokeWidth="1.6" />
        <circle className="chef-line" cx="32" cy="12" r="8" fill="#fff" strokeWidth="1.6" />
        <circle className="chef-line" cx="40" cy="17" r="7" fill="#fff" strokeWidth="1.6" />
        <rect x="21" y="18" width="22" height="9" fill="#fff" />
        <circle className="chef-line" cx="32" cy="39" r="15" fill="#f8ddc0" strokeWidth="1.6" />
        <rect className="chef-fill" x="19" y="24" width="26" height="6" rx="2" />
        <circle className="chef-fill" cx="26.5" cy="39" r="1.9" />
        <circle className="chef-fill" cx="37.5" cy="39" r="1.9" />
        <path className="chef-line" d="M27.5 44.5 Q32 49 36.5 44.5" strokeWidth="1.8" fill="none" strokeLinecap="round" />
        <circle cx="22.5" cy="44" r="2.6" fill="#f4b6a0" opacity=".75" />
        <circle cx="41.5" cy="44" r="2.6" fill="#f4b6a0" opacity=".75" />
        <path className="chef-leaf" d="M21 54 Q32 60 43 54 L41 64 L23 64Z" />
      </svg>
    </span>
  );
}
