"""docs/requirement.md를 도메인별 CodeRabbit 참고 문서로 나눈다.

원본을 고친 뒤 저장소 루트에서 실행한다: python3 .coderabbit/split_requirements.py
원본에 기능을 추가하면 DOMAIN_FEATURES에도 추가해야 한다(없으면 오류로 알려준다).
"""
import re
import sys
from collections import OrderedDict
from pathlib import Path

SOURCE = Path("docs/requirement.md")
OUT_DIR = Path(".coderabbit/requirements")

DOMAIN_NAMES = {
    "member": "회원·멤버십",
    "product": "상품·마트·재고",
    "recipe": "레시피·자동 장바구니·AI",
    "order": "장바구니·주문",
    "payment": "결제·환불·포인트",
}

# 담당자와 코드 도메인이 어긋나는 기능은 기능이 시작되는 도메인에 둔다.
DOMAIN_FEATURES = {
    "member": [
        "회원가입·로그인", "이용 가능 지역 판정", "배송지 관리", "마이페이지", "멤버십 기능 제한", "멤버십 활성 검증",
        "관리자 로그인", "멤버십 관리", "유료 구독", "무료체험", "해지 예약", "즉시 해지", "결제 결과에 따른 전환",
        "회원별 멤버십 이력 조회",
    ],
    "product": ["상품 검색·조회", "마트 관리", "상품 관리", "재료·상품 연결", "재고 관리"],
    "recipe": [
        "레시피 검색·조회", "레시피 찜", "메뉴·인분 선택", "수량 계산", "필수·선택 재료", "자동 구성 미리보기",
        "구매 불가 사유", "자동 구성 확정", "자동 구성 기록", "레시피 관리", "레시피 재료 관리", "자동 장보기 지정",
        "AI 자연어 장보기", "AI 조건 추천", "AI 처리 기준", "AI 이력 조회",
    ],
    "order": [
        "직접 장바구니", "장바구니 담기 이력", "주문 화면·배송지 확인", "주문 상품·금액 검증", "주문 생성 멱등성", "주문 생성",
        "결제 대기 주문 제한", "결제 결과에 따른 주문 처리", "주문 조회·상태 이력", "주문 조회·수락", "마트 거절·환불 연동",
        "주문 진행 상태 확인", "배송비", "포인트 사용·적립 연동", "배송비 혜택", "마트 주문 거절·자동 환불", "배송 상태 변경",
    ],
    "payment": [
        "상품 결제", "결제 실패·만료", "결제 전 취소", "결제 후 취소", "환불 처리", "결제 내역 조회", "결제 조회",
        "포인트 사용", "포인트 적립", "취소 시 포인트 복원", "포인트 조회", "자동 결제 동의", "카드 등록", "멤버십 결제",
        "첫 유료 결제 실패", "갱신 결제 실패", "갱신 시 카드 없음", "이용료 환불", "적립률 차등", "멤버십 결제 조회",
    ],
}

FEATURE_PATTERN = re.compile(r"- \*\*(.+?)\*\*")


def split(lines):
    """요구사항 줄을 도메인별로 나누고 차수·회원 구분별 안내문과 기능 블록을 반환한다.

    DOMAIN_FEATURES에 없는 기능이 나오면 SystemExit로 종료한다.
    """
    feature_domain = {f: d for d, features in DOMAIN_FEATURES.items() for f in features}
    # 도메인 -> (차수, 회원 구분) -> {"note": 섹션 안내문, "items": 기능 블록들}
    result = {d: OrderedDict() for d in DOMAIN_FEATURES}
    phase = audience = current = None
    note = []
    for line in lines:
        if line.startswith("## "):
            phase, audience, current = line, None, None
            continue
        if line.startswith("### "):
            audience, current, note = line, None, []
            continue
        if line.startswith(">"):
            note.append(line)
            continue
        match = FEATURE_PATTERN.match(line)
        if match:
            title = match.group(1)
            if title not in feature_domain:
                sys.exit(f"도메인이 지정되지 않은 기능입니다: {title} (DOMAIN_FEATURES에 추가하세요)")
            section = result[feature_domain[title]].setdefault((phase, audience), {"note": list(note), "items": []})
            current = []
            section["items"].append(current)
        if current is not None and line and line != "---":
            current.append(line)
    return result


def render(domain, sections):
    """도메인의 섹션을 차수·회원 구분 순서대로 배치한 마크다운 문자열로 반환한다."""
    out = [
        f"# 요구사항 - {domain} ({DOMAIN_NAMES[domain]})",
        "",
        f"> 자동 생성 파일입니다. 직접 고치지 말고 {SOURCE}를 고친 뒤 split_requirements.py를 실행하세요.",
        "",
    ]
    last_phase = None
    for (phase, audience), section in sections.items():
        if phase != last_phase:
            if last_phase:
                out += ["---", ""]
            out += [phase, ""]
            last_phase = phase
        out += [audience, ""]
        if section["note"]:
            out += section["note"] + [""]
        for item in section["items"]:
            out += item
        out.append("")
    return "\n".join(out).rstrip() + "\n"


def main():
    """SOURCE를 읽고 OUT_DIR에 도메인별 UTF-8 요구사항 문서를 생성하거나 덮어쓴다."""
    lines = SOURCE.read_text(encoding="utf-8").split("\n")
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for domain, sections in split(lines).items():
        (OUT_DIR / f"{domain}.md").write_text(render(domain, sections), encoding="utf-8")


if __name__ == "__main__":
    main()
