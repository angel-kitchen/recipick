import { useEffect, useRef, useState } from 'react';
import { findAddressByCoords, loadKakao } from '../address/kakao';
import ErrorNotice from './ErrorNotice';

const FALLBACK_CENTER = { lat: 37.5665, lng: 126.978 }; // 서울시청. 현재 위치를 못 얻었을 때 지도가 시작할 곳
const ASK_NOTICE = '현재 위치를 찾고 있어요. 브라우저가 위치 사용을 물으면 "허용"을 눌러 주세요.';
// 이보다 오차가 크면 핀이 실제 위치와 다를 수 있다고 알린다(GPS가 없는 PC는 Wi-Fi·IP로 추정해서 수 km씩 어긋나기도 한다)
const COARSE_METERS = 300;

function currentPosition() {
  return new Promise((resolve) => {
    if (!navigator.geolocation) {
      resolve({ error: '이 브라우저에서는 현재 위치를 쓸 수 없어요. 지도를 눌러 위치를 정해 주세요.' });
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (position) =>
        resolve({
          lat: position.coords.latitude,
          lng: position.coords.longitude,
          accuracy: position.coords.accuracy,
        }),
      (err) =>
        resolve({
          error:
            err.code === 1
              ? '위치 권한이 꺼져 있어요. 주소창의 위치 아이콘에서 허용하거나, 지도를 눌러 위치를 정해 주세요.'
              : '현재 위치를 찾지 못했어요. 지도를 눌러 위치를 정해 주세요.',
        }),
      // 매번 새로 측정하고 가능하면 GPS·Wi-Fi 같은 정밀한 방법을 쓴다
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 },
    );
  });
}

function foundNotice(accuracy) {
  if (accuracy > COARSE_METERS) {
    return `현재 위치의 오차가 커요(약 ${Math.round(accuracy).toLocaleString()}m). 지도의 원 안 어딘가라는 뜻이니, 핀을 정확한 위치로 옮겨 주세요.`;
  }
  return '현재 위치예요. 핀을 끌거나 지도를 눌러 정확한 위치를 정해 주세요.';
}

