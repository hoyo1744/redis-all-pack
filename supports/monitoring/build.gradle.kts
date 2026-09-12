plugins {
    `java-library`
}

/**
 * `api` 로 노출한다. 주차별 코드가 **자기 도메인 지표를 직접 등록**해야 하기 때문이다.
 * `implementation` 이면 MeterRegistry 가 앱 모듈의 컴파일 경로에 안 올라온다.
 */
dependencies {
    api("org.springframework.boot:spring-boot-starter-actuator")
    api("io.micrometer:micrometer-registry-prometheus")
}
