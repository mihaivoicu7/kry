package se.kry.codetest.dao;

import java.util.List;
import java.util.stream.Collectors;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.sql.ResultSet;
import se.kry.codetest.BackgroundPoller;
import se.kry.codetest.DBConnector;
import se.kry.codetest.dto.ServiceDTO;

public class ServiceDao {

  private static final String SQL_GET_ALL_SERVICES = "SELECT * FROM service";

  private static final String SQL_CREATE_SERVICE = "INSERT INTO service values (?, ?, ?)";

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
          serviceDTO.setStatus(BackgroundPoller.UNKNOWN_STATUS);
          return serviceDTO;
        }).collect(Collectors.toList()));
      } else {
        serviceFuture.fail(response.cause());
      }
    });
    return serviceFuture;
  }

  public Future<Void> saveService(ServiceDTO serviceDTO) {
    Future<Void> createFuture = Future.future();
    if (serviceDTO != null && serviceDTO.getUrl() != null) {
      JsonArray inputParams = new JsonArray();
      inputParams.add(serviceDTO.getUrl());
      inputParams.add(serviceDTO.getName());
      inputParams.add(serviceDTO.getDate());
      dbConnector.updateWithParams(SQL_CREATE_SERVICE, inputParams).setHandler(done -> {
        if (!done.succeeded()) {
          createFuture.fail(done.cause());
        } else {
          createFuture.complete();
        }
      });
    } else {
      createFuture.fail("Missing service params.");
    }
    return createFuture;
  }
}
