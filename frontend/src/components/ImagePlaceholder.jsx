import Pictogram from './Pictogram';

// 사진이 없는 상품·레시피 자리. 글자 대신 접시 그림을 두고, 스크린리더에는 이유를 읽어 준다
export default function ImagePlaceholder() {
  return (
    <div className="tile__image tile__image--empty" role="img" aria-label="이미지 준비 중">
      <Pictogram name="plate" size={72} />
    </div>
  );
}
