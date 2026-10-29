import { useEffect, useRef, useState } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { mockMarts } from '../mocks/marts';

// 마트 공개 API(GET /api/config/marts)가 아직 없거나 실패하면 임시 데이터로 보여준다
async function fetchMarts() {
  try {
    const res = await fetch('/api/config/marts');
    if (!res.ok) throw new Error(String(res.status));
    const json = await res.json();
    return json.data;
  } catch {
    return mockMarts;
  }
}

export default function KoreaMap() {
  const elementRef = useRef(null);
  const [marts, setMarts] = useState([]);

  useEffect(() => {
    fetchMarts().then(setMarts);
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
      L.circle(position, {
        radius: mart.serviceRadiusM,
        color,
        weight: 1,
        fillColor: color,
        fillOpacity: 0.12,
      }).addTo(map);
      // 이름을 HTML 문자열로 넣지 않고 텍스트 노드로 넣어 마트 이름에 태그가 섞여도 실행되지 않게 한다
      const popup = document.createElement('strong');
      popup.textContent = mart.name;
      L.circleMarker(position, { radius: 7, color: '#ffffff', weight: 2, fillColor: color, fillOpacity: 1 })
        .bindPopup(popup)
        .addTo(map);
    });

    return () => map.remove();
  }, [marts]);

  return <div ref={elementRef} className="korea-map" role="img" aria-label="가맹 마트 위치 지도" />;
}
