package cn.elytra.translatools

internal class NoStackTraceException : RuntimeException {
    constructor(message: String, cause: Throwable) : super(message, cause)

    override fun fillInStackTrace(): Throwable {
        // don't collect stacktrace information for this exception
        return this
    }
}
