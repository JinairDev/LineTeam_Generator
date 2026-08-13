package com.crew.lineteam.dto;

/**
 * 재편성 시 고정 범위
 */
public enum PinMode {
    /** 직전 팀에 있던 TP만 유지, TS·기타는 재배치 */
    TP_FIXED,
    /** 직전 팀에 있던 TS만 유지, TP는 풀에서 팀에 배정 후 TS·기타 재배치 */
    TS_FIXED,
    /** 직전 팀에 있던 TP·TS 유지. 비어 있는 TP 자리·미배정 TS는 자동 배정 후 나머지 팀원 배치 */
    BOTH_FIXED,
    /**
     * 소속팀/GRP·사전 배정·직접 옮긴 인원({@code pinnedEmployeeIds})만 현재 팀에 유지하고
     * 나머지는 다시 자동 편성. 고정 인원은 균등 보정에서도 이동하지 않음.
     */
    LOCKED_FIXED
}
