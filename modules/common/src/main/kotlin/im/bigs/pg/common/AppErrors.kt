package im.bigs.pg.common

open class NotFoundException(message: String) : RuntimeException(message)
open class ConflictException(message: String) : RuntimeException(message)
open class UnprocessableException(message: String) : RuntimeException(message)
open class UnauthorizedException(message: String) : RuntimeException(message)
