package se.kry.codetest;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class MainVerticle extends AbstractVerticle {

  private static final String SQL_CREATE_SERVICE_TABLE_IF_NOT_EXISTS = "CREATE TABLE IF NOT EXISTS service (url VARCHAR(128) NOT NULL)";
  private HashMap<String, String> services = new HashMap<>();
  //TODO use this
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

  private void setRoutes(Router router){
    router.route("/*").handler(StaticHandler.create());
    router.get("/service").handler(req -> {
      List<JsonObject> jsonServices = services
          .entrySet()
          .stream()
          .map(service ->
              new JsonObject()
                  .put("name", service.getKey())
                  .put("status", service.getValue()))
          .collect(Collectors.toList());
      req.response()
          .putHeader("content-type", "application/json")
          .end(new JsonArray(jsonServices).encode());
    });
    router.post("/service").handler(req -> {
      JsonObject jsonBody = req.getBodyAsJson();
      services.put(jsonBody.getString("url"), "UNKNOWN");
      req.response()
          .putHeader("content-type", "text/plain")
          .end("OK");
    });
  }

}



