package com.dtrps.config;

import javax.sql.DataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

/** Hibernate has created the tables by now. This adds what entities cannot express. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DatabaseInitializer implements ApplicationRunner {
  private static final String POST_SCHEMA_SCRIPT = "db/post-schema.sql";
  private static final String REFERENCE_DATA_SCRIPT = "db/reference-data.sql";
  private static final String BLOCK_SEPARATOR = "@@";

  private final DataSource dataSource;

  public DatabaseInitializer(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public void run(ApplicationArguments args) {
    ResourceDatabasePopulator postSchema =
        new ResourceDatabasePopulator(new ClassPathResource(POST_SCHEMA_SCRIPT));
    postSchema.setSeparator(BLOCK_SEPARATOR);
    postSchema.execute(dataSource);
    new ResourceDatabasePopulator(new ClassPathResource(REFERENCE_DATA_SCRIPT)).execute(dataSource);
  }
}
