package com.nosota.mwallet.container;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public class PostgresContainer extends PostgreSQLContainer<PostgresContainer> {

    private static final DockerImageName DOCKER_IMAGE = DockerImageName.parse("postgres:16.6")
            .asCompatibleSubstituteFor("postgres");

    private static PostgresContainer instance;

    private PostgresContainer() {
        super(DOCKER_IMAGE);
        withCommand("postgres", "-c", "log_statement=all", "-c", "log_destination=stderr");
        start();
        System.setProperty("JDBC_URL", getJdbcUrl());
        System.setProperty("JDBC_USER", getUsername());
        System.setProperty("JDBC_PASSWORD", getPassword());
    }

    @SuppressWarnings("UnusedReturnValue")
    public static PostgresContainer getInstance() {
        if (instance == null) {
            instance = new PostgresContainer();
        }
        return instance;
    }
}

