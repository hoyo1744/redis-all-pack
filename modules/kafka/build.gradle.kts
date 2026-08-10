plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    api("org.springframework.kafka:spring-kafka")

    testFixturesImplementation("org.springframework.kafka:spring-kafka")
    testFixturesImplementation("org.testcontainers:kafka")
}
