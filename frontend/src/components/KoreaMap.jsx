import { useEffect, useRef, useState } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

// 서버는 소수를 "37.5860000" 같은 문자열로 내려주므로 숫자로 바꾸고, 위치를 알 수 없는 마트는 그리지 않는다
function toMapMarts(list) {
  return list
    .map((mart) => ({
      ...mart,
      latitude: Number(mart.latitude),
      longitude: Number(mart.longitude),
      serviceRadiusM: mart.serviceRadiusM == null ? null : Number(mart.serviceRadiusM),
    }))
    .filter(
      (mart) =>
        Number.isFinite(mart.latitude) &&
        Number.isFinite(mart.longitude) &&
        (mart.serviceRadiusM === null || Number.isFinite(mart.serviceRadiusM)),
    );
}

// 운영 중인 마트만 내려온다(GET /api/config/marts, 로그인 불필요)
async function fetchMarts() {
  const res = await fetch('/api/config/marts');
  if (!res.ok) throw new Error(String(res.status));
  const json = await res.json();
  return toMapMarts(json.data);
}

export default function KoreaMap() {
  const elementRef = useRef(null);
  const [marts, setMarts] = useState([]);
  // 실패했을 때 가짜 마트를 보여주면 실제와 다른 정보가 되므로, 빈 지도와 안내 문구만 보여준다
  const [status, setStatus] = useState('loading');

  useEffect(() => {
    let ignore = false;
    fetchMarts()
      .then((list) => {
        if (ignore) return;
        setMarts(list);
        setStatus('done');
      })
      .catch(() => {
        if (!ignore) setStatus('error');
      });
    return () => {
      ignore = true;
    };
  }, []);

  useEffect(() => {
    const map = L.map(elementRef.current, {
      center: [36.3, 127.8],
      zoom: 7,
      minZoom: 6,
      maxBounds: [
        [32.5, 123.5],
        [39.5, 132.5],
      ],
      scrollWheelZoom: false, // 페이지 스크롤이 지도에 걸리지 않게 한다
    });
    L.tileLayer('https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png', {
      attribution: '&copy; OpenStreetMap contributors &copy; CARTO',
      maxZoom: 18,
    }).addTo(map);

    marts.forEach((mart) => {
      const open = mart.status === 'OPEN';
      const color = open ? '#e31b23' : '#8e8e8e';
      const position = [mart.latitude, mart.longitude];
      // 서비스 반경은 배송 가능 지역을 보여준다
      if (mart.serviceRadiusM !== null) {
        L.circle(position, {
          radius: mart.serviceRadiusM,
          color,
          weight: 1,
          fillColor: color,
          fillOpacity: 0.12,
        }).addTo(map);
      }
      // 이름을 HTML 문자열로 넣지 않고 텍스트 노드로 넣어 마트 이름에 태그가 섞여도 실행되지 않게 한다
      const popup = document.createElement('strong');
      popup.textContent = mart.name;
      L.circleMarker(position, { radius: 7, color: '#ffffff', weight: 2, fillColor: color, fillOpacity: 1 })
        .bindPopup(popup)
        .addTo(map);
    });

    return () => map.remove();
  }, [marts]);

  return (
    <>
      <div ref={elementRef} className="korea-map" role="img" aria-label="가맹 마트 위치 지도" />
      {status === 'error' && (
        <p className="field__hint">마트 위치를 불러오지 못했습니다. 잠시 뒤 다시 시도해 주세요.</p>
      )}
      {status === 'done' && marts.length === 0 && <p className="field__hint">표시할 마트가 아직 없습니다.</p>}
    </>
  );
}
