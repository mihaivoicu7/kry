package se.kry.codetest;

import io.vertx.core.Future;
import io.vertx.ext.web.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class BackgroundPoller {

  private static final Long TIMEOUT = 5000l;
  private final WebClient webClient;
  public static final String OK_STATUS = "OK";
  public static final String FAIL_STATUS = "FAIL";
  public static final String UNKNOWN_STATUS = "UNKNOWN";

  public BackgroundPoller(WebClient webClient) {
    this.webClient = webClient;
  }

  public Future<List<String>> pollServices(Set<String> services) {
    //TODO
    return Future.failedFuture("TODO");
  }
}
