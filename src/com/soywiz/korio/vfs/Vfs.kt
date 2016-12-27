package com.soywiz.korio.vfs

import com.soywiz.korio.async.AsyncSequence
import com.soywiz.korio.async.asyncFun
import com.soywiz.korio.async.asyncGenerate
import com.soywiz.korio.stream.AsyncStream
import com.soywiz.korio.stream.readBytes
import com.soywiz.korio.stream.writeBytes

abstract class Vfs {
    val root by lazy { VfsFile(this, "/") }

    operator fun get(path: String) = root[path]

    fun createNonExistsStat(path: String) = VfsStat(VfsFile(this, path), exists = false, isDirectory = false, size = 0L)

    suspend open fun open(path: String, mode: VfsOpenMode): AsyncStream = throw UnsupportedOperationException()

    suspend open fun <T> readSpecial(path: String, clazz: Class<T>, onProgress: (Long, Long) -> Unit = { _, _ -> }): T = throw UnsupportedOperationException()

    suspend open fun readFully(path: String) = asyncFun {
        val stat = stat(path)
        readChunk(path, 0L, stat.size.toInt())
    }

    suspend open fun writeFully(path: String, data: ByteArray) = writeChunk(path, data, 0L, true)

    suspend fun readChunk(path: String, offset: Long, size: Int): ByteArray = asyncFun {
        val s = open(path, VfsOpenMode.READ)
        if (offset != 0L) s.setPosition(offset)
        s.readBytes(size)
    }

    suspend fun writeChunk(path: String, data: ByteArray, offset: Long, resize: Boolean): Unit = asyncFun {
        val s = open(path, VfsOpenMode.WRITE)
        s.setPosition(offset)
        s.writeBytes(data)
        if (resize) s.setLength(s.getPosition())
    }

    suspend open fun setSize(path: String, size: Long): Unit = throw UnsupportedOperationException()
    suspend open fun stat(path: String): VfsStat = throw UnsupportedOperationException()
    suspend open fun list(path: String): AsyncSequence<VfsStat> = throw UnsupportedOperationException()

    abstract class Proxy : Vfs() {
        abstract protected fun access(path: String): VfsFile
        open protected fun transformStat(stat: VfsStat): VfsStat = stat

        suspend override fun open(path: String, mode: VfsOpenMode) = access(path).open(mode)
        suspend override fun <T> readSpecial(path: String, clazz: Class<T>, onProgress: (Long, Long) -> Unit): T = access(path).readSpecial(clazz, onProgress)
        suspend override fun readFully(path: String): ByteArray = access(path).read()
        suspend override fun writeFully(path: String, data: ByteArray): Unit = access(path).write(data)
        suspend override fun setSize(path: String, size: Long): Unit = access(path).setSize(size)
        suspend override fun stat(path: String): VfsStat = asyncFun { transformStat(access(path).stat()) }
        suspend override fun list(path: String) = asyncGenerate {
            for (it in access(path).list()) yield(transformStat(it))
        }
    }
}