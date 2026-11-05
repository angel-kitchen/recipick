// AI 답이 올 때까지 레피가 먼지를 일으키며 달려가는 모습을 보여 준다. 움직임은 recipe-chat.css의 repi-* 애니메이션이다
function Puff() {
  return (
    <svg className="repi-puff" viewBox="0 0 30 22" aria-hidden="true">
      <circle cx="9" cy="14" r="7" />
      <circle cx="17" cy="10" r="8" />
      <circle cx="23" cy="15" r="6" />
      <path d="M5 11 H11 M3 15 H9 M6 19 H12" />
    </svg>
  );
}

export default function RepiSearching({ message = '레피가 열심히 레시피를 찾고 있어요' }) {
  return (
    <div className="repi-run" role="status">
      <div className="repi-run__track" aria-hidden="true">
        <div className="repi-run__runner">
          <Puff />
          <Puff />
          <Puff />
          <svg className="repi-run__chef" viewBox="0 0 96 64">
            <g className="repi-run__body">
              {/* 양손을 위로 든 모습. 뒤 팔은 왼쪽 위로, 앞 팔은 프라이팬을 들고 오른쪽 위로 뻗는다 */}
              <path className="chef-line" d="M52 41 L45 30" strokeWidth="3.4" strokeLinecap="round" fill="none" />
              <circle className="chef-line" cx="45" cy="30" r="3" fill="#f8ddc0" strokeWidth="1.2" />
              <rect className="chef-line chef-leaf" x="50" y="38" width="16" height="15" rx="5" strokeWidth="1.6" />
              <circle className="chef-line" cx="54" cy="12" r="5" fill="#fff" strokeWidth="1.5" />
              <circle className="chef-line" cx="60" cy="9" r="6" fill="#fff" strokeWidth="1.5" />
              <circle className="chef-line" cx="66" cy="12" r="5" fill="#fff" strokeWidth="1.5" />
              <rect x="51" y="13" width="18" height="7" fill="#fff" />
              <circle className="chef-line" cx="60" cy="28" r="11" fill="#f8ddc0" strokeWidth="1.6" />
              <rect className="chef-fill" x="49" y="19" width="22" height="5" rx="2" />
              <circle className="chef-fill" cx="56" cy="29" r="1.7" />
              <circle className="chef-fill" cx="65" cy="29" r="1.7" />
              <path className="chef-line" d="M57 34 Q61 38 65 34" strokeWidth="1.7" fill="none" strokeLinecap="round" />
              <circle cx="52" cy="33" r="2.2" fill="#f4b6a0" opacity=".75" />
              <circle cx="69" cy="33" r="2.2" fill="#f4b6a0" opacity=".75" />
              <path className="chef-line" d="M64 41 L74 31" strokeWidth="3.4" strokeLinecap="round" fill="none" />
              <g transform="rotate(-12 74 31)">
                <path d="M74 31 L79 29" stroke="#3a4a44" strokeWidth="3" strokeLinecap="round" />
                <ellipse className="chef-line" cx="88" cy="27" rx="10" ry="3.9" fill="#3a4a44" strokeWidth="1.2" />
                <ellipse cx="88" cy="26.5" rx="7.6" ry="2.7" fill="#5a6b64" />
                <ellipse cx="87" cy="25.4" rx="5" ry="2" fill="#fff" />
                <circle cx="87" cy="25.2" r="1.6" fill="#f2c94c" />
              </g>
              <circle className="chef-line" cx="74" cy="31" r="3" fill="#f8ddc0" strokeWidth="1.2" />
            </g>
            <g className="repi-run__legs">
              <circle className="chef-line" cx="58" cy="56" r="8.5" fill="none" strokeWidth="1.6" strokeDasharray="5 3" />
              <path className="chef-line" d="M58 47.5 C64 52 64 60 58 64.5" fill="none" strokeWidth="2.4" strokeLinecap="round" />
              <path className="chef-line" d="M58 64.5 C52 60 52 52 58 47.5" fill="none" strokeWidth="2.4" strokeLinecap="round" opacity=".55" />
              <path className="chef-line" d="M49.5 56 C54 50 62 50 66.5 56" fill="none" strokeWidth="2.4" strokeLinecap="round" opacity=".55" />
            </g>
          </svg>
        </div>
      </div>
      <p className="repi-run__text">{message}</p>
    </div>
  );
}
