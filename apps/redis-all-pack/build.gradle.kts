plugins {
    id("org.jetbrains.kotlin.plugin.jpa")
}

dependencies {
    implementation(project(":modules:redis-cluster"))
    implementation(project(":modules:jpa"))
    implementation(project(":modules:kafka"))
    implementation(project(":supports:monitoring"))

    implementation("org.springframework.boot:spring-boot-starter-web")

    // @TwoLevelTtlCacheable 애노테이션으로 soft TTL 을 범용화한다 (@Aspect / @Around).
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // 펜싱 토큰 대조군. ZooKeeper 순차 znode 로 단조 증가 번호를 받는다.
    implementation("org.apache.curator:curator-framework:${project.properties["curatorVersion"]}")

    // 핫키 로컬 캐시 대조군. 손제작 캐시와 달리 동시 미스를 한 번의 로드로 합쳐준다.
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${project.properties["springDocOpenApiVersion"]}")

    testImplementation(testFixtures(project(":modules:redis-cluster")))
    testImplementation(testFixtures(project(":modules:jpa")))
}
