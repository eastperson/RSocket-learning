package com.chapter1_2.ep.service;


import com.rsocket.ep.model.Item;
import com.rsocket.ep.repository.ItemRepository;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Service;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Service
public class RSocketService {

    private final ItemRepository itemRepository;

    private final Sinks.Many<Item> itemSink;

    public RSocketService(ItemRepository itemRepository){
        this.itemRepository = itemRepository;
        this.itemSink = Sinks.many().multicast().onBackpressureBuffer();
    }

    // 스프링 메시징의 @MessageMapping
    // 도착지가 newItems.request-response로 지정된 R소켓 메시지를 이 메소드로 라우팅
    @MessageMapping("newItems.request-response")
    // 스프링 메시지는 메시지를 리액티브하게 기다리고 있다가. 메시지가 들어오면 메시지 본문을 인자로해서 save 메소드를 호출
    // 반환타입은 Item을 포함하는 리액터 타입 요청하는 측에서 예상하는 응답 메시지 시그니처와 일치해야한다.
    public Mono<Item> processNewItemsViaRSocketRequestResponse(Item item){
        // Item 객체에 대한 정보를 담은 메시지를 받으면 비즈니스 로직 수행
        return this.itemRepository.save(item)
                // doOnNext()를 호출해서 새로 저장된 Item 객체를 가져와서 싱크를 통해 FluxProcessor로 내보낸다.
                .doOnNext(savedItem -> this.itemSink.tryEmitNext(savedItem));
    }

    // 도착지가 newItems.request-stream로 지정된 R소켓 메시지를 이 메소드로 라우팅
    @MessageMapping("newItems.request-stream")
    // 메시지가 들어오면 Item목록 조회후 Flux에 담아 반환
    public Flux<Item> findItemsViaRScoketRequestStream(){
        // 몽고디비에 저장된 Item 조회
        return this.itemRepository.findAll()
                // 조회한 Item객체를 싱크를 통해 FluxProcessor로 보낸다.
                .doOnNext(this.itemSink::tryEmitNext);
    }

    @MessageMapping("newItems.fire-and-forget")
    // 실행 후 망각이기 때문에 데이터를 반환할 필요가 없다.
    public Mono<Void> processNewItemsViaRSocketFireAndForget(Item item) {
        return this.itemRepository.save(item)
                .doOnNext(savedItem -> this.itemSink.tryEmitNext(savedItem))
                //  then을 사용하면 Mono에 감싸져있는 데이터를 사용하지 않고 버릴 수 있다.
                .then();
    }

    @MessageMapping("newItems.monitor")
    public Flux<Item> monitorNewItems(){
        // 이 메소드를 구독하는 여러 주체들은 sink에 담겨있는 Item 객체들의 복사본을 받게 된다.
        return this.itemSink.asFlux();
    }

}
