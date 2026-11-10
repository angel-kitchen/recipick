import useSuggestions from '../hooks/useSuggestions';

// 상품 목록 화면의 이름 검색창. 입력값은 부모(상품 목록)가 들고, 자동완성은 공용 훅이 맡는다
export default function ProductSearchInput({ value, onChange }) {
  const { inputProps, list } = useSuggestions(value, 'product-suggestions', {
    endpoint: '/api/products/suggestions',
    idKey: 'productId',
    imageKey: 'imageUrl',
    pathOf: (suggestion) => `/products/${suggestion.productId}`,
  });

  return (
    <div className="suggest">
      <input
        className="field__input"
        placeholder="상품 이름 검색"
        value={value}
        maxLength={50}
        aria-label="상품 이름 검색"
        {...inputProps}
        onChange={onChange}
      />
      {list}
    </div>
  );
}
