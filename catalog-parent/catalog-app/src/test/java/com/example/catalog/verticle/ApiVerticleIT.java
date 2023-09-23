/* Licensed under Apache-2.0 2023. */
package com.example.catalog.verticle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import com.example.catalog.TestcontainerLogConsumer;
import com.example.catalog.entity.Item;
import com.example.catalog.web.route.dto.CreateItemRequestDto;
import com.example.catalog.web.route.dto.CreateItemResponseDto;
import com.example.catalog.web.route.dto.FindAllResponseDto;
import com.example.catalog.web.route.dto.FindOneResponseDto;
import com.example.catalog.web.route.dto.PaginatedResponseDto;
import com.example.catalog.web.route.dto.SuggestResponseDto;
import com.example.catalog.web.route.dto.UpdateItemRequestDto;
import com.example.commons.config.Config;
import com.example.commons.config.ParseConfig;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.Response;
import io.vertx.redis.client.ResponseType;
import java.io.IOException;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import lombok.extern.java.Log;
import org.junit.Rule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Log
@Testcontainers
@ExtendWith(VertxExtension.class)
class ApiVerticleIT {

  @Rule public Network network = Network.newNetwork();

  static Config config;

  static {
    try {
      ClassLoader classLoader = ApiVerticleIT.class.getClassLoader();
      String path = Objects.requireNonNull(classLoader.getResource("it-config.json")).getPath();
      config = ParseConfig.parseArgs(new String[] {path});
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Container
  public GenericContainer<?> redis =
      new GenericContainer<>(DockerImageName.parse("redis/redis-stack-server:latest"))
          .withExposedPorts(6379)
          .withNetwork(network)
          .withNetworkAliases("redis")
          .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1))
          .withLogConsumer(new TestcontainerLogConsumer());

  @Container
  public GenericContainer<?> app =
      new GenericContainer<>(DockerImageName.parse("catalog:jvm-latest"))
          .withExposedPorts(config.httpConfig().port())
          .withNetwork(network)
          .withNetworkAliases("app")
          .dependsOn(redis)
          .waitingFor(
              Wait.forLogMessage(".*deployment id.*", 1).withStartupTimeout(Duration.ofSeconds(5L)))
          .withClasspathResourceMapping("it-config.json", "/config.json", BindMode.READ_ONLY)
          .withCommand("/config.json")
          .withLogConsumer(new TestcontainerLogConsumer());

  @BeforeEach
  public void before() {
    RestAssured.baseURI = "http://" + app.getHost();
    RestAssured.port = app.getMappedPort(config.httpConfig().port());
    log.info("RestAssured.port: " + RestAssured.port);
  }

  @AfterEach
  public void after() {
    RestAssured.reset();
  }

  @Test
  void suggestionTest() {
    String createItemJsonResponse =
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(new CreateItemRequestDto("new_item", 123L).toJson().encode())
            .post("/api/create")
            .then()
            .assertThat()
            .statusCode(HttpResponseStatus.CREATED.code())
            .extract()
            .asString();

    CreateItemResponseDto createItemResponseDto =
        new CreateItemResponseDto(new JsonObject(createItemJsonResponse));

    String suggestJsonResponse =
        RestAssured.given()
            .get("/api/suggest?s=new")
            .then()
            .assertThat()
            .statusCode(HttpResponseStatus.OK.code())
            .extract()
            .asString();

    assertThat(new SuggestResponseDto(new JsonObject(suggestJsonResponse)))
        .isNotNull()
        .extracting(SuggestResponseDto::suggestions)
        .satisfies(dtos -> assertThat(dtos).isNotEmpty());

    RestAssured.given()
        .delete("/api/" + createItemResponseDto.id())
        .then()
        .assertThat()
        .statusCode(HttpResponseStatus.NO_CONTENT.code());

    suggestJsonResponse =
        RestAssured.given()
            .get("/api/suggest?s=new")
            .then()
            .assertThat()
            .statusCode(HttpResponseStatus.OK.code())
            .extract()
            .asString();

    assertThat(new SuggestResponseDto(new JsonObject(suggestJsonResponse)))
        .isNotNull()
        .extracting(SuggestResponseDto::suggestions)
        .satisfies(dtos -> assertThat(dtos).isEmpty());
  }

