package com.example.proxypotps.probe

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream

interface SocketLike : Closeable {
    val input: InputStream
    val output: OutputStream
}
