import KoreaMap from '../components/KoreaMap';
import MartList from '../components/MartList';

export default function Marts() {
  return (
    <div className="container page">
      <h1 className="page__title">가맹 마트</h1>
      <p className="page__sub">배송지가 속한 지역에 가맹 마트가 있으면 그 마트의 상품을 이용할 수 있어요. 우리 동네에 있는지 직접 확인해 보세요.</p>
      <KoreaMap />
      <MartList />
    </div>
  );
}
