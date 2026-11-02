// 홈 소개와 빈 이미지 자리에 쓰는 단순한 그림들. 외부 이미지 없이 도형만으로 그려서 어느 크기에서도 선명하다
const GREEN = '#00754a';
const DEEP = '#1e3932';
const LIGHT = '#d4e9e2';
const RED = '#d94b2b';
const LEAF = '#b9dca8';
const LEAF_DARK = '#7fbf6a';
const CREAM = '#fff7df';
const CREAM_SIDE = '#efe2b8';
const CREAM_DARK = '#e2d3a3';

const SHAPES = {
  // 찌개 한 그릇
  bowl: (
    <>
      <path d="M17 13c-2-2 2-4 0-7M24 13c-2-2 2-4 0-7M31 13c-2-2 2-4 0-7" stroke={GREEN} strokeWidth="2" fill="none" strokeLinecap="round" />
      <path d="M6 24h36c0 9-7 16-18 16S6 33 6 24z" fill={RED} />
      <rect x="4" y="21" width="40" height="4" rx="2" fill={DEEP} />
      <circle cx="18" cy="30" r="1.8" fill={CREAM} />
      <circle cx="27" cy="33" r="1.8" fill={CREAM} />
      <circle cx="32" cy="29" r="1.5" fill={LEAF} />
      <rect x="16" y="41" width="16" height="3" rx="1.5" fill={DEEP} />
    </>
  ),
  // 김치(배춧잎에 고춧가루)
  kimchi: (
    <>
      <path d="M24 5C14 7 8 19 11 31c2 8 8 12 13 12s11-4 13-12C40 19 34 7 24 5z" fill={LEAF} />
      <path d="M24 9v32" stroke="#fff" strokeWidth="2.6" strokeLinecap="round" opacity=".85" />
      <path d="M24 19l-8-5M24 26l-9-4M24 33l-8-3M24 19l8-5M24 26l9-4M24 33l8-3" stroke="#fff" strokeWidth="1.8" strokeLinecap="round" opacity=".7" />
      <circle cx="17" cy="27" r="2" fill={RED} />
      <circle cx="31" cy="21" r="2" fill={RED} />
      <circle cx="30" cy="34" r="1.8" fill={RED} />
      <circle cx="19" cy="37" r="1.6" fill={RED} />
    </>
  ),
  // 두부
  tofu: (
    <>
      <path d="M7 19l17-8 17 8-17 8z" fill={CREAM} />
      <path d="M7 19v13l17 8V27z" fill={CREAM_SIDE} />
      <path d="M41 19v13l-17 8V27z" fill={CREAM_DARK} />
    </>
  ),
  // 대파
  scallion: (
    <>
      <rect x="10" y="12" width="8" height="30" rx="4" fill="#f3f6ef" stroke="#cfd8c6" />
      <rect x="10" y="5" width="8" height="21" rx="4" fill={LEAF_DARK} />
      <rect x="20" y="8" width="8" height="34" rx="4" fill="#f3f6ef" stroke="#cfd8c6" />
      <rect x="20" y="3" width="8" height="24" rx="4" fill={LEAF_DARK} />
      <rect x="30" y="13" width="8" height="29" rx="4" fill="#f3f6ef" stroke="#cfd8c6" />
      <rect x="30" y="7" width="8" height="21" rx="4" fill={LEAF_DARK} />
      <path d="M12 42l-1 3M24 42v3M35 42l1 3" stroke="#b9ab8c" strokeWidth="1.6" strokeLinecap="round" />
    </>
  ),
  // 위치 핀
  pin: (
    <>
      <path d="M24 4c-7.5 0-13 5.5-13 12.5C11 26 24 44 24 44s13-18 13-27.5C37 9.5 31.5 4 24 4z" fill={GREEN} />
      <circle cx="24" cy="17" r="5.5" fill="#fff" />
    </>
  ),
  // 배송 트럭
  truck: (
    <>
      <rect x="3" y="14" width="26" height="19" rx="3" fill={GREEN} />
      <path d="M29 20h9l7 8v5H29z" fill={DEEP} />
      <path d="M33 23h4l4 5h-8z" fill={LIGHT} />
      <circle cx="14" cy="35" r="4.5" fill="#fff" stroke={DEEP} strokeWidth="2.4" />
      <circle cx="37" cy="35" r="4.5" fill="#fff" stroke={DEEP} strokeWidth="2.4" />
      <path d="M9 20h12M9 25h8" stroke="#fff" strokeWidth="2" strokeLinecap="round" opacity=".7" />
    </>
  ),
  // 집
  house: (
    <>
      <path d="M7 23L24 9l17 14v17a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2z" fill={LIGHT} />
      <path d="M4 25L24 7l20 18" stroke={DEEP} strokeWidth="3" fill="none" strokeLinecap="round" strokeLinejoin="round" />
      <rect x="19" y="28" width="10" height="14" rx="2" fill={GREEN} />
    </>
  ),
  // 사진이 없을 때 쓰는 접시와 포크·숟가락
  plate: (
    <>
      <circle cx="24" cy="24" r="12" fill="#fff" stroke={LEAF_DARK} strokeWidth="2.4" />
      <circle cx="24" cy="24" r="7" fill="none" stroke={LEAF_DARK} strokeWidth="1.6" opacity=".6" />
      <path d="M5 8v8a3 3 0 0 0 6 0V8M8 8v32" stroke={DEEP} strokeWidth="2" strokeLinecap="round" fill="none" opacity=".55" />
      <ellipse cx="41" cy="14" rx="3.5" ry="6" fill="none" stroke={DEEP} strokeWidth="2" opacity=".55" />
      <path d="M41 20v20" stroke={DEEP} strokeWidth="2" strokeLinecap="round" opacity=".55" />
    </>
  ),
};

export default function Pictogram({ name, size = 40 }) {
  return (
    <svg className="pictogram" width={size} height={size} viewBox="0 0 48 48" aria-hidden="true" focusable="false">
      {SHAPES[name]}
    </svg>
  );
}
