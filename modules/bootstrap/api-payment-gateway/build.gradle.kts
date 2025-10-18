tasks.jar {
    enabled = false
}

tasks.bootJar {
    enabled = true
}

dependencies {
    implementation(projects.modules.common)
    implementation(projects.modules.domain)
    implementation(projects.modules.application)
    implementation(projects.modules.infrastructure.persistence)
    implementation(projects.modules.external.pgClient)
    implementation(libs.spring.boot.starter.jpa)
    implementation(libs.bundles.bootstrap)
    implementation("org.mariadb.jdbc:mariadb-java-client:3.3.3")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")
    // Removed Flyway to fully disable it as per requirements
    testImplementation(libs.bundles.test)
    testImplementation(libs.spring.boot.starter.test) {
        exclude(module = "mockito-core")
    }
    testImplementation(libs.spring.mockk)
    // Removed H2 to fully disable it as per requirements
}
