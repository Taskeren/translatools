package cn.elytra.translatools.api.utils

public interface ConsoleLogger {
    public fun info(message: String)

    public fun warn(
        message: String,
        throwable: Throwable? = null,
    )

    public fun error(
        message: String,
        throwable: Throwable? = null,
    )

    public fun isInfoEnabled(): Boolean

    public fun isWarnEnabled(): Boolean

    public fun isErrorEnabled(): Boolean

    public companion object {
        private object SystemOut : ConsoleLogger {
            var infoEnabled: Boolean = true
            var warnEnabled: Boolean = true
            var errorEnabled: Boolean = true

            override fun info(message: String) {
                if (isInfoEnabled()) {
                    println(message)
                }
            }

            override fun warn(
                message: String,
                throwable: Throwable?,
            ) {
                if (isWarnEnabled()) {
                    println(message)
                    throwable?.printStackTrace(System.out)
                }
            }

            override fun error(
                message: String,
                throwable: Throwable?,
            ) {
                if (isErrorEnabled()) {
                    println(message)
                    throwable?.printStackTrace(System.out)
                }
            }

            override fun isInfoEnabled(): Boolean = infoEnabled

            override fun isWarnEnabled(): Boolean = warnEnabled

            override fun isErrorEnabled(): Boolean = errorEnabled
        }

        private object NoOp : ConsoleLogger {
            override fun info(message: String) {}

            override fun warn(
                message: String,
                throwable: Throwable?,
            ) {
            }

            override fun error(
                message: String,
                throwable: Throwable?,
            ) {
            }

            override fun isInfoEnabled(): Boolean = false

            override fun isWarnEnabled(): Boolean = false

            override fun isErrorEnabled(): Boolean = false
        }

        public val NOOP: ConsoleLogger = NoOp
        public val CONSOLE: ConsoleLogger = SystemOut
    }
}

public inline fun ConsoleLogger.info(block: () -> String) {
    if (isInfoEnabled()) info(block())
}

public inline fun ConsoleLogger.warn(
    throwable: Throwable? = null,
    block: () -> String,
) {
    if (isWarnEnabled()) warn(block(), throwable)
}

public inline fun ConsoleLogger.error(
    throwable: Throwable? = null,
    block: () -> String,
) {
    if (isErrorEnabled()) error(block(), throwable)
}
