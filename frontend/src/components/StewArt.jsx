// 보글보글 끓는 김치찌개 일러스트. 뚝배기 안에 김치·돼지고기·두부·대파가 들어 있고, 김이 오르고 아래에서 불꽃이 일렁인다.
// 설명용 그림이라 스크린리더는 건너뛴다. 움직임은 membership-intro.css 의 stew-* 애니메이션이다
const RED = '#d4361d';
const RED_LIGHT = '#ef6a45';

function Kimchi({ x, y, r = 0 }) {
  return (
    <g transform={`translate(${x} ${y}) rotate(${r})`}>
      <rect x="-24" y="-10" width="48" height="20" rx="9" fill="#f3e8c4" stroke="#c63a1c" strokeWidth="3" />
      <path d="M-14 -3h26M-12 4h22" stroke="#e8b6a2" strokeWidth="2.5" strokeLinecap="round" />
    </g>
  );
}

function Pork({ x, y, r = 0 }) {
  return (
    <g transform={`translate(${x} ${y}) rotate(${r})`}>
      <rect x="-22" y="-10" width="44" height="20" rx="9" fill="#f4a3a0" />
      <path d="M-14 -2c8-6 18-6 28 0" stroke="#fde7e3" strokeWidth="3" fill="none" strokeLinecap="round" />
    </g>
  );
}

function Tofu({ x, y, r = 0 }) {
  return (
    <g transform={`translate(${x} ${y}) rotate(${r})`}>
      <rect x="-15" y="-15" width="30" height="30" rx="6" fill="#fffaf0" />
      <rect x="-15" y="-15" width="30" height="30" rx="6" fill="none" stroke="#efe1bd" strokeWidth="2" />
    </g>
  );
}

function Scallion({ x, y }) {
  return (
    <g transform={`translate(${x} ${y})`}>
      <ellipse rx="10" ry="6" fill="#9ccc7a" />
      <ellipse rx="5" ry="3" fill="#e6f4d8" />
    </g>
  );
}

export default function StewArt() {
  return (
    <svg className="stew" viewBox="0 0 480 420" aria-hidden="true" focusable="false">
      {/* 김 */}
      <g className="stew-steam" fill="none" stroke="#fff" strokeWidth="7" strokeLinecap="round">
        <path className="stew-steam__a" d="M170 126c-14-16 14-30 0-48s12-26 0-40" />
        <path className="stew-steam__b" d="M240 118c-14-16 14-30 0-48s12-26 0-40" />
        <path className="stew-steam__c" d="M310 126c-14-16 14-30 0-48s12-26 0-40" />
      </g>

      {/* 뚝배기 손잡이와 몸통 */}
      <rect x="62" y="196" width="40" height="22" rx="11" fill="#3d302b" />
      <rect x="378" y="196" width="40" height="22" rx="11" fill="#3d302b" />
      <path d="M84 188Q88 322 240 332Q392 322 396 188Z" fill="#2b2220" />
      <path d="M112 222Q118 300 190 318" fill="none" stroke="#4a3b35" strokeWidth="8" strokeLinecap="round" opacity=".7" />

      {/* 입구 테두리와 국물 */}
      <ellipse cx="240" cy="188" rx="158" ry="42" fill="#3d302b" />
      <ellipse cx="240" cy="190" rx="140" ry="33" fill={RED} />
      <ellipse cx="240" cy="190" rx="140" ry="33" fill="none" stroke={RED_LIGHT} strokeWidth="3" opacity=".7" />
      <path d="M130 196c30 12 90 16 130 14" stroke={RED_LIGHT} strokeWidth="4" fill="none" strokeLinecap="round" opacity=".6" />

      {/* 건더기 */}
      <Kimchi x="168" y="184" r="-12" />
      <Kimchi x="298" y="196" r="10" />
      <Kimchi x="236" y="204" r="-4" />
      <Pork x="206" y="180" r="8" />
      <Pork x="322" y="180" r="-10" />
      <Tofu x="262" y="178" r="8" />
      <Tofu x="148" y="198" r="-10" />
      <Scallion x="196" y="204" />
      <Scallion x="282" y="210" />
      <Scallion x="336" y="196" />
      <Scallion x="214" y="170" />
      <g fill="#8d1d10" opacity=".8">
        <circle cx="188" cy="192" r="2.6" />
        <circle cx="254" cy="196" r="2.6" />
        <circle cx="308" cy="186" r="2.6" />
        <circle cx="228" cy="214" r="2.6" />
        <circle cx="172" cy="206" r="2.6" />
      </g>

      {/* 보글보글 거품 */}
      <g className="stew-bubbles" fill={RED_LIGHT} stroke="#ffb59f" strokeWidth="2">
        <circle className="stew-bubble stew-bubble--a" cx="154" cy="178" r="9" />
        <circle className="stew-bubble stew-bubble--b" cx="228" cy="166" r="11" />
        <circle className="stew-bubble stew-bubble--c" cx="288" cy="170" r="8" />
        <circle className="stew-bubble stew-bubble--d" cx="332" cy="192" r="10" />
        <circle className="stew-bubble stew-bubble--e" cx="200" cy="212" r="7" />
        <circle className="stew-bubble stew-bubble--f" cx="266" cy="214" r="9" />
      </g>

      {/* 받침과 불꽃 */}
      <rect x="150" y="334" width="180" height="14" rx="7" fill="#fff" opacity=".9" />
      <rect x="120" y="348" width="240" height="12" rx="6" fill="#7ba05b" />
      <g className="stew-flames">
        <path className="stew-flame stew-flame--a" d="M186 392c-12-10-8-22 0-32 2 8 10 12 10 22 0 6-4 10-10 10z" fill="#ff9a3c" />
        <path className="stew-flame stew-flame--b" d="M240 396c-14-12-9-26 0-38 3 10 12 14 12 26 0 7-5 12-12 12z" fill="#ffc247" />
        <path className="stew-flame stew-flame--c" d="M294 392c-12-10-8-22 0-32 2 8 10 12 10 22 0 6-4 10-10 10z" fill="#ff9a3c" />
      </g>
    </svg>
  );
}
