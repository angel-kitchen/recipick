package com.recipick.global.common;

// 레시피 검색 자동완성의 초성 검색용. 완성형 한글(가~힣)은 초성으로 바꾸고 나머지 글자는 그대로 둔다.
// 키보드로 친 초성은 호환용 자모(ㄱ U+3131 등)라, 같은 문자로 바꿔야 비교된다
public final class Choseong {

    private static final char[] INITIALS = {
        'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'};
    private static final char FIRST_SYLLABLE = '가';
    private static final char LAST_SYLLABLE = '힣';
    // 완성형 한글은 초성 하나마다 중성 21개 × 종성 28개 = 588자씩 이어진다
    private static final int SYLLABLES_PER_INITIAL = 21 * 28;

    private Choseong() {
    }

    public static String of(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder result = new StringBuilder(text.length());
        for (char letter : text.toCharArray()) {
            result.append(letter >= FIRST_SYLLABLE && letter <= LAST_SYLLABLE
                ? INITIALS[(letter - FIRST_SYLLABLE) / SYLLABLES_PER_INITIAL]
                : letter);
        }
        return result.toString();
    }

    public static boolean isChoseongOnly(String text) {
        return !text.isEmpty() && text.chars().allMatch(Choseong::isInitial);
    }

    private static boolean isInitial(int letter) {
        for (char initial : INITIALS) {
            if (initial == letter) {
                return true;
            }
        }
        return false;
    }
}
