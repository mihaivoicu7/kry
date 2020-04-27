package se.kry.codetest;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.vertx.core.Future;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

public class BackgroundPoller {

  public static final String OK_STATUS = "OK";
  public static final String FAIL_STATUS = "FAIL";
  public static final String UNKNOWN_STATUS = "UNKNOWN";
  private static final Long TIMEOUT = 5000l;
  private final WebClient webClient;

  public BackgroundPoller(WebClient webClient) {
    this.webClient = webClient;
  }

  public Future<Map<String, Future<String>>> pollServices(Set<String> services) {
    Future<Map<String, Future<String>>> response = Future.future();
    Map<String, Future<String>> servicesResponseMap = new ConcurrentHashMap<>();
    services.stream().forEach(service -> {
      Future<String> serviceFuture = Future.future();
      servicesResponseMap.put(service, serviceFuture);
      try {
        this.webClient.getAbs(service).timeout(TIMEOUT).send(handler -> {
          if (handler.succeeded()) {
            HttpResponse httpResponse = handler.result();
            if (httpResponse.statusCode() == 200) {
              serviceFuture.complete(OK_STATUS);
            } else {
              serviceFuture.complete(FAIL_STATUS);
            }
          } else {
            serviceFuture.complete(FAIL_STATUS);
          }
        });
      } catch (Exception ex) {
        serviceFuture.complete(FAIL_STATUS);
      }
    });
    response.complete(servicesResponseMap);
    return response;
  }
}
