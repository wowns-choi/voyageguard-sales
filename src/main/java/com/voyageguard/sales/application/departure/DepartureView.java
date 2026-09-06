package com.voyageguard.sales.application.departure;

import java.time.LocalDate;

/** Sales가 Planning의 Departure에 대해 알아야 하는 정보만 담은 뷰 */
public record DepartureView(Status status, Integer capacity, LocalDate saleEndDate, Integer salePrice) {

    public enum Status { OPEN, CLOSED, CANCELLED }
}
