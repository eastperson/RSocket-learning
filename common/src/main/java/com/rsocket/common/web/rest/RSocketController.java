package com.rsocket.common.web.rest;


import com.rsocket.common.model.Item;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.net.URI;

import static io.rsocket.metadata.WellKnownMimeType.MESSAGE_RSOCKET_ROUTING;
import static org.springframework.http.MediaType.APPLICATION_JSON;
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

}
