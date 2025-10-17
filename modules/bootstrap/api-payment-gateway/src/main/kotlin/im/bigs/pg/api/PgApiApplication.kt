package im.bigs.pg.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * API 실행 진입점. bootstrap 모듈은 실행/환경설정만을 담당합니다.
 */
@SpringBootApplication(scanBasePackages = ["im.bigs.pg"])
class PgApiApplication

fun main(args: Array<String>) {
    runApplication<PgApiApplication>(*args)
}
