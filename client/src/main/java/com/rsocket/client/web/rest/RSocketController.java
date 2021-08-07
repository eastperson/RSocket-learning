package com.rsocket.client.web.rest;


import com.rsocket.client.model.Item;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;

import static io.rsocket.metadata.WellKnownMimeType.MESSAGE_RSOCKET_ROUTING;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE;
import static org.springframework.http.MediaType.parseMediaType;

@RestController
public class RSocketController {

    // R소켓에 연결된 코드는 새 클라이언트가 구독할 때 마다 호출된다.
    private final Mono<RSocketRequester> requester;

    // RSocketRequesterAutoConfiguration 정책안에서 자동설정으로 RSocketRequester.Builder 빈을 생성
    public RSocketController(RSocketRequester.Builder builder) {
        this.requester = builder
                // 데이터의 미디어 타입을 지정
                .dataMimeType(APPLICATION_JSON)
                // message/x.rsocket.routing.v0로 지정
                .metadataMimeType(parseMediaType(MESSAGE_RSOCKET_ROUTING.toString()))
                // 호스트 이름과 포트번호를 지정하고 connectTcp()를 호출해서 7000번 포트를 사용하는 R소켓 서버에 연결한다.
                .connectTcp("localhost",7000)
                // 견고성을 높이기 위해 메시지 처리 실패시 Mono가 5번까지 재시도할 수있도록 지정
                .retry(5)
                // 요청 Mono를 핫 소스로 전환한다. 핫 소스에서 가장 최근의 신호는 캐시되어 있을 수 있으며 구독자는 사본을 가지고 있을 수 있다.
                // 이 방식은 다수의 클라이언트가 동일한 하나의 데이터를 요구할 때 효율성을 높일 수 있다.
                .cache();
    }

    @PostMapping("/items/request-response")
    Mono<ResponseEntity<?>> addNewItemUsingRSocketRequestResponse(@RequestBody Item item) {
        System.out.println("=========================================================================");
        return this.requester
                .flatMap(rSocketRequester -> rSocketRequester
                    // 이 요청을 newItems.request-response로 라우팅 한다.
                    .route("newItems.request-response")
                    // Item 객체 정보를 data() 메서드에 전달한다.
                    .data(item)
                    // Mono<Item> 응답을 원한다는 신호를 보낸다.
                    .retrieveMono(Item.class))
                // 한 개의 Item이 반환되면 map()과 ResponseEntity 헬퍼 메소드를 사용해서 HTTP 201 Created 응답을 반환한다.
                .map(savedItem -> ResponseEntity.created(
                        URI.create("/items/request-response")).body(savedItem));
    }

    // HTTP GET 요청을 처리하고 Flux를 통해 JSON 스트림 데이터를 반환
    // 스트림방 식으로 반환하기 위해 미디어타입을 APPLICATION_NDJSON_VALUE로 지정 실제값은 "application/x-ndjson"
    @GetMapping(value = "/items/request-stream", produces = MediaType.APPLICATION_NDJSON_VALUE)
    // 스트림을 반환해야 하므로 Flux에 담는다.
    Flux<Item> findItemsUsingRSocketRequestStream(){
        return this.requester
                // 여러 건의 조회 결과를 Flux에 담아 반환하기 위해 flatMapMany() 적용
                .flatMapMany(rSocketRequester -> rSocketRequester
                    // R소켓 서버로 라우팅
                    .route("newItems.request-stream")
                        .retrieveFlux(Item.class)
                        // 여러 건의 Item을 1초에 1건씩 반환하도록 요청
                        // 여러건의 데이터가 세 번에 응답되는 게 아니라 스트림을 통해 응답되는 것을 눈으로 쉽게 확인하기 위해 넣은 코드.
                        .delayElements(Duration.ofSeconds(1)));
    }

    @PostMapping("/items/fire-and-forget")
    Mono<ResponseEntity<?>> addNewItemUsingRSocketFireAndForget(@RequestBody Item item) {
        return this.requester
                .flatMap(rSocketRequester -> rSocketRequester
                     // 경로로 전달
                    .route("newItems.fire-and-forget")
                     // 새 Item 정보가 포함돼 있는 Mono를 받아온다.
                    .data(item)
                    .send())
                .then(
                        // Item 정보가 포함된 Mono를 map()을 사용해서 Item 정보를 포함하는 ResponseEntity를 Mono를 변환해서 반환
                        // 실행 후 망각 예제에서는 Mono<Void<를 반환
                        // 새로 생성된 Item에 대한 Location 헤더값을 포함하는 HTTP 201 created를 반환하려면 map()이 아니라 then()과 Mono.just()를 사용해서
                        // Mono를 새로 만들어서 반환
                        Mono.just(ResponseEntity.created(URI.create("/items/fire-and-forget")).build()));
    }

    @GetMapping(value = "/items", produces = TEXT_EVENT_STREAM_VALUE)
    Flux<Item> liveUpdates() {
        return this.requester
                .flatMapMany(rSocketRequester -> rSocketRequester
                    .route("newItems.monitor")
                    .retrieveFlux(Item.class));
    }

}
