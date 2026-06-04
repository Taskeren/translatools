package cn.elytra.translatools.api.handler

public enum class DuplicatedKeyStrategy {
    DROP_OLD {
        override fun determineValue(
            old: String,
            new: String,
            key: String,
        ): String = new
    },
    DROP_NEW {
        override fun determineValue(
            old: String,
            new: String,
            key: String,
        ): String = old
    },
    PANIC {
        override fun determineValue(
            old: String,
            new: String,
            key: String,
        ): String {
            error("Key $key duplicated, old $old, new $new")
        }
    },
    ;

    public abstract fun determineValue(
        old: String,
        new: String,
        key: String,
    ): String
}
