import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { clearMemory, isValidResult, readMemory, writeMemory } from '../chatMemory';
import { CUISINE, DISH_TYPE, formatWon, spicyText } from '../labels';
import ChefAvatar from './ChefAvatar';
import ErrorNotice from './ErrorNotice';
import ImagePlaceholder from './ImagePlaceholder';
import MembershipInfoModal from './MembershipInfoModal';
import QuickAdd from './QuickAdd';
import RepiSearching from './RepiSearching';

// 칩 → 꼬리질문 1번 → 공감 한마디 → 추천 레시피 순서로 끝나는 짧은 대화다.
// 질문·선택지·공감 문구는 정해 둔 문장이고, 고른 답을 한 문장(text)으로 합쳐 기존 AI 조건 추천에 보낸다.
// 카드에 보이는 추천 이유(reason)만 AI가 쓴 글이다
const TOPICS = [
  {
    chip: '매운 음식 땡기세요?',
    ask: '어느 정도 매운맛이 좋아요?',
    options: [
      { label: '안 매운 걸로', text: '안 매운 음식', reply: ['순한 맛이 편하죠!', '맵지 않은 걸로 골라 봤어요.'] },
      { label: '적당히 매콤하게', text: '적당히 매콤한 음식', reply: ['딱 알싸한 정도를 원하시는군요!', '적당히 매콤한 걸로 골라 봤어요.'] },
      { label: '아주 매운 걸로', text: '아주 매운 음식', reply: ['스트레스 풀리는 매운맛이 당기는 날이네요!', '확실히 매운 걸로 골라 봤어요.'] },
    ],
  },
  {
    chip: '아이와 함께 먹을 수 있는 음식?',
    ask: '몇 살 아이랑 먹어요?',
    options: [
      {
        label: '영유아',
        text: '영유아도 먹을 수 있는 안 맵고 부드러운 음식',
        reply: ['아기가 먹을 거라 더 신경 쓰이죠!', '안 맵고 부드러운 걸로 골라 봤어요.'],
      },
      {
        label: '5~7세',
        text: '5~7세 아이와 함께 먹을 수 있는 안 매운 음식',
        reply: ['한창 잘 먹고 뛰어노는 나이네요!', '맵지 않고 손이 덜 가는 걸로 골라 봤어요.'],
      },
      {
        label: '초등학생',
        text: '초등학생 아이와 함께 먹을 수 있는 안 매운 음식',
        reply: ['잘 먹는 만큼 든든한 한 끼가 필요하겠네요!', '맵지 않고 아이들이 좋아할 만한 걸로 골라 봤어요.'],
      },
    ],
  },
  {
    chip: '예산에 맞춰 골라 줘요',
    ask: '재료비는 얼마까지 생각해요?',
    // 레시피 재료를 기본 인분만큼 사면 가장 싼 레시피도 1만 원을 넘어, 1만 원 선택지는 늘 빈 결과가 된다
    options: [
      { label: '2만 원 이하', text: '재료비 2만 원 이하 음식', reply: ['알뜰하게 차려 볼까요?', '2만 원 안에서 골라 봤어요.'] },
      { label: '3만 원 이하', text: '재료비 3만 원 이하 음식', reply: ['넉넉하진 않아도 충분히 맛있게 할 수 있어요!', '3만 원 안에서 골라 봤어요.'] },
      { label: '5만 원 이하', text: '재료비 5만 원 이하 음식', reply: ['오늘은 조금 든든하게 차려 볼까요?', '5만 원 안에서 골라 봤어요.'] },
    ],
  },
];

// 서버는 최대 5개를 주지만 대화가 길어 보이지 않도록 4개까지만 보여 준다
const MAX_CARDS = 4;

function Sparkle() {
  return (
    <svg className="chat-sparkle" viewBox="0 0 16 16" aria-hidden="true">
      <path d="M8 0 L9.6 6.4 L16 8 L9.6 9.6 L8 16 L6.4 9.6 L0 8 L6.4 6.4Z" />
    </svg>
  );
}

function Chip({ onClick, selected, disabled, children }) {
  return (
    <button type="button" className={`chat-chip${selected ? ' is-on' : ''}`} aria-pressed={selected} onClick={onClick} disabled={disabled}>
      <Sparkle />
      {children}
    </button>
  );
}

// live는 새로 나타나는 답을 스크린리더가 읽게 할 말풍선에만 붙인다
function Ai({ lines, live }) {
  return (
    <div className="chat-row">
      <ChefAvatar />
      <div className="chat-col">
        <b className="chat-name">레피</b>
        <div className="chat-bubble" role={live ? 'status' : undefined}>
          {lines.map((line) => (
            <p key={line}>{line}</p>
          ))}
        </div>
      </div>
    </div>
  );
}

