/* Licensed under Apache-2.0 2023. */
package com.example.payment.tranactionscope.repo;

import io.vertx.core.impl.NoStackTraceException;
import java.sql.Connection;
import java.sql.SQLException;
import javax.inject.Inject;
import javax.sql.DataSource;
import lombok.extern.java.Log;

@Log
public class RepoImpl implements Repo {

  private final DataSource dataSource;

  @Inject
  RepoImpl(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public void save() {
    try (Connection connection = dataSource.getConnection()) {
      String s = connection.nativeSQL("select 1");
      log.info("save: " + s);
    } catch (SQLException e) {
      throw new NoStackTraceException(e);
    }
  }
}
