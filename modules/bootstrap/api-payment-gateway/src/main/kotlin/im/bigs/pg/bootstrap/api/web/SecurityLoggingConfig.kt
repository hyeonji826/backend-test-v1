package im.bigs.pg.bootstrap.api.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.web.filter.OncePerRequestFilter

@Configuration
@Order(1)
class SecurityLoggingConfig : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        req: HttpServletRequest,
        res: HttpServletResponse,
        chain: FilterChain,
    ) {
        // 최소 정보만 로깅: 본문(body) 등 민감정보는 남기지 않는다.
        if (log.isDebugEnabled) {
            log.debug("HTTP {} {}", req.method, req.requestURI)
        }
        chain.doFilter(req, res)
    }
}
