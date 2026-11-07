package com.ilankog.coinwallet.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object AtomicFiles {
    /** Write to "<name>.tmp", fsync, then atomically rename over [target]. */
    @Throws(IOException::class)
    fun write(target: File, text: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        FileOutputStream(tmp).use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    @Throws(IOException::class)
    fun readOrNull(file: File): String? = if (file.exists()) file.readText(Charsets.UTF_8) else null

    @Throws(IOException::class)
    fun moveAside(file: File, to: File) {
        Files.move(file.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}