  //  @Test
  //  void pagination(Vertx ignore, VertxTestContext testContext) {
  //    // create items
  //    for (int i = 0; i < 8; i++) {
  //      RestAssured.given()
  //          .contentType(ContentType.JSON)
  //          .body(new CreateItemRequestDto("new_item_" + i, 100L + i).toJson().encode())
  //          .post("/api/create")
  //          .then()
  //          .assertThat()
  //          .statusCode(HttpResponseStatus.CREATED.code());
  //    }
  //
  //    // search forwards
  //    String searchJsonResponse =
  //        RestAssured.given()
  //            .get("/api/items?lastId=0&size=5")
  //            .then()
  //            .assertThat()
  //            .statusCode(HttpResponseStatus.OK.code())
  //            .extract()
  //            .asString();
  //
  //    PaginatedResponseDto page1 = new PaginatedResponseDto(new JsonObject(searchJsonResponse));
  //    testContext.verify(
  //        () -> {
  //          assertThat(page1.total()).isEqualTo(8L);
  //          assertThat(page1.items()).hasSize(5);
  //          assertThat(page1.more()).isTrue();
  //        });
  //
  //    long lastSequence = page1.items().get(page1.items().size() - 1).sequence();
  //
  //    searchJsonResponse =
  //        RestAssured.given()
  //            .get("/api/items?lastId=" + lastSequence + "&size=5")
  //            .then()
  //            .assertThat()
  //            .statusCode(HttpResponseStatus.OK.code())
  //            .extract()
  //            .asString();
  //
  //    PaginatedResponseDto page2 = new PaginatedResponseDto(new JsonObject(searchJsonResponse));
  //    testContext.verify(
  //        () -> {
  //          assertThat(page2.total()).isEqualTo(8L);
  //          assertThat(page2.items()).hasSize(4);
  //          assertThat(page2.more()).isFalse();
  //        });
  //
  //    assertThat(lastSequence).isEqualTo(page2.items().get(0).sequence());
  //    lastSequence = page2.items().get(page2.items().size() - 1).sequence();
  //
  //    searchJsonResponse =
  //        RestAssured.given()
  //            .get("/api/items?lastId=" + lastSequence + "&size=5")
  //            .then()
  //            .assertThat()
  //            .statusCode(HttpResponseStatus.OK.code())
  //            .extract()
  //            .asString();
  //
  //    // page 3 is just the last item
  //    PaginatedResponseDto page3 = new PaginatedResponseDto(new JsonObject(searchJsonResponse));
  //    testContext.verify(
  //        () -> {
  //          assertThat(page3.total()).isEqualTo(8L);
  //          assertThat(page3.items()).hasSize(1);
  //          assertThat(page3.more()).isFalse();
  //        });
  //
  //    assertThat(lastSequence).isEqualTo(page3.items().get(0).sequence());
  //    lastSequence = page3.items().get(page3.items().size() - 1).sequence();
  //
  //    // search backwards
  //    searchJsonResponse =
  //        RestAssured.given()
  //            .get(
  //                "/api/items?lastId="
  //                    + lastSequence
  //                    + "&size=5&direction="
  //                    + ItemService.Direction.BACKWARD.name())
  //            .then()
  //            .assertThat()
  //            .statusCode(HttpResponseStatus.OK.code())
  //            .extract()
  //            .asString();
  //
  //    PaginatedResponseDto page4 = new PaginatedResponseDto(new JsonObject(searchJsonResponse));
  //    testContext.verify(
  //        () -> {
  //          assertThat(page4.total()).isEqualTo(8L);
  //          assertThat(page4.items()).hasSize(5);
  //          assertThat(page4.more()).isTrue();
  //        });
  //
  //    // lastSequence is now on the last item
  //    assertThat(lastSequence).isEqualTo(page4.items().get(page4.items().size() - 1).sequence());
  //    // we are paginating backwards, we want lastItem to be first in this list
  //    lastSequence = page4.items().get(0).sequence();
  //
  //    searchJsonResponse =
  //        RestAssured.given()
  //            .get(
  //                "/api/items?lastId="
  //                    + lastSequence
  //                    + "&size=5&direction="
  //                    + ItemService.Direction.BACKWARD.name())
  //            .then()
  //            .assertThat()
  //            .statusCode(HttpResponseStatus.OK.code())
  //            .extract()
  //            .asString();
  //
  //    PaginatedResponseDto page5 = new PaginatedResponseDto(new JsonObject(searchJsonResponse));
  //    testContext.verify(
  //        () -> {
  //          assertThat(page5.total()).isEqualTo(8L);
  //          assertThat(page5.items()).hasSize(4);
  //          assertThat(page5.more()).isFalse();
  //        });
  //
  //    assertThat(lastSequence).isEqualTo(page5.items().get(page5.items().size() - 1).sequence());
  //    lastSequence = page5.items().get(0).sequence();
  //
  //    searchJsonResponse =
  //        RestAssured.given()
  //            .get(
  //                "/api/items?lastId="
  //                    + lastSequence
  //                    + "&size=5&direction="
  //                    + ItemService.Direction.BACKWARD.name())
  //            .then()
  //            .assertThat()
  //            .statusCode(HttpResponseStatus.OK.code())
  //            .extract()
  //            .asString();
  //
  //    PaginatedResponseDto page6 = new PaginatedResponseDto(new JsonObject(searchJsonResponse));
  //
  //    // last page, we should get the first item again as only element
  //    testContext.verify(
  //        () -> {
  //          assertThat(page6.total()).isEqualTo(8L);
  //          assertThat(page6.items()).hasSize(1);
  //          assertThat(page6.more()).isFalse();
  //        });
  //
  //    assertThat(lastSequence).isEqualTo(page6.items().get(0).sequence());
  //    testContext.completeNow();
  //  }

