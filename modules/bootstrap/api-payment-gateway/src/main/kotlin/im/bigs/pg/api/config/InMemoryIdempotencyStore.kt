package im.bigs.pg.api.config

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class InMemoryIdempotencyStore {
    private val map = ConcurrentHashMap<String, Long>()

    fun isDuplicate(key: String): Boolean = map.containsKey(key)

    fun put(key: String, paymentId: Long) {
        map.putIfAbsent(key, paymentId)
    }
}
