package com.crew.lineteam.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 라인팀 표시 ID (SEL: A101~… / PUS: B101~…). {@link TeamAssignmentService} 의 BASE 정규화 값(SEL, PUS) 기준.
 */
public final class LineTeamIdFormatter {

    private static final List<String> SEL_IDS = buildSelIds();
    private static final List<String> PUS_IDS = buildBusanIds();

    private LineTeamIdFormatter() {}

    /** SEL 고정 라인팀 수 (A101~… 전 구간) */
    public static int selTeamCount() {
        return SEL_IDS.size();
    }

    /** PUS 고정 라인팀 수 (B101~… 전 구간) */
    public static int pusTeamCount() {
        return PUS_IDS.size();
    }

    private static List<String> buildSelIds() {
        List<String> ids = new ArrayList<>(61);
        for (int n = 101; n <= 113; n++) ids.add("A" + n);
        for (int n = 201; n <= 212; n++) ids.add("A" + n);
        for (int n = 301; n <= 312; n++) ids.add("A" + n);
        for (int n = 401; n <= 412; n++) ids.add("A" + n);
        for (int n = 501; n <= 512; n++) ids.add("A" + n);
        return List.copyOf(ids);
    }

    private static List<String> buildBusanIds() {
        List<String> ids = new ArrayList<>(8);
        for (int n = 101; n <= 108; n++) ids.add("B" + n);
        return List.copyOf(ids);
    }

    /**
     * @param normalizedBase {@link TeamAssignmentService} 의 normalizeBase 결과 (예: SEL, PUS)
     * @param indexInBase    해당 베이스에서 1부터 시작하는 팀 순번
     */
    public static String teamIdFor(String normalizedBase, int indexInBase) {
        if (indexInBase < 1) {
            throw new IllegalArgumentException("indexInBase는 1 이상이어야 합니다: " + indexInBase);
        }
        String b = normalizedBase == null ? "" : normalizedBase.trim().toUpperCase(Locale.ROOT);
        if ("SEL".equals(b)) {
            if (indexInBase <= SEL_IDS.size()) {
                return SEL_IDS.get(indexInBase - 1);
            }
            return "SEL-" + String.format("%02d", indexInBase);
        }
        if ("PUS".equals(b)) {
            if (indexInBase <= PUS_IDS.size()) {
                return PUS_IDS.get(indexInBase - 1);
            }
            return "PUS-" + String.format("%02d", indexInBase);
        }
        return b + "-" + String.format("%02d", indexInBase);
    }
}
