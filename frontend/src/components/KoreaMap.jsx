import { useEffect, useRef, useState } from 'react';
import maplibregl from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';

// 서버는 소수를 "37.5860000" 같은 문자열로 내려주므로 숫자로 바꾸고, 위치를 알 수 없는 마트는 그리지 않는다
function toMapMarts(list) {
  return list
    .map((mart) => ({
      ...mart,
      latitude: Number(mart.latitude),
      longitude: Number(mart.longitude),
    }))
    .filter((mart) => Number.isFinite(mart.latitude) && Number.isFinite(mart.longitude));
}

// 운영 중인 마트만 내려온다(GET /api/config/marts, 로그인 불필요)
async function fetchMarts() {
  const res = await fetch('/api/config/marts');
  if (!res.ok) throw new Error(String(res.status));
  const json = await res.json();
  return toMapMarts(json.data);
}

// 키 없이 쓸 수 있는 벡터 지도(OpenFreeMap). 모양은 아래 tidyBasemap에서 우리 서비스에 맞게 고친다
const MAP_STYLE = 'https://tiles.openfreemap.org/styles/bright';

const HIDDEN_LAYER = /^(poi|airport|aeroway|highway-shield|road_shield|road_oneway|boundary)/;
// 큰길만 주황·노랑으로 두고 골목과 작은 길은 흰색으로 눌러서, 길이 전부 같은 색으로 뒤엉켜 보이지 않게 한다
const MAJOR_ROADS = { motorway: '#f6ad55', trunk: '#fad9a6', primary: '#fff0d4' };

function roadColor(layerId) {
  const match = layerId.match(/^(?:highway|bridge|tunnel)-(.+?)(-casing)?$/);
  if (!match) return null;
  const major = Object.keys(MAJOR_ROADS).find((kind) => match[1].startsWith(kind));
  if (major) return match[2] ? '#e7b27a' : MAJOR_ROADS[major];
  return match[2] ? '#e4e4e4' : '#ffffff';
}

function tidyBasemap(map) {
  map.getStyle().layers.forEach((layer) => {
    if (HIDDEN_LAYER.test(layer.id)) {
      map.setLayoutProperty(layer.id, 'visibility', 'none');
      return;
    }
    if (layer.type === 'symbol' && layer.layout?.['text-field']) {
      map.setLayoutProperty(layer.id, 'text-field', ['coalesce', ['get', 'name:ko'], ['get', 'name']]);
    }
    const color = layer.type === 'line' && !/path|railway/.test(layer.id) ? roadColor(layer.id) : null;
    if (color) map.setPaintProperty(layer.id, 'line-color', color);
    if (layer.id === 'background') map.setPaintProperty(layer.id, 'background-color', '#f6f6f4');
    if (layer.id.startsWith('landuse-')) map.setPaintProperty(layer.id, 'fill-color', '#f1f1ef');
    if (layer.id.startsWith('landcover-wood') || layer.id.startsWith('landcover-grass') || layer.id === 'park') {
      map.setPaintProperty(layer.id, 'fill-color', '#e6efe0');
    }
  });
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
    const map = new maplibregl.Map({
      container: elementRef.current,
      style: MAP_STYLE,
      center: [127.8, 36.3],
      zoom: 6,
      minZoom: 5,
      maxZoom: 17,
      maxBounds: [
        [123.5, 32.5],
        [132.5, 39.5],
      ],
      attributionControl: { compact: true },
    });
    map.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-left');

    // 바탕 지도에 나오는 가게·산·공항 표시를 지우고 지명을 한글로 보여 줘서 마트 점이 먼저 눈에 띄게 한다
    map.once('style.load', () => tidyBasemap(map));

    marts.forEach((mart) => {
      const open = mart.status === 'OPEN';
      const dot = document.createElement('div');
      dot.className = `map-dot${open ? '' : ' map-dot--closed'}`;
      // 이름을 HTML 문자열로 넣지 않고 텍스트 노드로 넣어 마트 이름에 태그가 섞여도 실행되지 않게 한다
      const popup = document.createElement('strong');
      popup.textContent = mart.name;
      new maplibregl.Marker({ element: dot })
        .setLngLat([mart.longitude, mart.latitude])
        .setPopup(new maplibregl.Popup({ offset: 12 }).setDOMContent(popup))
        .addTo(map);
    });

    // 처음부터 마트가 보이도록 마트 위치에 맞춘다. 마트가 가까우면 동네 단위(줌 14)까지만 당기고,
    // 멀리 흩어져 있으면 모두 보이는 만큼만 당긴다. 마트가 없으면 전국 화면 그대로 둔다
    if (marts.length > 0) {
      const bounds = new maplibregl.LngLatBounds();
      marts.forEach((mart) => bounds.extend([mart.longitude, mart.latitude]));
      map.fitBounds(bounds, { padding: 40, maxZoom: 14, duration: 0 });
    }

    return () => map.remove();
  }, [marts]);

  return (
    <>
      <div ref={elementRef} className="korea-map" role="img" aria-label="가맹 마트 위치 지도" />
      {status === 'error' && (
        <p className="field__hint">마트 위치를 불러오지 못했어요. 잠시 뒤에 다시 시도해 주세요.</p>
      )}
      {status === 'done' && marts.length === 0 && <p className="field__hint">표시할 마트가 아직 없어요.</p>}
    </>
  );
}