function Me({ children }) {
  return (
    <div className="chat-row chat-row--me">
      <div className="chat-bubble">{children}</div>
    </div>
  );
}

function RecipeCard({ recipe, showPrice }) {
  const meta = [
    CUISINE[recipe.cuisineType],
    DISH_TYPE[recipe.dishType],
    recipe.cookingTimeMinutes ? `${recipe.cookingTimeMinutes}분` : null,
    recipe.spicyLevel != null ? spicyText(recipe.spicyLevel) : null,
  ]
    .filter(Boolean)
    .join(' · ');
  return (
    <div className="chat-recipe">
      {recipe.thumbnailUrl ? (
        <img className="chat-recipe__thumb" src={recipe.thumbnailUrl} alt="" loading="lazy" />
      ) : (
        <ImagePlaceholder />
      )}
      <div className="chat-recipe__body">
        <span className="chat-recipe__title">{recipe.name}</span>
        <span className="chat-recipe__meta">{meta}</span>
        {recipe.reason && <span className="chat-recipe__reason">{recipe.reason}</span>}
        <span className="chat-recipe__tags">
          {recipe.autoCartAvailable && <span className="badge">자동 담기 가능</span>}
          {showPrice && recipe.estimatedPrice != null && <span className="badge">재료 약 {formatWon(recipe.estimatedPrice)}</span>}
        </span>
      </div>
      <div className="chat-recipe__actions">
        <Link to={`/recipes/${recipe.recipeId}`} className="chat-btn">
          상세보기
        </Link>
        <QuickAdd recipe={recipe} className="chat-btn chat-btn--solid" />
      </div>
    </div>
  );
}

