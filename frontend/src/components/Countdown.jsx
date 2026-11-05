import { useEffect, useRef, useState } from 'react';
import { serverNow } from '../api/client';

const pad = (n) => String(n).padStart(2, '0');

// 하루 넘게 남으면 일수를 앞에 붙인다(예: 3일 04:11:46)
export function formatRemaining(ms) {
  const total = Math.max(0, Math.floor(ms / 1000));
  const days = Math.floor(total / 86400);
  const hours = Math.floor((total % 86400) / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const seconds = total % 60;
  return `${days > 0 ? `${days}일 ` : ''}${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;
}

// endAt까지 남은 시간을 서버 시각 기준으로 1초마다 보여 주고, 끝나면 onEnd를 한 번 부른다
export default function Countdown({ endAt, onEnd, className = 'countdown' }) {
  const end = Date.parse(endAt);
  const [remain, setRemain] = useState(() => end - serverNow());
  const onEndRef = useRef(onEnd);
  onEndRef.current = onEnd;

  useEffect(() => {
    const update = () => end - serverNow();
    setRemain(update());
    const timer = setInterval(() => {
      const left = update();
      setRemain(left);
      if (left <= 0) {
        clearInterval(timer);
        onEndRef.current?.();
      }
    }, 1000);
    return () => clearInterval(timer);
  }, [end]);

  if (Number.isNaN(end)) return null;
  if (remain <= 0) return <span className={className}>특가 종료</span>;
  return (
    <span className={className}>
      <span aria-hidden="true">⏱ </span>
      {formatRemaining(remain)} 남음
    </span>
  );
}
