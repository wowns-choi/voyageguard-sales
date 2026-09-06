package com.voyageguard.sales.infrastructure.planning;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.voyageguard.sales.application.departure.DepartureClient;
import com.voyageguard.sales.application.departure.DepartureView;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * DepartureClient의 REST 어댑터 - Planning이 이미 프론트엔드용으로 노출해둔
 * GET /api/v1/departures/{id}를 그대로 재사용한다(엔드포인트를 서비스 간 통신 전용으로 새로
 * 만들 필요 없음). 지금은 Planning이 같은 프로세스 안에 있어서 사실상 자기 자신을 호출하는
 * 모양이지만, 이렇게 호출 주소를 설정값으로 빼두면 나중에 Planning이 실제로 분리돼도 이
 * 클래스만 주소를 바꾸면 되고 ReservationService/WaitlistService는 안 건드려도 된다.
 */
@Component
public class PlanningDepartureClient implements DepartureClient {

    private final RestClient restClient;

    public PlanningDepartureClient(@Value("${planning.service.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        // 무한 대기 방지 - Planning이 응답 안 하면 Sales도 같이 멈추는 걸 막기 위한 최소한의 안전장치
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(3000);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public DepartureView get(Long departureId) {
        try {
            DepartureResponse response = restClient.get()
                    .uri("/api/v1/departures/{id}", departureId)
                    .retrieve()
                    .body(DepartureResponse.class);
            return new DepartureView(
                    DepartureView.Status.valueOf(response.status()),
                    response.capacity(),
                    response.saleEndDate(),
                    response.salePrice() // 회차 판매가격
            );
        } catch (HttpClientErrorException.NotFound e) {
            throw new IllegalArgumentException("존재하지 않는 회차입니다. id=" + departureId);
        }
    }

    // Planning의 DepartureResponse 중 Sales가 필요한 필드만 매핑 - 나머지(productTitle 등)는 무시
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DepartureResponse(String status, Integer capacity, LocalDate saleEndDate, Integer salePrice) {
    }
}
