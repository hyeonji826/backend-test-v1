package im.bigs.pg.domain.partner

/**
 * Partner(제휴사) 마스터.
 * - 도메인 계층의 순수 데이터 모델로서 프레임워크에 의존하지 않습니다.
 * - 활성화 여부(active)에 따라 결제 가능/불가를 구분할 수 있습니다.
 *
 * @property id 내부 식별자(PK)
 * @property code 비즈니스 식별 코드(사내/외부 시스템 연계에 활용)
 * @property name 제휴사 명칭
 * @property active 사용 가능 여부
 */
data class Partner(
    val id: Long,
    val code: String,
    val name: String,
    val active: Boolean = true,
)
