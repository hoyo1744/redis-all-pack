plugins {
    `java-library`
    `java-test-fixtures`
}

/**
 * 클러스터 접속과 클라이언트 3종을 여기 모은다.
 * 주차별 코드는 클라이언트 설정을 신경 쓰지 않고 주입만 받는다.
 */
dependencies {
    api("org.springframework.boot:spring-boot-starter-data-redis")
    api("redis.clients:jedis:${project.properties["jedisVersion"]}")
    api("org.redisson:redisson:${project.properties["redissonVersion"]}")

    // 4주차 직렬화 비교. JSON 과 데이터 모델이 같아 코드를 그대로 두고 포맷만 바꿀 수 있다.
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-smile")

    testFixturesImplementation("org.springframework.boot:spring-boot-starter-data-redis")
}
