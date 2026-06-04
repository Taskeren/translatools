package cn.elytra.translatools.internal.utils

import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.readBytes

internal object Checksum {
    fun md5(path: Path): ByteArray {
        val md = MessageDigest.getInstance("MD5")
        md.update(path.readBytes())
        return md.digest()
    }

    fun md5String(path: Path): String = md5(path).toHexString()
}
