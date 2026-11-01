// 카카오 지도 JavaScript SDK의 주소 변환(services) 기능. 좌표를 주소와 법정동 코드로 바꾼다.
// 키는 브라우저에 노출되는 값이고 카카오 개발자 사이트에 등록한 도메인에서만 동작한다(.env.local의 VITE_KAKAO_JS_KEY)
const KEY = import.meta.env.VITE_KAKAO_JS_KEY;

export const kakaoAvailable = Boolean(KEY);

let sdkLoading = null;
export function loadKakao() {
  if (window.kakao?.maps?.services) return Promise.resolve();
  if (!KEY) return Promise.reject(new Error('현재 위치 기능을 사용할 수 없어요. (지도 키가 설정되지 않았어요)'));
  if (!sdkLoading) {
    sdkLoading = new Promise((resolve, reject) => {
      const script = document.createElement('script');
      // autoload=false: 스크립트만 받고 load()로 준비가 끝났을 때 이어서 쓴다
      script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${KEY}&libraries=services&autoload=false`;
      script.onload = () => window.kakao.maps.load(resolve);
      script.onerror = () => {
        sdkLoading = null;
        reject(new Error('지도 서비스를 불러오지 못했어요. 네트워크를 확인해 주세요.'));
      };
      document.head.appendChild(script);
    });
  }
  return sdkLoading;
}

const geocoder = () => new window.kakao.maps.services.Geocoder();

// 카카오 메서드는 (경도, 위도) 순서로 받는다
function coord2RegionCode(lat, lng) {
  return new Promise((resolve, reject) => {
    geocoder().coord2RegionCode(lng, lat, (result, status) => {
      if (status === window.kakao.maps.services.Status.OK) resolve(result);
      else if (status === window.kakao.maps.services.Status.ZERO_RESULT) resolve([]);
      else reject(new Error('이 위치의 지역 정보를 가져오지 못했어요.'));
    });
  });
}

function coord2Address(lat, lng) {
  return new Promise((resolve) => {
    geocoder().coord2Address(lng, lat, (result, status) => {
      // 도로명 주소는 없을 수 있고, 주소 문장 변환이 실패해도 법정동만 있으면 계속할 수 있다
      resolve(status === window.kakao.maps.services.Status.OK ? result[0] ?? null : null);
    });
  });
}

// 좌표 → { address, legalDongCode, dongName }. 서버가 쓰는 법정동(B) 기준이고, 도로명 주소가 없으면 지번 주소를 쓴다
export async function findAddressByCoords(lat, lng) {
  await loadKakao();
  const [regions, detail] = await Promise.all([coord2RegionCode(lat, lng), coord2Address(lat, lng)]);
  const legal = regions.find((region) => region.region_type === 'B');
  if (!legal || !/^\d{10}$/.test(legal.code ?? '')) {
    throw new Error('이 위치의 주소를 찾지 못했어요. 주소 직접 검색을 이용해 주세요.');
  }
  return {
    address: detail?.road_address?.address_name || detail?.address?.address_name || legal.address_name,
    legalDongCode: legal.code,
    dongName: legal.region_3depth_name,
  };
}
