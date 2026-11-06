import useSuggestions from './useSuggestions';

// 레시피 검색 자동완성. 헤더 검색창과 레시피 목록 검색창이 함께 쓴다
export default function useRecipeSuggestions(value, listId) {
  return useSuggestions(value, listId, {
    endpoint: '/api/recipes/suggestions',
    idKey: 'recipeId',
    imageKey: 'thumbnailUrl',
    pathOf: (suggestion) => `/recipes/${suggestion.recipeId}`,
  });
}
