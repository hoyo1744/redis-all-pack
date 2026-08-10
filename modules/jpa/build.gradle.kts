plugins {
    `java-library`
    `java-test-fixtures`
    id("org.jetbrains.kotlin.plugin.jpa")
}

/** 캐시·락의 **원본 저장소**. 스탬피드 실험에서 "DB 를 몇 번 때렸나" 를 세는 대상이다. */
dependencies {
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("com.mysql:mysql-connector-j")

    testFixturesImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
    testFixturesImplementation("org.testcontainers:mysql")
}
