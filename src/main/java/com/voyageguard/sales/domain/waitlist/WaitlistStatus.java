package com.voyageguard.sales.domain.waitlist;

public enum WaitlistStatus {
    WAITING, // 대기중
    PROMOTED, // 승격됨(재고 확보되어 예약 가능해짐)
    EXPIRED // 대기 만료
}
