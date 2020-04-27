package se.kry.codetest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;

public class MainVerticle extends AbstractVerticle {

  private static final String SQL_CREATE_SERVICE_TABLE_IF_NOT_EXISTS = "CREATE TABLE IF NOT EXISTS service (url VARCHAR(128) NOT NULL UNIQUE)";
  private static final String URL_PARAM = "url";
  private static final String OK = "OK";
  private static final String NOT_FOUND = "Not found.";
  private Map<String, String> services = new ConcurrentHashMap<>();
  // TODO use this
  private DBConnector connector;
  private BackgroundPoller poller = new BackgroundPoller();

  @Override
  public void start(Future<Void> startFuture) {
    Future<Void> steps = prepareDatabase().compose(v -> startHttpServer());
    services.put("https://www.kry.se", "UNKNOWN");
    vertx.setPeriodic(1000 * 60, timerId -> poller.pollServices(services));
    steps.setHandler(ar -> {
      if (ar.succeeded()) {
        System.out.println("KRY code test service started");
        startFuture.complete();
      } else {
        startFuture.fail(ar.cause());
      }
    });
  }

  private Future<Void> prepareDatabase() {
    this.connector = new DBConnector(vertx);
    final Future<Void> databaseFuture = Future.future();
    connector.query(SQL_CREATE_SERVICE_TABLE_IF_NOT_EXISTS).setHandler(done -> {
      if (done.succeeded()) {
        System.out.println("completed db migrations");
        databaseFuture.complete();
      } else {
        databaseFuture.fail(done.cause());
      }
    });
    return databaseFuture;
  }

  private Future<Void> startHttpServer() {
    final Future<Void> httpServerStartFuture = Future.future();
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());
    setRoutes(router);
    vertx.createHttpServer().requestHandler(router).listen(8080, result -> {
      if (result.succeeded()) {
        httpServerStartFuture.complete();
      } else {
        httpServerStartFuture.fail(result.cause());
      }
    });
    return httpServerStartFuture;
  }

  private void setRoutes(Router router) {
    router.route("/*").handler(StaticHandler.create());
    router.get("/service").handler(this::getServicesHandler);
    router.post("/service").handler(this::createServiceHandler);
    router.delete("/service").handler(this::deleteServiceHandler);
  }

  private void createServiceHandler(RoutingContext context) {
    JsonObject jsonBody = context.getBodyAsJson();
    services.put(jsonBody.getString("url"), "UNKNOWN");
    context.response().putHeader("content-type", "text/plain").end("OK");
  }

  private void getServicesHandler(RoutingContext context) {
    List<JsonObject> jsonServices = services.entrySet().stream()
        .map(service -> new JsonObject().put("name", service.getKey()).put("status", service.getValue()))
        .collect(Collectors.toList());
    context.response().putHeader("content-type", "application/json").end(new JsonArray(jsonServices).encode());
  }

  private void deleteServiceHandler(RoutingContext context) {
    int responseStatusCode = 200;
    String responseMessage = OK;
    if (context.queryParams().contains(URL_PARAM)) {
      String url = context.queryParams().get(URL_PARAM);
      String status = this.services.remove(url);
      if (status == null) {
        responseStatusCode = 404;
        responseMessage = NOT_FOUND;
      }
    } else {
      responseStatusCode = 400;
      responseMessage = "Missing url param.";
    }
    context.response().putHeader("content-type", "text/plain").setStatusCode(responseStatusCode).end(responseMessage);
  }

}
