package it.fast4x.riplay.extensions.download

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile

/**
 * Writes iTunes-style tags (title, artist, album, comment, cover) into an .m4a while copying it.
 *
 * YouTube's audio-only m4a is a fragmented MP4: `ftyp moov sidx (moof mdat)*`. Every moof
 * locates its samples relative to itself (tfhd flag default-base-is-moof) and sidx relative to
 * its own end, so growing `moov` shifts nothing that matters. The two layouts where it would
 * (absolute tfhd base offsets, or a classic stco/co64 table before mdat) are handled: the first
 * one is refused, the second one patched. When the file is not what we expect, [copyWithTags]
 * returns false WITHOUT writing anything and the caller stores the file untagged.
 */
object Mp4Tagger {

    class Tags(
        val title: String,
        val artist: String,
        val album: String?,
        val comment: String?,
        val cover: ByteArray?,
    )

    private class Box(val type: String, val offset: Long, val headerSize: Int, val size: Long)

    fun copyWithTags(input: File, output: OutputStream, tags: Tags): Boolean {
        RandomAccessFile(input, "r").use { raf ->
            val top = readBoxes(raf, 0, raf.length()) ?: return false
            val moov = top.firstOrNull { it.type == "moov" } ?: return false
            if (moov.size > MAX_MOOV_BYTES) return false
            val firstMdat = top.firstOrNull { it.type == "mdat" }
            val moovBeforeData = firstMdat == null || moov.offset < firstMdat.offset

            val moovBody = ByteArray((moov.size - moov.headerSize).toInt())
            raf.seek(moov.offset + moov.headerSize)
            raf.readFully(moovBody)

            val children = readBoxes(moovBody) ?: return false
            val newUdta = buildUdta(tags)
            val keptBytes = children.filter { it.type != "udta" }.sumOf { it.size }
            val newMoovSize = 8L + keptBytes + newUdta.size
            val delta = newMoovSize - moov.size

            if (moovBeforeData && delta != 0L) {
                if (top.any { it.type == "moof" } && hasAbsoluteFragmentOffsets(raf, top)) return false
            }

            val newMoovBody = ByteArrayOutputStream(newMoovSize.toInt())
            for (child in children) {
                if (child.type == "udta") continue
                val bytes = moovBody.copyOfRange(child.offset.toInt(), (child.offset + child.size).toInt())
                if (child.type == "trak" && moovBeforeData && delta != 0L) {
                    if (!shiftChunkOffsets(bytes, delta)) return false
                }
                newMoovBody.write(bytes)
            }
            newMoovBody.write(newUdta)

            // Everything validated: from here on we only write.
            val buffer = ByteArray(COPY_BUFFER)
            for (box in top) {
                if (box === moov) {
                    val out = DataOutputStream(output)
                    out.writeInt(newMoovSize.toInt())
                    out.writeBytes("moov")
                    out.write(newMoovBody.toByteArray())
                    out.flush()
                } else {
                    raf.seek(box.offset)
                    var left = box.size
                    while (left > 0) {
                        val n = raf.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                        if (n < 0) error("Unexpected end of file")
                        output.write(buffer, 0, n)
                        left -= n
                    }
                }
            }
            output.flush()
            return true
        }
    }

    // ---- reading ----

    private fun readBoxes(raf: RandomAccessFile, start: Long, end: Long): List<Box>? {
        val boxes = mutableListOf<Box>()
        var offset = start
        val header = ByteArray(16)
        while (offset < end) {
            if (end - offset < 8) return null
            raf.seek(offset)
            raf.readFully(header, 0, 8)
            var size = u32(header, 0)
            val type = String(header, 4, 4, Charsets.ISO_8859_1)
            var headerSize = 8
            when (size) {
                1L -> {
                    if (end - offset < 16) return null
                    raf.readFully(header, 8, 8)
                    size = u64(header, 8)
                    headerSize = 16
                }
                0L -> size = end - offset
            }
            if (size < headerSize || offset + size > end) return null
            boxes += Box(type, offset, headerSize, size)
            offset += size
        }
        return boxes
    }

    private fun readBoxes(bytes: ByteArray, start: Int = 0, end: Int = bytes.size): List<Box>? {
        val boxes = mutableListOf<Box>()
        var offset = start
        while (offset < end) {
            if (end - offset < 8) return null
            var size = u32(bytes, offset)
            val type = String(bytes, offset + 4, 4, Charsets.ISO_8859_1)
            var headerSize = 8
            when (size) {
                1L -> {
                    if (end - offset < 16) return null
                    size = u64(bytes, offset + 8)
                    headerSize = 16
                }
                0L -> size = (end - offset).toLong()
            }
            if (size < headerSize || offset + size > end) return null
            boxes += Box(type, offset.toLong(), headerSize, size)
            offset += size.toInt()
        }
        return boxes
    }

