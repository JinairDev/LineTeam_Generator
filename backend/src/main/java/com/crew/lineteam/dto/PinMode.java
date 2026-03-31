package com.crew.lineteam.dto;

/**
 * 재편성 시 고정 범위
 */
public enum PinMode {
    /** 직전 팀에 있던 TP만 유지, TS·기타는 재배치 */
    TP_FIXED,
    /** 직전 팀에 있던 TS만 유지, TP는 풀에서 팀에 배정 후 TS·기타 재배치 */
    TS_FIXED,
    /** 직전 팀에 있던 TP·TS 모두 유지, 그 외 멤버만 재배치 */
    BOTH_FIXED
}
