package com.rsocket.client;

import com.rsocket.client.model.Item;
import com.rsocket.client.repository.ItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureWebTestClient
public class RSocketTest {

    @Autowired WebTestClient webTestClient;
    @Autowired ItemRepository itemRepository;

    @Test
    void verifyRemoteOperationThroughRSocketRequestResponse() throws InterruptedException{
        // 데이터 초기화
        this.itemRepository.deleteAll()
                .as(StepVerifier::create)
                .verifyComplete();

        // 새 Item 생성
        this.webTestClient.post().uri("/items/request-response")
                .bodyValue(new Item("Alf alarm clock","nothing important",19.99))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Item.class)
                .value(item -> {
                    assertThat(item.getId()).isNotNull();
                    assertThat(item.getName()).isEqualTo("Alf alarm clock");
                    assertThat(item.getDescription()).isEqualTo("nothing important");
                    assertThat(item.getPrice()).isEqualTo(19.99);
                });

        // Item이 몽고디비에 저장됐는지 확인
        this.itemRepository.findAll()
                .as(StepVerifier::create)
                .expectNextMatches(item -> {
                    assertThat(item.getId()).isNotNull();
                    assertThat(item.getName()).isNotNull();
                    assertThat(item.getDescription()).isEqualTo("nothing important");
                    assertThat(item.getPrice()).isEqualTo(19.99);
                    return true;
                })
                 .verifyComplete();
    }

    @Test
    void verifyRemoteOperationsThroughRSocketStream() throws InterruptedException{
        // 데이터 초기화
        this.itemRepository.deleteAll().block();

        // 3개의 Item 생성
        List<Item> items = IntStream.rangeClosed(1,3)
                .mapToObj(i -> new Item("name - " + i, "description - " + i,i))
                .collect(Collectors.toList());
        // 테스트 대상이 아니므로 StepVerifier 대신에 blockLast() 사용
        this.itemRepository.saveAll(items).blockLast();

        // 스트림 테스트
        this.webTestClient.get().uri("/items/request-stream")
                .accept(MediaType.APPLICATION_NDJSON)
                .exchange()
                .expectStatus().isOk()
                .returnResult(Item.class)
                .getResponseBody()
                // StepVerifier를 통해 검증할 수 있도록 체이닝 플로우에서 빠져나온다.
                .as(StepVerifier::create)
                // 3번의 값 조회
                .expectNextMatches(itemPredicate("1"))
                .expectNextMatches(itemPredicate("2"))
                .expectNextMatches(itemPredicate("3"))
                .verifyComplete();
    }

    private Predicate<Item> itemPredicate(String num) {
        return item -> {
            assertThat(item.getName()).startsWith("name");
            assertThat(item.getName()).endsWith(num);
            assertThat(item.getDescription()).startsWith("description");
            assertThat(item.getDescription()).endsWith(num);
            assertThat(item.getPrice()).isPositive();
            return true;
        };
    }

    @Test
    void verifyRemoteOperationsThroughRSocketFireAndForget() throws InterruptedException {

        // 데이터 초기화
        this.itemRepository.deleteAll()
                .as(StepVerifier::create)
                .verifyComplete();

        // 새 Item 생성
        this.webTestClient.post().uri("/items/fire-and-forget")
                .bodyValue(new Item("Alf alarm clock","nothing important",19.99))
                .exchange()
                .expectStatus().isCreated()
                .expectBody().isEmpty();

        Thread.sleep(500);

        // Item이 몽고디비에 저장됐는지 확인
        this.itemRepository.findAll()
                .as(StepVerifier::create)
                .expectNextMatches(item -> {
                    assertThat(item.getId()).isNotNull();
                    assertThat(item.getName()).isEqualTo("Alf alarm clock");
                    assertThat(item.getDescription()).isEqualTo("nothing important");
                    assertThat(item.getPrice()).isEqualTo(19.99);
                    return true;
                }).verifyComplete();
    }



}
