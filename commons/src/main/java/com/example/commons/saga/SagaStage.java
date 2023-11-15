/* Licensed under Apache-2.0 2023. */
package com.example.commons.saga;

import static com.example.commons.mesage.Headers.SAGA_ID_HEADER;
import static com.example.commons.mesage.Headers.SAGA_ROLLBACK_HEADER;

import com.google.protobuf.GeneratedMessageV3;
import io.vertx.core.Future;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import java.time.Duration;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;

@Log
@Builder
@RequiredArgsConstructor
class SagaStage {

  private final SagaStageHandler handler;
  private final String commandAddress;
  private final EventBus eventBus;

  public Future<Message<GeneratedMessageV3>> sendCommand(String sagaId) {
    log.info("%s: sending command".formatted(sagaId));

    return handler
        .getCommand(sagaId)
        .compose(
            message ->
                eventBus.request(
                    commandAddress,
                    message,
                    new DeliveryOptions()
                        .setSendTimeout(Duration.ofSeconds(5L).toMillis())
                        .addHeader(SAGA_ID_HEADER, sagaId)));
  }

  public Future<Void> sendRollbackCommand(String sagaId) {
    log.info("%s: sending rollback command".formatted(sagaId));

    return handler
        .onRollBack(sagaId)
        .compose(
            ignore -> {
              eventBus.send(
                  commandAddress,
                  null,
                  new DeliveryOptions()
                      .addHeader(SAGA_ID_HEADER, sagaId)
                      .addHeader(SAGA_ROLLBACK_HEADER, Boolean.TRUE.toString()));
              return null;
            });
  }

  public Future<Boolean> handleResult(String sagaId, Message<GeneratedMessageV3> result) {
    log.info("%s: handle result".formatted(sagaId));
    return handler.handleResult(sagaId, result);
  }
}
