package com.example.starter.verticle;

import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;

import com.example.starter.config.Config;
import com.example.starter.web.route.handler.PingHandler;
import com.example.starter.web.route.handler.UserHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import javax.inject.Inject;
import lombok.extern.java.Log;

@Log
public class ApiVerticle extends AbstractVerticle {

  private final UserHandler userHandler;
  private final PingHandler pingHandler;
  private final Config.HttpConfig httpConfig;

  @Inject
  public ApiVerticle(
      UserHandler userHandler, PingHandler pingHandler, Config.HttpConfig httpConfig) {
    this.userHandler = userHandler;
    this.pingHandler = pingHandler;
    this.httpConfig = httpConfig;
  }

  @Override
  public void start(Promise<Void> startPromise) {
    log.info("starting verticle");

    HttpServer server = vertx.createHttpServer();
    Router mainRouter = Router.router(vertx);
    Router apiRouter = Router.router(vertx);

    // 100kB max body size
    mainRouter
        .route(HttpMethod.POST, "/*")
        .handler(BodyHandler.create().setBodyLimit(1024L * 100L));

    // main routes
    mainRouter.get("/ping").handler(pingHandler);
    mainRouter.route("/api/*").subRouter(apiRouter);

    // api routes
    apiRouter.post("/login").handler(userHandler::login);
    apiRouter.post("/refresh").handler(userHandler::refresh);
    apiRouter.post("/register").handler(userHandler::register);

    // all unmatched requests go here
    mainRouter.route("/*").handler(ctx -> ctx.response().setStatusCode(NOT_FOUND.code()).end());

    server
        .requestHandler(mainRouter)
        .listen(
            httpConfig.port(),
            res -> {
              if (res.succeeded()) {
                log.info("started http server");
                startPromise.complete();
              } else {
                startPromise.fail(res.cause());
              }
            });
  }

  @Override
  public void stop(Promise<Void> stopPromise) {
    log.warning("stopping");
    stopPromise.complete();
  }
}
