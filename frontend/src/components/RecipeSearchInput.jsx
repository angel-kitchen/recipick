import useRecipeSuggestions from '../hooks/useRecipeSuggestions';

// 레시피 목록 화면의 이름 검색창. 입력값과 조합 중 처리는 부모(검색 목록)가 들고, 자동완성은 공용 훅이 맡는다
export default function RecipeSearchInput({ value, onChange, onCompositionStart, onCompositionEnd }) {
  const { inputProps, list } = useRecipeSuggestions(value, 'recipe-suggestions');

  return (
    <div className="suggest">
      <input
        className="field__input"
        placeholder="레시피 이름 검색"
        value={value}
        maxLength={50}
        aria-label="레시피 이름 검색"
        {...inputProps}
        onChange={onChange}
        onCompositionStart={onCompositionStart}
        onCompositionEnd={onCompositionEnd}
      />
      {list}
    </div>
  );
}
