// 레시피 상세 응답에는 썸네일 주소가 없어서, 유튜브 주소에서 영상 번호를 뽑아 서버와 같은 썸네일 주소를 만든다
export function youtubeThumbnail(url) {
  const id = url?.match(/[?&]v=([\w-]{6,})/)?.[1];
  return id ? `https://img.youtube.com/vi/${id}/hqdefault.jpg` : null;
}
