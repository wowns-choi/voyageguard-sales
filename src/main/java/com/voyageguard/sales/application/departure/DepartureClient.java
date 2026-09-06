package com.voyageguard.sales.application.departure;

/** Planning의 Departure 조회 포트(port) */
public interface DepartureClient {

    DepartureView get(Long departureId);
}