// 레시피 목록 위쪽 AI 도우미. 멤버십 회원만 쓸 수 있고, 비멤버가 칩이나 입력창을 누르면 멤버십 안내 창을 띄운다.
// 기존 세 가지 검색(메뉴·조건 / 가진 재료 / 식단)은 "자세히 찾기" 버튼으로 연다
// children은 "자세히 찾기"를 눌렀을 때 대화 아래에 펼쳐지는 기존 검색 화면이다
export default function RecipeChat({ detailOpen, onToggleDetail, children }) {
  const { member } = useAuth();
  const isMembership = Boolean(member?.membership?.active);
  // 상세 화면에 갔다가 뒤로 오면 같은 대화를 보여 줘서, AI를 다시 부르지 않게 한다(chatMemory.js)
  const [saved] = useState(() =>
    readMemory('chat', member?.memberId, (data) => (data.topic === -1 || TOPICS[data.topic] !== undefined) && isValidResult(data.result)),
  );
  const [topic, setTopic] = useState(() => TOPICS[saved?.topic] ?? null);
  const [option, setOption] = useState(() => TOPICS[saved?.topic]?.options[saved?.option] ?? null);
  const [asked, setAsked] = useState(saved?.asked ?? null);
  const [text, setText] = useState('');
  const [result, setResult] = useState(saved?.result ?? null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);
  const [membershipInfo, setMembershipInfo] = useState(false);

  // 늦게 도착한 이전 요청의 응답이 지금 화면을 덮어쓰지 않도록 요청마다 번호를 매긴다
  const latest = useRef(0);
  const started = topic || asked;

  // 답을 기다리는 중이거나 오류일 때는 기억을 바꾸지 않는다. 대화를 처음으로 되돌리면 기억도 지운다.
  // 문장만 보내고 결과가 없는 상태는 메뉴 이름이라 미리보기 화면으로 넘어간 경우라서, 기억하면 돌아왔을 때 질문만 남는다
  useEffect(() => {
    if (loading || error || !member) return;
    if (!started || (asked && !result)) {
      clearMemory('chat');
      return;
    }
    writeMemory('chat', member.memberId, {
      topic: TOPICS.indexOf(topic),
      option: topic ? topic.options.indexOf(option) : -1,
      asked,
      result,
    });
  }, [loading, error, member, started, topic, option, asked, result]);

  const reset = () => {
    latest.current += 1;
    setTopic(null);
    setOption(null);
    setAsked(null);
    setResult(null);
    setError(null);
    setLoading(false);
  };

  // action이 돌려준 값을 결과로 보여 준다(돌려주지 않으면 화면을 바꾸지 않는다). 성공하면 true
  const run = async (action) => {
    latest.current += 1;
    const id = latest.current;
    setError(null);
    setResult(null);
    setLoading(true);
    try {
      const data = await action();
      if (id !== latest.current) return false;
      if (data) setResult(data);
      return true;
    } catch (e) {
      if (id === latest.current) setError(e);
      return false;
    } finally {
      if (id === latest.current) setLoading(false);
    }
  };

  const pickTopic = (next) => {
    if (!isMembership) {
      setMembershipInfo(true);
      return;
    }
    reset();
    setTopic(next);
  };

  const pickOption = (next) => {
    setOption(next);
    run(() => api.post('/api/recipes/ai-recommendations', { text: next.text }));
  };

  // 무엇을 입력해도 같은 조건 추천으로 보내 항상 레시피 카드로 보여 준다.
  // 메뉴 이름 → 자동 장바구니 미리보기는 카드의 "재료 담기"나 "자세히 찾기"에서 쓴다
  const submit = (event) => {
    event.preventDefault();
    const sentence = text.trim();
    if (!sentence) return;
    reset();
    setAsked(sentence);
    // 요청이 실패하면 다시 보낼 수 있게 입력을 남겨 둔다
    run(() => api.post('/api/recipes/ai-recommendations', { text: sentence })).then((ok) => ok && setText(''));
  };

  const guardMembership = () => {
    if (!isMembership) setMembershipInfo(true);
  };

  const recipes = result?.recipes?.slice(0, MAX_CARDS) ?? [];

  return (
    <section className="chat" aria-label="AI 레시피 도우미">
      <div className="chat-deco" aria-hidden="true">
        <i />
        <Sparkle />
        <i />
      </div>
      <div className="chat-log">
        {!started && (
          <>
            <Ai lines={['오늘 뭐 먹을지 같이 골라 볼까요?', '아래에서 하나 눌러도 되고, 직접 말해 줘도 돼요.']} />
            <div className="chat-chips">
              {TOPICS.map((item) => (
                <Chip key={item.chip} onClick={() => pickTopic(item)}>
                  {item.chip}
                </Chip>
              ))}
            </div>
          </>
        )}
        {topic && (
          <>
            <Me>{topic.chip}</Me>
            <Ai lines={[topic.ask]} />
            <div className="chat-chips">
              {topic.options.map((item) => (
                <Chip key={item.label} selected={option === item} disabled={loading} onClick={() => pickOption(item)}>
                  {item.label}
                </Chip>
              ))}
            </div>
            {option && <Me>{option.label}</Me>}
          </>
        )}
        {asked && <Me>{asked}</Me>}
        {loading && <RepiSearching />}
        {error && <ErrorNotice error={error} />}
        {result && (
          <>
            {recipes.length === 0 && result.status === 'FALLBACK' && (
              <Ai live lines={['지금은 추천하기 어려워요.', '아래 필터로 직접 찾아볼까요?']} />
            )}
            {recipes.length === 0 && result.status !== 'FALLBACK' && (
              <Ai live lines={['조건에 맞는 레시피를 찾지 못했어요.', '조건을 줄여서 다시 골라 볼까요?']} />
            )}
            {recipes.length > 0 && (
              <>
                <Ai
                  live
                  lines={
                    result.status === 'FALLBACK'
                      ? ['지금은 AI가 고르지 못했어요.', '조건에 맞는 레시피를 순서대로 보여 드려요.']
                      : (option?.reply ?? ['말씀하신 조건으로 골라 봤어요.'])
                  }
                />
                <div className="chat-recipes">
                  {recipes.map((recipe) => (
                    <RecipeCard key={recipe.recipeId} recipe={recipe} showPrice={result.budgetApplied} />
                  ))}
                </div>
              </>
            )}
          </>
        )}
        {started && !loading && (
          <button type="button" className="chat-restart" onClick={reset}>
            처음부터 다시 고르기
          </button>
        )}
      </div>
      <div className="chat-detail" hidden={!detailOpen}>
        {children}
      </div>
      <div className="chat-composer">
        <button type="button" className="chat-more" aria-expanded={detailOpen} onClick={onToggleDetail}>
          {detailOpen ? '자세히 찾기 닫기' : '자세히 찾기 · 메뉴 / 가진 재료 / 식단'}
        </button>
        {/* 자세히 찾기 안에도 입력칸이 있어서, 펼친 동안은 이 입력칸을 숨겨 입력칸이 두 개 보이지 않게 한다 */}
        {!detailOpen && (
          <form className="chat-line" onSubmit={submit}>
            <input
              className="chat-field"
              placeholder="먹고 싶은 걸 직접 말해 보세요"
              aria-label="먹고 싶은 걸 직접 말하기"
              value={text}
              maxLength={200}
              readOnly={!isMembership}
              required={isMembership}
              onClick={guardMembership}
              onChange={(e) => setText(e.target.value)}
            />
            <button type="submit" className="chat-send" disabled={loading} onClick={guardMembership}>
              ↑ 보내기
            </button>
          </form>
        )}
      </div>
      {membershipInfo && <MembershipInfoModal onClose={() => setMembershipInfo(false)} />}
    </section>
  );
}
