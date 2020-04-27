package se.kry.codetest;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import org.apache.commons.validator.routines.UrlValidator;
import se.kry.codetest.dao.ServiceDao;
import se.kry.codetest.dto.ServiceDTO;

public class MainVerticle extends AbstractVerticle {

  private static final String SQL_CREATE_SERVICE_TABLE_IF_NOT_EXISTS = "CREATE TABLE IF NOT EXISTS service (url VARCHAR(128) NOT NULL UNIQUE, name VARCHAR(128), date INTEGER)";
  private static final String URL_PARAM = "url";
  private static final String NAME_PARAM = "name";
  private static final String OK = "OK";
  private static final String NOT_FOUND = "Not found.";
  private final Map<String, ServiceDTO> services = new ConcurrentHashMap<>();
  private UrlValidator urlValidator = UrlValidator.getInstance();
  private DBConnector connector;
  private BackgroundPoller poller;
  private ServiceDao serviceDao;

  @Override
  public void start(Future<Void> startFuture) {
    this.connector = new DBConnector(vertx);
    this.serviceDao = new ServiceDao(this.connector);
    Future<Void> steps = prepareDatabase().compose(v -> getServicesFromDB()).compose(v -> startBackgroundPoller()).compose(v -> startHttpServer());
    steps.setHandler(ar -> {
      if (ar.succeeded()) {
        System.out.println("KRY code test service started");
        startFuture.complete();
      } else {
        startFuture.fail(ar.cause());
      }
    });
  }

  private Future<Void> startBackgroundPoller() {
    final Future<Void> startPollerFuture = Future.future();
    this.poller = new BackgroundPoller(WebClient.create(vertx));
    vertx.setPeriodic(1000 * 10, timerId -> {
      poller.pollServices(new HashSet<>(this.services.keySet())).setHandler(this::pollingFinishedHandler);
    });
    System.out.println("Backend service poller started");
    startPollerFuture.complete();
    return startPollerFuture;
  }

  private Future<Void> prepareDatabase() {
    final Future<Void> databaseFuture = Future.future();
    connector.query(SQL_CREATE_SERVICE_TABLE_IF_NOT_EXISTS).setHandler(done -> {
      if (done.succeeded()) {
        System.out.println("Completed db migrations");
        databaseFuture.complete();
      } else {
        databaseFuture.fail(done.cause());
      }
    });
    return databaseFuture;
  }

  private final Future<Void> getServicesFromDB() {
    final Future<Void> dbServices = Future.future();
    this.serviceDao.getAllServices().setHandler( done -> {
      if (done.succeeded()) {
        this.services.putAll(done.result().stream().collect(Collectors.toMap(ServiceDTO::getUrl, Function.identity())));
        System.out.println("Finished getting services from db");
        dbServices.complete();
      } else {
        dbServices.fail(done.cause());
      }
    });
    return dbServices;
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

  private void pollingFinishedHandler(AsyncResult<Map<String, Future<String>>> response) {
    if (response.succeeded()) {
      Map<String, Future<String>> pollingResult = response.result();
      pollingResult.keySet().forEach(key -> {
        pollingResult.get(key).setHandler(handler -> {
          ServiceDTO serviceDTO = this.services.get(key);
          if (Objects.nonNull(serviceDTO)) {
            serviceDTO.setStatus(handler.result());
          }
        });
      });
      System.out.println("Polling finished");
    } else {
      System.out.println("Polling failed");
    }
  }

  private void setRoutes(Router router) {
    router.route("/*").handler(StaticHandler.create());
    router.get("/service").handler(this::getServicesHandler);
    router.post("/service").handler(this::createServiceHandler);
    router.delete("/service").handler(this::deleteServiceHandler);
  }

  private void createServiceHandler(RoutingContext context) {
    int responseStatusCode = 200;
    String responseMessage = OK;
    JsonObject requestBody = context.getBodyAsJson();
    JsonObject responseBody = new JsonObject();
    if (!requestBody.containsKey(URL_PARAM) || !requestBody.containsKey(NAME_PARAM)) {
      responseStatusCode = 400;
      responseMessage = "Missing service parameters";
    } else {
      String url = requestBody.getString("url");
      if(!this.urlValidator.isValid(url)) {
        responseStatusCode = 400;
        responseMessage = "Invalid url";
      } else {
        if(!services.containsKey(url)) {
          String name = requestBody.getString("name");
          services.put(url, new ServiceDTO(url, name, BackgroundPoller.UNKNOWN_STATUS));
        }
      }
    }
    responseBody.put("message", responseMessage);
    context.response().setStatusCode(responseStatusCode).putHeader("content-type", "application/json")
        .end(responseBody.encode());
  }

  private void getServicesHandler(RoutingContext context) {
    List<JsonObject> jsonServices = services.entrySet().stream()
        .map(service -> {
          ServiceDTO serviceDTO = service.getValue();
          return new JsonObject().put(URL_PARAM, service.getKey()).put("status", serviceDTO.getStatus()).put(NAME_PARAM, serviceDTO.getName()).put("date", serviceDTO.getDateFormatted());
        })
        .collect(Collectors.toList());
    context.response().putHeader("content-type", "application/json").end(new JsonArray(jsonServices).encode());
  }

  private void deleteServiceHandler(RoutingContext context) {
    int responseStatusCode = 200;
    String responseMessage = OK;
    if (context.queryParams().contains(URL_PARAM)) {
      String url = context.queryParams().get(URL_PARAM);
      if (Objects.nonNull(this.services.remove(url))) {
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
