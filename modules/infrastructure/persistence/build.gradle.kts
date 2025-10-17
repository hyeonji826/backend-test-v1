tasks.jar {
    enabled = true
}

tasks.bootJar {
    enabled = false
}

dependencies {
    implementation(projects.modules.domain)
    implementation(projects.modules.application)
    implementation(libs.spring.boot.starter.jpa)
    runtimeOnly(libs.database.h2)
    runtimeOnly(libs.database.mariadb)

    testImplementation(libs.spring.boot.starter.test) {
        exclude(module = "mockito-core")
    }
    testImplementation(libs.database.h2)
}