// 현재 위치에서 지도를 열고, 핀을 끌거나 지도를 눌러 위치를 정한 뒤 "이 위치로 확인"을 누르면 주소와 법정동 코드를 돌려준다.
// 위치 좌표는 주소를 찾으려고 카카오 SDK에 전달하고, 우리 서버에는 보내거나 저장하지 않는다. 배송지로 저장하는 건 호출한 화면에서 사용자가 상세 주소를 입력하고 확정할 때다
export default function LocationPickerModal({ onConfirm, onClose }) {
  const containerRef = useRef(null);
  // 모달이 열린 뒤 효과가 다시 실행되거나(개발 모드) 닫히면 값이 바뀐다. 오래된 비동기 작업이 이어서 지도를 만들지 않게 구분하는 데 쓴다
  const runRef = useRef(0);
  const mapRef = useRef(null);
  const markerRef = useRef(null);
  const circleRef = useRef(null);
  const pinRef = useRef(null);
  const [notice, setNotice] = useState(ASK_NOTICE);
  const [error, setError] = useState(null);
  const [pinned, setPinned] = useState(false);
  const [finding, setFinding] = useState(false);
  const [confirming, setConfirming] = useState(false);

  useEffect(() => {
    const onKey = (event) => event.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  // 사용자가 직접 정한 핀. 직접 정했으니 위치 오차 원은 지운다
  const placeByUser = (latLng) => {
    // 진행 중인 위치 조회·주소 확인 결과가 직접 놓은 핀을 덮어쓰지 못하게 무효화한다
    runRef.current += 1;
    setFinding(false);
    setConfirming(false);
    markerRef.current.setPosition(latLng);
    markerRef.current.setVisible(true);
    circleRef.current?.setMap(null);
    pinRef.current = { lat: latLng.getLat(), lng: latLng.getLng() };
    setPinned(true);
    setError(null);
    setNotice('핀을 놓았어요. 맞으면 "이 위치로 확인"을 눌러 주세요. 다르면 핀을 끌거나 지도를 다시 눌러 주세요.');
  };

  // 브라우저에 현재 위치를 물어 핀을 그곳으로 옮긴다. 처음 열 때와 "내 위치로 다시 찾기"가 같이 쓴다
  const findMe = async () => {
    setFinding(true);
    setError(null);
    setNotice(ASK_NOTICE);
    const run = runRef.current;
    const position = await currentPosition();
    if (run !== runRef.current) return;
    setFinding(false);
    if (position.error) {
      setNotice(position.error);
      return;
    }

    const { maps } = window.kakao;
    const latLng = new maps.LatLng(position.lat, position.lng);
    const map = mapRef.current;
    map.setLevel(position.accuracy > 1000 ? 5 : position.accuracy > COARSE_METERS ? 4 : 3);
    map.setCenter(latLng);
    markerRef.current.setPosition(latLng);
    markerRef.current.setVisible(true);
    if (circleRef.current) {
      circleRef.current.setPosition(latLng);
      circleRef.current.setRadius(position.accuracy);
    } else {
      circleRef.current = new maps.Circle({
        map,
        center: latLng,
        radius: position.accuracy,
        strokeWeight: 1,
        strokeColor: '#00754a',
        strokeOpacity: 0.7,
        fillColor: '#00754a',
        fillOpacity: 0.12,
      });
    }
    circleRef.current.setMap(map);
    pinRef.current = { lat: position.lat, lng: position.lng };
    setPinned(true);
    setNotice(foundNotice(position.accuracy));
  };

  useEffect(() => {
    const run = runRef.current;
    const container = containerRef.current;

    (async () => {
      try {
        await loadKakao();
      } catch (e) {
        if (run === runRef.current) {
          setError(e);
          setNotice('');
        }
        return;
      }
      if (run !== runRef.current) return;

      // 위치를 찾는 동안 지도부터 보여 준다. 위치를 알기 전에는 핀을 숨겨 엉뚱한 곳이 선택된 것처럼 보이지 않게 한다
      const { maps } = window.kakao;
      const center = new maps.LatLng(FALLBACK_CENTER.lat, FALLBACK_CENTER.lng);
      const map = new maps.Map(container, { center, level: 3 });
      const marker = new maps.Marker({ position: center, draggable: true, map });
      marker.setVisible(false);
      mapRef.current = map;
      markerRef.current = marker;
      circleRef.current = null;
      pinRef.current = null;
      maps.event.addListener(marker, 'dragend', () => placeByUser(marker.getPosition()));
      maps.event.addListener(map, 'click', (event) => placeByUser(event.latLng));

      findMe();
    })();

    return () => {
      runRef.current += 1;
      if (container) container.innerHTML = '';
    };
    // 지도는 모달이 열릴 때 한 번만 만든다
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const confirm = async () => {
    if (!pinRef.current) return;
    setError(null);
    setConfirming(true);
    const run = runRef.current;
    try {
      const result = await findAddressByCoords(pinRef.current.lat, pinRef.current.lng);
      // 조회 중에 모달을 닫았거나 핀을 옮겼으면 이 결과는 버린다
      if (run !== runRef.current) return;
      onConfirm(result);
    } catch (e) {
      if (run !== runRef.current) return;
      setError(e);
      setConfirming(false);
    }
  };

  return (
    <div className="modal" role="dialog" aria-modal="true" aria-label="현재 위치로 찾기">
      <div className="modal__box modal__box--wide card">
        <button type="button" className="modal__close" aria-label="닫기" onClick={onClose}>
          ×
        </button>
        <h2 className="tile__title">현재 위치로 찾기</h2>
        {notice && <p className="field__hint">{notice}</p>}
        <div ref={containerRef} className="location-map" />
        <p className="field__hint">위치는 주소를 찾기 위해 카카오 지도로 전달되며, Recipick에는 저장하지 않아요.</p>
        <ErrorNotice error={error} />
        <div className="row-actions">
          <button type="button" className="btn btn--large" disabled={!pinned || confirming} onClick={confirm}>
            {confirming ? '확인하는 중...' : '이 위치로 확인'}
          </button>
          <button type="button" className="btn btn--large btn--outline" disabled={finding || confirming} onClick={findMe}>
            {finding ? '위치 찾는 중...' : '내 위치로 다시 찾기'}
          </button>
        </div>
      </div>
    </div>
  );
}
