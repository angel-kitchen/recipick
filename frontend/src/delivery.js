// 배송지 상태에 따라 재료 담기·주문을 쓸 수 없는 이유. 쓸 수 있으면 null
// - text: 버튼에 마우스를 올리면 보이는 짧은 말
// - notice: 화면 위 안내 띠에 쓰는 말
export function deliveryBlock(member) {
  if (!member.currentAddress) {
    return {
      text: '배송지를 등록하면 이용할 수 있어요',
      notice: '배송지를 등록하면 배송 가능한 지역인지 바로 알려 드려요. 등록하기 전에는 상품 조회, 재료 담기, 주문을 할 수 없어요.',
      label: '배송지 등록',
      tone: 'info',
    };
  }
  if (!member.mart) {
    return {
      text: '선택한 배송지는 아직 서비스 지역이 아니에요',
      notice: `선택한 배송지(${member.currentAddress.address})는 아직 서비스 지역이 아니에요. 레시피는 둘러볼 수 있지만 상품 조회, 담기, 주문은 할 수 없어요. 서비스 지역의 배송지를 선택해 주세요.`,
      label: '배송지 변경',
      tone: 'warn',
    };
  }
  return null;
}
