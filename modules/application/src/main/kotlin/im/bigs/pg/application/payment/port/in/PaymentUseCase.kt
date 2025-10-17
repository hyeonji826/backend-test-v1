package im.bigs.pg.application.payment.port.`in`

import im.bigs.pg.domain.payment.Payment

/**
 * 결제 생성 유스케이스(입력 포트).
 * - 어댑터(REST 등)에서 수신한 요청을 도메인/아웃바운드 포트로 위임합니다.
 * - 구현은 결제 승인 → 수수료 계산 → 영속화의 순서로 동작하는 것이 일반적입니다.
 */
interface PaymentUseCase {
    /**
     * 결제를 수행하고 저장된 Payment 스냅샷을 반환합니다.
     *
     * @param command 입력 명세
     * @return 저장된 결제 스냅샷
     * @throws IllegalArgumentException 파트너가 존재하지 않거나 비활성인 경우
     * @throws IllegalStateException PG 클라이언트 또는 정책 조회 실패 등 환경 오류
     */
    fun pay(command: PaymentCommand): Payment
}
