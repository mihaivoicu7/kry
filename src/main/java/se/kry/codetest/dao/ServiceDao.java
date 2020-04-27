package se.kry.codetest.dao;

import io.vertx.core.Future;
import io.vertx.ext.sql.ResultSet;
import se.kry.codetest.DBConnector;
import se.kry.codetest.dto.ServiceDTO;

import java.util.List;
import java.util.stream.Collectors;

public class ServiceDao {

  private static final String SQL_GET_ALL_SERVICES = "SELECT * FROM service";

  private final DBConnector dbConnector;

  public ServiceDao(DBConnector dbConnector) {
    this.dbConnector = dbConnector;
  }

  public Future<List<ServiceDTO>> getAllServices() {
    Future<List<ServiceDTO>> serviceFuture = Future.future();
    dbConnector.query(SQL_GET_ALL_SERVICES).setHandler(response -> {
      if (response.succeeded()) {
        ResultSet resultSet = response.result();
        serviceFuture.complete(resultSet.getResults().stream().map(array -> {
          ServiceDTO serviceDTO = new ServiceDTO(array.getString(0), array.getString(1), array.getLong(2));
          return serviceDTO;
        }).collect(Collectors.toList()));
      } else {
        serviceFuture.fail(response.cause());
      }
    });
    return serviceFuture;
  }
}