    /** True when any moof/traf/tfhd carries an absolute base-data-offset (flag 0x1). */
    private fun hasAbsoluteFragmentOffsets(raf: RandomAccessFile, top: List<Box>): Boolean {
        for (moof in top) {
            if (moof.type != "moof") continue
            if (moof.size > MAX_MOOV_BYTES) return true
            val body = ByteArray((moof.size - moof.headerSize).toInt())
            raf.seek(moof.offset + moof.headerSize)
            raf.readFully(body)
            val trafs = readBoxes(body) ?: return true
            for (traf in trafs) {
                if (traf.type != "traf") continue
                val inner = readBoxes(body, (traf.offset + traf.headerSize).toInt(), (traf.offset + traf.size).toInt())
                    ?: return true
                val tfhd = inner.firstOrNull { it.type == "tfhd" } ?: return true
                val flags = u32(body, (tfhd.offset + tfhd.headerSize).toInt()) and 0xFFFFFFL
                if (flags and 0x1L != 0L) return true
            }
        }
        return false
    }

    /** Adds [delta] to every stco/co64 entry inside a trak box. False if something is off. */
    private fun shiftChunkOffsets(trak: ByteArray, delta: Long): Boolean {
        val path = listOf("mdia", "minf", "stbl")
        var start = 8
        var end = trak.size
        for (name in path) {
            val box = readBoxes(trak, start, end)?.firstOrNull { it.type == name } ?: return true
            start = (box.offset + box.headerSize).toInt()
            end = (box.offset + box.size).toInt()
        }
        val stbl = readBoxes(trak, start, end) ?: return false
        for (box in stbl) {
            val p = (box.offset + box.headerSize).toInt()
            when (box.type) {
                "stco" -> {
                    val count = u32(trak, p + 4).toInt()
                    for (i in 0 until count) {
                        val at = p + 8 + i * 4
                        val value = u32(trak, at) + delta
                        if (value < 0 || value > 0xFFFFFFFFL) return false
                        putU32(trak, at, value)
                    }
                }
                "co64" -> {
                    val count = u32(trak, p + 4).toInt()
                    for (i in 0 until count) {
                        val at = p + 8 + i * 8
                        putU64(trak, at, u64(trak, at) + delta)
                    }
                }
            }
        }
        return true
    }

    // ---- writing ----

    private fun buildUdta(tags: Tags): ByteArray {
        val ilst = ByteArrayOutputStream()
        ilst.write(textItem("©nam", tags.title))
        ilst.write(textItem("©ART", tags.artist))
        ilst.write(textItem("aART", tags.artist))
        tags.album?.takeIf { it.isNotBlank() }?.let { ilst.write(textItem("©alb", it)) }
        tags.comment?.takeIf { it.isNotBlank() }?.let { ilst.write(textItem("©cmt", it)) }
        ilst.write(textItem("©too", "Yammbo Music"))
        tags.cover?.takeIf { it.isNotEmpty() }?.let { cover ->
            val type = if (cover.size > 3 && cover[0] == 0x89.toByte() && cover[1] == 'P'.code.toByte()) 14 else 13
            ilst.write(box("covr", dataAtom(type, cover)))
        }

        val hdlr = ByteArrayOutputStream().apply {
            write(ByteArray(4))          // version + flags
            write(ByteArray(4))          // pre_defined
            write("mdir".toByteArray(Charsets.ISO_8859_1))
            write("appl".toByteArray(Charsets.ISO_8859_1))
            write(ByteArray(8))          // reserved
            write(0)                     // empty name
        }.toByteArray()

        val meta = ByteArrayOutputStream().apply {
            write(ByteArray(4))          // full box: version + flags
            write(box("hdlr", hdlr))
            write(box("ilst", ilst.toByteArray()))
        }.toByteArray()

        return box("udta", box("meta", meta))
    }

    private fun textItem(name: String, value: String): ByteArray =
        box(name, dataAtom(1, value.toByteArray(Charsets.UTF_8)))

    private fun dataAtom(type: Int, payload: ByteArray): ByteArray {
        val body = ByteArrayOutputStream(payload.size + 8)
        DataOutputStream(body).apply {
            writeInt(type)               // version 0 + well-known type (1 = UTF-8, 13 = JPEG, 14 = PNG)
            writeInt(0)                  // locale
            write(payload)
        }
        return box("data", body.toByteArray())
    }

    private fun box(type: String, body: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(body.size + 8)
        DataOutputStream(out).apply {
            writeInt(body.size + 8)
            write(type.toByteArray(Charsets.ISO_8859_1))
            write(body)
        }
        return out.toByteArray()
    }

    private fun u32(b: ByteArray, at: Int): Long =
        ((b[at].toLong() and 0xFF) shl 24) or ((b[at + 1].toLong() and 0xFF) shl 16) or
            ((b[at + 2].toLong() and 0xFF) shl 8) or (b[at + 3].toLong() and 0xFF)

    private fun u64(b: ByteArray, at: Int): Long = (u32(b, at) shl 32) or u32(b, at + 4)

    private fun putU32(b: ByteArray, at: Int, v: Long) {
        b[at] = (v ushr 24).toByte(); b[at + 1] = (v ushr 16).toByte()
        b[at + 2] = (v ushr 8).toByte(); b[at + 3] = v.toByte()
    }

    private fun putU64(b: ByteArray, at: Int, v: Long) {
        putU32(b, at, v ushr 32); putU32(b, at + 4, v and 0xFFFFFFFFL)
    }

    private const val MAX_MOOV_BYTES = 8L * 1024 * 1024
    private const val COPY_BUFFER = 64 * 1024
}
