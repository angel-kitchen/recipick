import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';

const SUGGEST_DELAY_MS = 200;

// 레시피 이름 검색창과 자동완성 목록. 입력값과 조합 중 처리는 부모(검색 목록)가 들고, 이 컴포넌트는 제안만 맡는다.
// 목록에서 고르면 그 레시피 상세로 가고, 고르지 않으면 부모가 하던 대로 입력값으로 검색 목록이 바뀐다.
// 한글은 조합 중인 글자도 입력값에 들어오므로 조합이 끝나기를 기다리지 않고 제안한다("ㄱㅊ"의 마지막 ㅊ도 조합 중이다)
export default function RecipeSearchInput({ value, onChange, onCompositionStart, onCompositionEnd }) {
  const navigate = useNavigate();
  const [suggestions, setSuggestions] = useState([]);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const latestRequest = useRef(0);

  useEffect(() => {
    const keyword = value.trim();
    if (!keyword) {
      latestRequest.current += 1;
      setSuggestions([]);
      return undefined;
    }
    const timer = setTimeout(() => {
      // 빠르게 칠 때 먼저 보낸 요청의 응답이 늦게 와도 마지막 입력의 결과만 남긴다
      const requestId = ++latestRequest.current;
      api
        .get('/api/recipes/suggestions', { keyword })
        .then((data) => requestId === latestRequest.current && setSuggestions(data))
        // 제안은 보조 기능이라 실패하면 목록만 비우고 검색은 그대로 쓰게 한다
        .catch(() => requestId === latestRequest.current && setSuggestions([]));
    }, SUGGEST_DELAY_MS);
    return () => clearTimeout(timer);
  }, [value]);

  useEffect(() => setActive(-1), [suggestions]);

  const show = open && suggestions.length > 0;

  const choose = (suggestion) => {
    setOpen(false);
    navigate(`/recipes/${suggestion.recipeId}`);
  };

  const onKeyDown = (event) => {
    // 한글 조합 중 Enter·방향키는 글자를 확정하는 키라 목록 이동으로 쓰지 않는다
    if (event.nativeEvent.isComposing) return;
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      if (!suggestions.length) return;
      event.preventDefault();
      setOpen(true);
      // -1(입력창)과 목록 0~n-1을 한 바퀴로 돌아, 맨 아래에서 내리면 입력창으로 돌아온다
      const step = event.key === 'ArrowDown' ? 1 : -1;
      const size = suggestions.length + 1;
      setActive((prev) => ((prev + 1 + step + size) % size) - 1);
      return;
    }
    if (event.key === 'Enter') {
      if (show && active >= 0) {
        event.preventDefault();
        choose(suggestions[active]);
      } else {
        setOpen(false);
      }
      return;
    }
    if (event.key === 'Escape') setOpen(false);
  };

  return (
    <div className="suggest">
      <input
        className="field__input"
        placeholder="레시피 이름 검색 (초성도 돼요: ㄱㅊ)"
        value={value}
        maxLength={50}
        role="combobox"
        aria-label="레시피 이름 검색"
        aria-autocomplete="list"
        aria-expanded={show}
        aria-controls="recipe-suggestions"
        aria-activedescendant={show && active >= 0 ? `recipe-suggestion-${active}` : undefined}
        onChange={(e) => {
          onChange(e);
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
        onBlur={() => setOpen(false)}
        onKeyDown={onKeyDown}
        onCompositionStart={onCompositionStart}
        onCompositionEnd={onCompositionEnd}
      />
      {show && (
        <ul id="recipe-suggestions" className="suggest__list" role="listbox">
          {suggestions.map((suggestion, index) => (
            <li
              key={suggestion.recipeId}
              id={`recipe-suggestion-${index}`}
              role="option"
              aria-selected={index === active}
              className={`suggest__item ${index === active ? 'is-active' : ''}`}
              // 클릭보다 입력창의 blur가 먼저 일어나 목록이 닫히지 않도록 누르는 순간 포커스 이동을 막는다
              onMouseDown={(e) => e.preventDefault()}
              onMouseEnter={() => setActive(index)}
              onClick={() => choose(suggestion)}
            >
              {suggestion.thumbnailUrl ? (
                <img className="suggest__thumb" src={suggestion.thumbnailUrl} alt="" loading="lazy" />
              ) : (
                <span className="suggest__thumb" aria-hidden="true" />
              )}
              <Highlighted name={suggestion.name} keyword={value} />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

// 이름 안에서 검색어(띄어쓰기 무시)와 같은 부분을 굵게 보여 준다. 초성 검색은 글자가 달라 강조하지 않는다
function Highlighted({ name, keyword }) {
  const query = keyword.replace(/\s+/g, '').toLowerCase();
  const start = query ? name.toLowerCase().indexOf(query) : -1;
  if (start < 0) return <span>{name}</span>;
  return (
    <span>
      {name.slice(0, start)}
      <strong>{name.slice(start, start + query.length)}</strong>
      {name.slice(start + query.length)}
    </span>
  );
}
