import { useEffect, useState } from 'react';
import { api } from '../api/client';

// 시드 데이터(data.sql)의 시연 지역. 법정동 코드를 외우지 않아도 눌러서 채울 수 있게 한다
export const DEMO_REGIONS = [
  { name: '서울 종로구 삼청동', dongName: '삼청동', code: '1111014000' },
  { name: '경기 광명시 하안동', dongName: '하안동', code: '4121010300' },
];

// 카카오(다음) 우편번호 검색. 키 없이 쓰는 공개 스크립트이고 결과에 법정동 코드(bcode)가 들어 있다
let postcodeLoading = null;
function loadPostcode() {
  if (window.daum?.Postcode) return Promise.resolve();
  if (!postcodeLoading) {
    postcodeLoading = new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = 'https://t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js';
      script.onload = resolve;
      script.onerror = () => {
        postcodeLoading = null;
        reject(new Error('주소 검색을 불러오지 못했습니다. 네트워크를 확인해 주세요.'));
      };
      document.head.appendChild(script);
    });
  }
  return postcodeLoading;
}

// 주소 검색창을 열고, 고르면 { address, legalDongCode, dongName }을 넘긴다.
// 법정동 코드는 서버가 요구하는 값이라 화면에 보이지 않게 검색 결과에서 받아 온다
export async function openPostcode(onSelect) {
  await loadPostcode();
  new window.daum.Postcode({
    oncomplete: (data) =>
      onSelect({
        address: data.roadAddress || data.jibunAddress,
        legalDongCode: data.bcode,
        dongName: data.bname,
      }),
  }).open();
}

// 주소가 정해지면 로그인 없이 배송 가능 여부를 확인한다(저장하지 않는다).
// 확인 중이면 { area: null, failed: false }, 조회가 실패하면 { area: null, failed: true }.
// 실패해도 주소 입력은 계속할 수 있어서 호출한 쪽이 안내만 바꾸고, retry()로 같은 주소를 다시 조회한다
export function useServiceArea(legalDongCode) {
  const [result, setResult] = useState({ area: null, failed: false });
  // 법정동 코드가 그대로여도 효과가 다시 돌게 하려고 시도 횟수를 의존성에 둔다
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    setResult({ area: null, failed: false });
    if (!legalDongCode || legalDongCode.length !== 10) return undefined;
    let ignore = false;
    api
      .post('/api/service-area/check', { legalDongCode })
      .then((data) => !ignore && setResult({ area: data, failed: false }))
      .catch(() => !ignore && setResult({ area: null, failed: true }));
    return () => {
      ignore = true;
    };
  }, [legalDongCode, attempt]);

  return { ...result, retry: () => setAttempt((n) => n + 1) };
}
