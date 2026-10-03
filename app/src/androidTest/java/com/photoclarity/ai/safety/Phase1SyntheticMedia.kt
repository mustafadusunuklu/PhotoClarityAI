package com.photoclarity.ai.safety

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.Adler32
import java.util.zip.CRC32

/** CC0-1.0 port of tools/GenerateTestFixtures.java; no media file is stored in Git. */
internal object Phase1SyntheticMedia {
    fun png(): ByteArray {
        val pixels = ByteArray(64 * 193)
        var state = 0x50434149
        for (row in 0 until 64) {
            for (column in 1 until 193) {
                state = state * 1664525 + 1013904223
                pixels[row * 193 + column] = (state ushr 24).toByte()
            }
        }
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(-119,80,78,71,13,10,26,10))
        val header = ByteArrayOutputStream()
        DataOutputStream(header).apply {
            writeInt(64); writeInt(64); write(byteArrayOf(8,2,0,0,0))
        }
        chunk(out,"IHDR",header.toByteArray())
        // Identical uncompressed zlib block to the baseline generator, across OS/JDK versions.
        val zlib = ByteArrayOutputStream()
        zlib.write(0x78); zlib.write(0x01); zlib.write(1)
        val size = pixels.size
        zlib.write(size and 255); zlib.write(size ushr 8)
        zlib.write(size.inv() and 255); zlib.write((size.inv() ushr 8) and 255)
        zlib.write(pixels)
        val adler = Adler32().apply { update(pixels) }
        DataOutputStream(zlib).writeInt(adler.value.toInt())
        chunk(out,"IDAT",zlib.toByteArray())
        chunk(out,"IEND",byteArrayOf())
        return out.toByteArray()
    }

    private fun chunk(out: ByteArrayOutputStream, type: String, payload: ByteArray) {
        val label = type.toByteArray(Charsets.US_ASCII)
        val data = DataOutputStream(out)
        data.writeInt(payload.size); data.write(label); data.write(payload)
        val crc = CRC32().apply { update(label); update(payload) }
        data.writeInt(crc.value.toInt())
    }
}
