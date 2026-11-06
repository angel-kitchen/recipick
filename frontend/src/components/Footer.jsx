import { Link } from 'react-router-dom';

const LINKS = [
  { to: '/privacy', label: '개인정보처리방침' },
  { to: '/terms', label: '서비스 이용약관' },
  { to: '/support', label: '고객센터' },
  { to: '/membership-info', label: '멤버십 안내' },
];

export default function Footer() {
  return (
    <footer className="footer">
      <div className="container footer__inner">
        <nav className="footer__links" aria-label="바로가기">
          {LINKS.map((link) => (
            <Link key={link.to} to={link.to}>
              {link.label}
            </Link>
          ))}
        </nav>
        <div className="footer__info">
          <p className="footer__info-title">Recipick 서비스 정보</p>
          <p>팀 : angel-kitchen | 서비스 : 레시피 기반 장보기 · 멤버십 구독</p>
          <p>이메일 : support@recipick.shop | 전화 : 070-0000-0000 (평일 10:00~18:00)</p>
          <p>
            GitHub :{' '}
            <a href="https://github.com/angel-kitchen/recipick" target="_blank" rel="noreferrer">
              github.com/angel-kitchen/recipick
            </a>
          </p>
        </div>
        <p className="footer__copyright">Copyright ©2026 Recipick. All rights reserved.</p>
      </div>
    </footer>
  );
}