  @Test
  void fullHappyPath(Vertx vertx, VertxTestContext testContext) {
    // find all empty
    String getItemsJsonResponse =
        RestAssured.given()
            .get("/api/items")
            .then()
            .assertThat()
            .statusCode(HttpResponseStatus.OK.code())
            .extract()
            .asString();

    assertThat(new PaginatedResponseDto(new JsonObject(getItemsJsonResponse)))
        .isNotNull()
        .extracting(PaginatedResponseDto::items)
        .satisfies(dtos -> assertThat(dtos).isEmpty());

    // create
    String createItemJsonResponse =
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(new CreateItemRequestDto("new_item", 123L).toJson().encode())
            .post("/api/create")
            .then()
            .assertThat()
            .statusCode(HttpResponseStatus.CREATED.code())
            .extract()
            .asString();

    CreateItemResponseDto createItemResponseDto =
        new CreateItemResponseDto(new JsonObject(createItemJsonResponse));
    assertThat(createItemResponseDto)
        .isNotNull()
        .satisfies(
            dto ->
                assertSoftly(
                    softly ->
                        testContext.verify(
                            () -> {
                              softly.assertThat(dto.id()).isNotNull();
                              softly.assertThat(dto.name()).isEqualTo("new_item");
                              softly.assertThat(dto.priceInCents()).isEqualTo(123L);
                            })));

    getItemsJsonResponse =
        RestAssured.given()
            .get("/api/items")
            .then()
            .assertThat()
            .statusCode(HttpResponseStatus.OK.code())
            .extract()
            .asString();

    assertThat(new PaginatedResponseDto(new JsonObject(getItemsJsonResponse)))
        .isNotNull()
        .extracting(PaginatedResponseDto::items)
        .satisfies(
            items ->
                assertThat(items)
                    .singleElement()
                    .satisfies(
                        dto ->
                            assertSoftly(
                                softly ->
                                    testContext.verify(
                                        () -> {
                                          softly.assertThat(dto.id()).isNotNull();
                                          softly.assertThat(dto.name()).isEqualTo("new_item");
                                          softly.assertThat(dto.priceInCents()).isEqualTo(123L);
                                        }))));

    // find one
    String findOneJsonResponse =
        RestAssured.given()
            .get("/api/" + createItemResponseDto.id())
            .then()
            .assertThat()
            .statusCode(HttpResponseStatus.OK.code())
            .extract()
            .asString();

    assertThat(new FindOneResponseDto(new JsonObject(findOneJsonResponse)))
        .isNotNull()
        .satisfies(
            dto ->
                assertSoftly(
                    softly ->
                        testContext.verify(
                            () -> {
                              softly.assertThat(dto.id()).isEqualTo(createItemResponseDto.id());
                              softly.assertThat(dto.name()).isEqualTo("new_item");
                              softly.assertThat(dto.priceInCents()).isEqualTo(123L);
                            })));

    // search
    String searchJsonResponse =
        RestAssured.given()
            .get("/api/search?s=" + createItemResponseDto.name() + "&page=0&size=100")
            .then()
            .assertThat()
            .statusCode(HttpResponseStatus.OK.code())
            .extract()
            .asString();

    assertThat(new FindAllResponseDto(new JsonObject(searchJsonResponse)).dtos())
        .singleElement()
        .satisfies(
            dto ->
                assertSoftly(
                    softly ->
                        testContext.verify(
                            () -> {
                              softly.assertThat(dto.id()).isEqualTo(createItemResponseDto.id());
                              softly.assertThat(dto.name()).isEqualTo("new_item");
                              softly.assertThat(dto.priceInCents()).isEqualTo(123L);
                            })));

    searchJsonResponse =
        RestAssured.given()
            .get(
                "/api/search?s="
                    + createItemResponseDto.name().substring(2, 5)
                    + "&page=0&size=100")
            .then()
            .assertThat()
            .statusCode(HttpResponseStatus.OK.code())
            .extract()
            .asString();

    assertThat(new FindAllResponseDto(new JsonObject(searchJsonResponse)).dtos())
        .singleElement()
        .satisfies(
            dto ->
                assertSoftly(
                    softly ->
                        testContext.verify(
                            () -> {
                              softly.assertThat(dto.id()).isEqualTo(createItemResponseDto.id());
                              softly.assertThat(dto.name()).isEqualTo("new_item");
                              softly.assertThat(dto.priceInCents()).isEqualTo(123L);
                            })));

    searchJsonResponse =
        RestAssured.given()
            .get("/api/search?s=bad&page=0&size=100")
            .then()
            .assertThat()
            .statusCode(HttpResponseStatus.OK.code())
            .extract()
            .asString();

    assertThat(new FindAllResponseDto(new JsonObject(searchJsonResponse)))
        .extracting(FindAllResponseDto::dtos)
        .asList()
        .isEmpty();

    // edit
    RestAssured.given()
        .body(new UpdateItemRequestDto("new_item_updated", 321L).toJson().encode())
        .post("/api/edit/" + createItemResponseDto.id())
        .then()
        .assertThat()
        .statusCode(HttpResponseStatus.NO_CONTENT.code());

    findOneJsonResponse =
        RestAssured.given()
            .get("/api/" + createItemResponseDto.id())
            .then()
            .assertThat()
            .statusCode(HttpResponseStatus.OK.code())
            .extract()
            .asString();

    assertThat(new FindOneResponseDto(new JsonObject(findOneJsonResponse)))
        .isNotNull()
        .satisfies(
            dto ->
                assertSoftly(
                    softly ->
                        testContext.verify(
                            () -> {
                              softly.assertThat(dto.id()).isEqualTo(createItemResponseDto.id());
                              softly.assertThat(dto.name()).isEqualTo("new_item_updated");
                              softly.assertThat(dto.priceInCents()).isEqualTo(321L);
                            })));

    // delete
    RestAssured.given()
        .delete("/api/" + createItemResponseDto.id())
        .then()
        .assertThat()
        .statusCode(HttpResponseStatus.NO_CONTENT.code());

    // fetch
    RestAssured.given()
        .get("/api/" + createItemResponseDto.id())
        .then()
        .assertThat()
        .statusCode(HttpResponseStatus.NOT_FOUND.code());

    getItemsJsonResponse =
        RestAssured.given()
            .get("/api/items")
            .then()
            .assertThat()
            .statusCode(HttpResponseStatus.OK.code())
            .extract()
            .asString();

    assertThat(new FindAllResponseDto(new JsonObject(getItemsJsonResponse)))
        .isNotNull()
        .extracting(FindAllResponseDto::dtos)
        .satisfies(dtos -> assertThat(dtos).isEmpty());

    // consume message from stream
    Redis client =
        Redis.createClient(
            vertx, new Config.RedisConfig("localhost", redis.getMappedPort(6379), 0).uri());
    RedisAPI redisAPI = RedisAPI.api(client);

    redisAPI
        .xlen("catalog-stream")
        .onFailure(testContext::failNow)
        .onSuccess(
            result -> {
              testContext.verify(
                  () -> {
                    assertThat(result.type()).isEqualTo(ResponseType.NUMBER);
                    assertThat(result.toLong()).isEqualTo(2L);
                  });
            });

    redisAPI
        .xrange(List.of("catalog-stream", "-", "+"))
        .onFailure(testContext::failNow)
        .onSuccess(
            result -> {
              testContext.verify(
                  () -> {
                    assertThat(result.type()).isEqualTo(ResponseType.MULTI);

                    Response next = result.iterator().next();
                    assertThat(next.type()).isEqualTo(ResponseType.MULTI);

                    Iterator<Response> itr = next.iterator();
                    Response streamId = itr.next();
                    System.err.println("streamId: " + streamId);

                    Response streamMessage = itr.next();
                    Iterator<Response> sItr = streamMessage.iterator();
                    Response next1 = sItr.next();
                    Response next2 = sItr.next();

                    assertThat(next1.toString()).hasToString("item");

                    Item item = new Item(new JsonObject(next2.toString()));
                    assertThat(item).isNotNull();

                    testContext.completeNow();
                  });
            });
  }
}
