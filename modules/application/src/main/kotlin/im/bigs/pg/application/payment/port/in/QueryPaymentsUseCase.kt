package im.bigs.pg.application.payment.port.`in`

/**
 * 결제 이력 조회 유스케이스(입력 포트).
 * - 통계와 커서 기반 페이지네이션을 함께 제공합니다.
 * - 커서는 createdAt/Id 기준 내림차순 정렬을 전제로 생성/해석됩니다.
 */
interface QueryPaymentsUseCase {
    fun query(filter: QueryFilter): QueryResult
}
