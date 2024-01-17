/* Licensed under Apache-2.0 2023. */
package com.example.iam.rpc;

import com.example.commons.config.Config;
import com.example.iam.rpc.verticle.RpcVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
public abstract class TestBase {

  @BeforeEach
  void prepare(Vertx vertx, VertxTestContext testContext) {
    Config config =
        new Config(
            new Config.HttpConfig(0),
            new Config.GrpcConfig(0),
            new Config.RedisConfig("127.0.0.1", 6379, 0),
            null,
            Map.of(),
            new Config.VerticleConfig(1));

    JsonObject cfg = config.encode();
    vertx.deployVerticle(
        new RpcVerticle(),
        new DeploymentOptions().setConfig(cfg),
        testContext.succeedingThenComplete());
  }
}