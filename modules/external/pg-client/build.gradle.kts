tasks.jar {
    enabled = true
}

tasks.bootJar {
    enabled = false
}

dependencies {
    implementation(projects.modules.application)
    implementation(projects.modules.domain)
    implementation(libs.spring.boot.starter.web)
}
