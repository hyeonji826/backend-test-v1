tasks.jar {
    enabled = false
}

tasks.bootJar {
    enabled = true
}

dependencies {
    implementation(projects.modules.domain)
    implementation(projects.modules.application)
    implementation(projects.modules.infrastructure.persistence)
    implementation(projects.modules.external.pgClient)
    implementation(libs.spring.boot.starter.jpa)
    implementation(libs.bundles.bootstrap)
    implementation("org.mariadb.jdbc:mariadb-java-client:3.3.3")
    implementation("org.flywaydb:flyway-core:10.18.2")
    testImplementation(libs.bundles.test)
    testImplementation(libs.spring.boot.starter.test) {
        exclude(module = "mockito-core")
    }
    testImplementation(libs.spring.mockk)
    testImplementation(libs.database.h2)
}
