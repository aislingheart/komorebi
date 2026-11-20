/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Local Decision Model
 * File: TinyDecideEngine.kt
 * Description: Pure-Kotlin on-device runtime for TheREZOR/TinyDecide (Apache-2.0) Jev-style
 *              non-autoregressive ELECTRA-small decision models (both 10.4M Q4 and 13.8M Full).
 *              Zero external ML libraries; supports choice, noul, score, and span heads.
 */

package com.sameerasw.essentials.assistant.decision

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sqrt
import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class TinyDecideQuestion(
    val type: String, // "choice" | "noul" | "score" | "span"
    val text: String,
    val options: List<String> = emptyList(),
)

data class TinyDecideRequest(
    val state: String,
    val questions: List<TinyDecideQuestion>,
)

data class TinyDecideAnswer(
    val type: String,
    val probs: FloatArray = FloatArray(0),
    val pick: Int = -1,
    val confidence: Float = 0f,
    val p: Float = 0f,
    val score: Float = 0f,
    val pPresent: Float = 0f,
    val pSpan: Float = 0f,
    val text: String = "",
)

data class TinyDecideResponse(
    val answers: List<TinyDecideAnswer>,
    val truncated: Boolean,
    val latencyMs: Long,
)

internal class TinyDecideWordPiece(
    vocabList: List<String?>,
    unkToken: String,
    private val prefix: String,
    private val maxChars: Int,
) {
    private val vocab = HashMap<String, Int>(vocabList.size * 2)
    private val unkId: Int

    init {
        for (i in vocabList.indices) {
            val token = vocabList[i]
            if (token != null) {
                vocab[token] = i
            }
        }
        unkId = vocab[unkToken] ?: 0
    }

    data class EncodedText(
        val ids: IntArray,
        val starts: IntArray,
        val ends: IntArray,
    )

    private data class NormChar(
        val ch: Char,
        val start: Int,
        val end: Int,
    )

    fun encode(text: String): EncodedText = encodeUntil(text, Int.MAX_VALUE)

    fun encodeUntil(text: String, limit: Int): EncodedText {
        val ids = ArrayList<Int>(min(text.length + 4, 256))
        val starts = ArrayList<Int>(min(text.length + 4, 256))
        val ends = ArrayList<Int>(min(text.length + 4, 256))
        val cur = ArrayList<NormChar>(32)

        fun flush() {
            if (cur.isNotEmpty()) {
                emitWord(cur, ids, starts, ends)
                cur.clear()
            }
        }

        var u = 0
        val len = text.length
        while (u < len) {
            if (ids.size > limit) break
            val cp = text.codePointAt(u)
            val charCount = Character.charCount(cp)
            val a = u
            val b = u + charCount
            u = b

            if (cp == 0 || cp == 0xFFFD) continue
            val type = Character.getType(cp)
            val isCtrl = type == Character.CONTROL.toInt() || type == Character.FORMAT.toInt()
            if (isCtrl && cp != '\t'.code && cp != '\n'.code && cp != '\r'.code) continue

            if (cp == ' '.code || cp == '\t'.code || cp == '\n'.code || cp == '\r'.code ||
                type == Character.SPACE_SEPARATOR.toInt()
            ) {
                flush()
                continue
            }

            if (isCjk(cp)) {
                flush()
                if (charCount == 1) {
                    emitWord(listOf(NormChar(cp.toChar(), a, b)), ids, starts, ends)
                } else {
                    // Supplementary CJK codepoints are uncommon in WordPiece 16K/30K vocab -> UNK
                    val raw = String(Character.toChars(cp))
                    val id = vocab[raw] ?: unkId
                    ids.add(id)
                    starts.add(a)
                    ends.add(b)
                }
                continue
            }

            val pieceStr = text.substring(a, b)
            val nfd = Normalizer.normalize(pieceStr, Normalizer.Form.NFD)
            for (idx in nfd.indices) {
                val dc = nfd[idx]
                if (Character.getType(dc) == Character.NON_SPACING_MARK.toInt()) continue
                val lowered = dc.lowercase()
                for (li in lowered.indices) {
                    val lc = lowered[li]
                    when {
                        lc == ' ' || lc.isWhitespace() -> flush()
                        isPunct(lc) -> {
                            flush()
                            emitWord(listOf(NormChar(lc, a, b)), ids, starts, ends)
                        }
                        cur.size > maxChars -> cur[cur.size - 1] = NormChar(lc, a, b)
                        else -> cur.add(NormChar(lc, a, b))
                    }
                }
            }
        }
        if (ids.size <= limit) flush()

        return EncodedText(
            ids = ids.toIntArray(),
            starts = starts.toIntArray(),
            ends = ends.toIntArray(),
        )
    }

    private fun emitWord(
        word: List<NormChar>,
        ids: MutableList<Int>,
        starts: MutableList<Int>,
        ends: MutableList<Int>,
    ) {
        val wholeStart = word.first().start
        val wholeEnd = word.last().end
        if (word.size > maxChars) {
            ids.add(unkId)
            starts.add(wholeStart)
            ends.add(wholeEnd)
            return
        }
        val pieceIds = ArrayList<Int>(4)
        val pieceStarts = ArrayList<Int>(4)
        val pieceEnds = ArrayList<Int>(4)
        val sb = StringBuilder(word.size + prefix.length)
        var start = 0
        while (start < word.size) {
            var end = word.size
            var got = -1
            while (start < end) {
                sb.setLength(0)
                if (start > 0) sb.append(prefix)
                for (i in start until end) sb.append(word[i].ch)
                val id = vocab[sb.toString()]
                if (id != null) {
                    got = id
                    break
                }
                end--
            }
            if (got < 0) {
                ids.add(unkId)
                starts.add(wholeStart)
                ends.add(wholeEnd)
                return
            }
            pieceIds.add(got)
            pieceStarts.add(word[start].start)
            pieceEnds.add(word[end - 1].end)
            start = end
        }
        for (i in pieceIds.indices) {
            ids.add(pieceIds[i])
            starts.add(pieceStarts[i])
            ends.add(pieceEnds[i])
        }
    }

    companion object {
        private fun isCjk(cp: Int): Boolean =
            (cp in 0x4E00..0x9FFF) ||
                (cp in 0x3400..0x4DBF) ||
                (cp in 0x20000..0x2A6DF) ||
                (cp in 0x2A700..0x2B73F) ||
                (cp in 0x2B740..0x2B81F) ||
                (cp in 0x2B820..0x2CEAF) ||
                (cp in 0xF900..0xFAFF) ||
                (cp in 0x2F800..0x2FA1F)

        private fun isPunct(ch: Char): Boolean {
            val c = ch.code
            if (c in 33..47 || c in 58..64 || c in 91..96 || c in 123..126) return true
            val t = Character.getType(ch)
            return t == Character.CONNECTOR_PUNCTUATION.toInt() ||
                t == Character.DASH_PUNCTUATION.toInt() ||
                t == Character.START_PUNCTUATION.toInt() ||
                t == Character.END_PUNCTUATION.toInt() ||
                t == Character.INITIAL_QUOTE_PUNCTUATION.toInt() ||
                t == Character.FINAL_QUOTE_PUNCTUATION.toInt() ||
                t == Character.OTHER_PUNCTUATION.toInt()
        }
    }
}

internal class TinyDecideWeights(
    meta: JsonObject,
    rawBytes: ByteArray,
) {
    data class Tensor(val data: FloatArray, val shape: IntArray)

    private class LazyEmbedding(
        val dtype: String,
        val rows: Int,
        val cols: Int,
        val offset: Int,
        val scaleOffset: Int,
        val bytes: ByteArray,
    ) {
        fun copyRow(row: Int, dst: FloatArray, dstOffset: Int) {
            when (dtype) {
                "f32" -> {
                    val bb = ByteBuffer.wrap(bytes, offset + row * cols * 4, cols * 4)
                        .order(ByteOrder.LITTLE_ENDIAN)
                    for (c in 0 until cols) dst[dstOffset + c] = bb.float
                }
                "q4" -> {
                    val nb = cols / 32
                    val scOff = scaleOffset + row * nb * 2
                    val nibBase = offset + row * nb * 16
                    for (b in 0 until nb) {
                        val lo = bytes[scOff + b * 2].toInt() and 0xFF
                        val hi = bytes[scOff + b * 2 + 1].toInt() and 0xFF
                        val d = Float.fromBits(((hi shl 8) or lo) shl 16)
                        val no = nibBase + b * 16
                        val wo = dstOffset + b * 32
                        for (k in 0 until 16) {
                            val v = bytes[no + k].toInt() and 0xFF
                            dst[wo + k] = ((v and 0x0F) - 8) * d
                            dst[wo + k + 16] = (((v ushr 4) and 0x0F) - 8) * d
                        }
                    }
                }
                else -> { // int8
                    val scBits = readIntLE(bytes, scaleOffset + row * 4)
                    val sc = Float.fromBits(scBits)
                    val ro = offset + row * cols
                    for (c in 0 until cols) {
                        dst[dstOffset + c] = bytes[ro + c].toFloat() * sc
                    }
                }
            }
        }
    }

    private val tensors = HashMap<String, Tensor>(256)
    private var lazyWordEmb: LazyEmbedding? = null

    init {
        val arr = meta.getAsJsonArray("tensors")
        for (idx in 0 until arr.size()) {
            val e = arr[idx].asJsonObject
            val name = e.get("name").asString
            val shapeArr = e.getAsJsonArray("shape")
            val shape = IntArray(shapeArr.size()) { shapeArr[it].asInt }
            val dtype = e.get("dtype").asString
            val offset = e.get("offset").asInt
            val scaleOffset = e.get("scale_offset")?.takeIf { !it.isJsonNull }?.asInt ?: 0

            // Keep the large word embedding table compact and decode only requested token rows.
            if (name == "m.word.weight" && shape.size == 2) {
                lazyWordEmb = LazyEmbedding(dtype, shape[0], shape[1], offset, scaleOffset, rawBytes)
                continue
            }

            var total = 1
            for (dim in shape) total *= dim
            val data = when (dtype) {
                "f32" -> {
                    val out = FloatArray(total)
                    val bb = ByteBuffer.wrap(rawBytes, offset, total * 4).order(ByteOrder.LITTLE_ENDIAN)
                    for (i in 0 until total) out[i] = bb.float
                    out
                }
                "q4" -> {
                    val rows = shape[0]
                    val cols = shape[1]
                    val nb = cols / 32
                    val out = FloatArray(total)
                    for (r in 0 until rows) {
                        val scRow = scaleOffset + r * nb * 2
                        val nibRow = offset + r * nb * 16
                        val outRow = r * cols
                        for (b in 0 until nb) {
                            val lo = rawBytes[scRow + b * 2].toInt() and 0xFF
                            val hi = rawBytes[scRow + b * 2 + 1].toInt() and 0xFF
                            val d = Float.fromBits(((hi shl 8) or lo) shl 16)
                            val no = nibRow + b * 16
                            val wo = outRow + b * 32
                            for (k in 0 until 16) {
                                val v = rawBytes[no + k].toInt() and 0xFF
                                out[wo + k] = ((v and 0x0F) - 8) * d
                                out[wo + k + 16] = (((v ushr 4) and 0x0F) - 8) * d
                            }
                        }
                    }
                    out
                }
                else -> { // int8
                    val rows = shape[0]
                    val cols = shape[1]
                    val out = FloatArray(total)
                    for (r in 0 until rows) {
                        val sc = Float.fromBits(readIntLE(rawBytes, scaleOffset + r * 4))
                        val ro = offset + r * cols
                        val wo = r * cols
                        for (c in 0 until cols) {
                            out[wo + c] = rawBytes[ro + c].toFloat() * sc
                        }
                    }
                    out
                }
            }
            tensors[name] = Tensor(data, shape)
        }
    }

    fun get(name: String): Tensor =
        tensors[name] ?: throw IllegalStateException("Missing TinyDecide tensor: $name")

    fun has(name: String): Boolean = tensors.containsKey(name) || (name == "m.word.weight" && lazyWordEmb != null)

    fun copyWordEmbedding(tokenId: Int, dst: FloatArray, dstOffset: Int) {
        val emb = lazyWordEmb
        if (emb != null) {
            val safeRow = tokenId.coerceIn(0, emb.rows - 1)
            emb.copyRow(safeRow, dst, dstOffset)
        } else {
            val t = get("m.word.weight")
            val cols = t.shape[1]
            val safeRow = tokenId.coerceIn(0, t.shape[0] - 1)
            System.arraycopy(t.data, safeRow * cols, dst, dstOffset, cols)
        }
    }

    companion object {
        private fun readIntLE(b: ByteArray, off: Int): Int =
            (b[off].toInt() and 0xFF) or
                ((b[off + 1].toInt() and 0xFF) shl 8) or
                ((b[off + 2].toInt() and 0xFF) shl 16) or
                ((b[off + 3].toInt() and 0xFF) shl 24)
    }
}

class TinyDecideEngine internal constructor(
    private val meta: JsonObject,
    rawBytes: ByteArray,
) {
    private val cfg = meta.getAsJsonObject("cfg")
    private val format = meta.getAsJsonObject("format")
    private val specials = meta.getAsJsonObject("specials")
    private val temp: FloatArray
    private val weights = TinyDecideWeights(meta, rawBytes)
    private val tokenizer: TinyDecideWordPiece

    private val arch = cfg.get("arch").asString
    private val d = cfg.get("d").asInt
    private val layers = cfg.get("layers").asInt
    private val heads = cfg.get("heads").asInt
    private val dhHead = cfg.get("dh_head").asInt
    private val embDim = cfg.get("emb_dim").asInt
    private val lnEps = cfg.get("ln_eps").asFloat
    private val layerA = cfg.get("L_a")?.takeIf { !it.isJsonNull }?.asInt ?: 0
    private val fusion = cfg.get("fusion")?.takeIf { !it.isJsonNull }?.asString ?: "all"

    private val tsMax = format.get("ts_max").asInt
    private val pQ = format.get("p_q").asInt
    private val qMax = format.get("q_max").asInt
    private val kMax = format.get("k_max").asInt
    private val spanMax = format.get("span_max").asInt

    init {
        require(arch == "electra") { "Unsupported TinyDecide arch: $arch" }
        val tempArr = meta.getAsJsonArray("temp")
        temp = FloatArray(tempArr.size()) { tempArr[it].asFloat }
        val tk = meta.getAsJsonObject("tokenizer")
        require(tk.get("kind").asString == "wordpiece") { "Unsupported TinyDecide tokenizer" }
        val vocabJson = tk.getAsJsonArray("vocab")
        val vocabList = ArrayList<String?>(vocabJson.size())
        for (i in 0 until vocabJson.size()) {
            val el = vocabJson[i]
            vocabList.add(if (el == null || el.isJsonNull) null else el.asString)
        }
        tokenizer = TinyDecideWordPiece(
            vocabList = vocabList,
            unkToken = tk.get("unk").asString,
            prefix = tk.get("prefix").asString,
            maxChars = tk.get("max_chars").asInt,
        )
    }

    private data class EncodedQuestion(
        val type: String,
        val ansIdx: Int,
        val optIndices: IntArray,
    )

    private data class EncodedRequest(
        val ids: IntArray,
        val pos: IntArray,
        val blk: IntArray,
        val kind: IntArray,
        val stIdx: IntArray,
        val stStarts: IntArray,
        val stEnds: IntArray,
        val qs: List<EncodedQuestion>,
        val truncated: Boolean,
    )

    private fun w(name: String): FloatArray = weights.get(name).data

    private fun encodeRequest(req: TinyDecideRequest): EncodedRequest {
        val st = tokenizer.encodeUntil(req.state, tsMax - 1)
        val n = min(st.ids.size, tsMax - 1)
        val ids = ArrayList<Int>(n + 64)
        val pos = ArrayList<Int>(n + 64)
        val blk = ArrayList<Int>(n + 64)
        val kind = ArrayList<Int>(n + 64)

        ids.add(specials.get("<|state|>").asInt)
        pos.add(0)
        blk.add(0)
        kind.add(K_STATE)
        for (i in 0 until n) {
            ids.add(st.ids[i])
            pos.add(i + 1)
            blk.add(0)
            kind.add(K_STATE)
        }
        val stIdx = IntArray(n) { it + 1 }
        val stStarts = st.starts.copyOf(n)
        val stEnds = st.ends.copyOf(n)

        val qs = ArrayList<EncodedQuestion>(req.questions.size)
        req.questions.forEachIndexed { qi, q ->
            val k = qi + 1
            val nOpt = q.options.size
            if (q.type == "choice" || q.type == "score") {
                require(nOpt in 2..kMax) { "Question $k needs 2..$kMax options, got $nOpt" }
            } else {
                require(nOpt == 0) { "Question $k (${q.type}) takes no options" }
            }
            val bIds = ArrayList<Int>(64)
            val bKind = ArrayList<Int>(64)
            bIds.add(specials.get("<|${q.type}|>").asInt)
            bKind.add(K_QTEXT)

            val qt = tokenizer.encode(q.text).ids
            for (id in qt) {
                bIds.add(id)
                bKind.add(K_QTEXT)
            }

            val optLocal = ArrayList<Int>(nOpt)
            if (q.options.isNotEmpty()) {
                bIds.add(specials.get("<|sep|>").asInt)
                bKind.add(K_QTEXT)
                val markerKey = if (q.type == "choice") "<|o|>" else "<|lv|>"
                val markerKind = if (q.type == "choice") K_OPT else K_LV
                val markerId = specials.get(markerKey).asInt
                for (opt in q.options) {
                    val oi = tokenizer.encode(opt).ids
                    for (id in oi) {
                        bIds.add(id)
                        bKind.add(K_QTEXT)
                    }
                    optLocal.add(bIds.size)
                    bIds.add(markerId)
                    bKind.add(markerKind)
                }
            }
            val ansLocal = bIds.size
            bIds.add(specials.get("<|ans|>").asInt)
            bKind.add(K_ANS)
            require(bIds.size <= qMax) { "Question $k is too long (${bIds.size} > $qMax tokens)" }

            val base = ids.size
            for (j in bIds.indices) {
                ids.add(bIds[j])
                pos.add(pQ + j)
                blk.add(k)
                kind.add(bKind[j])
            }
            qs.add(
                EncodedQuestion(
                    type = q.type,
                    ansIdx = base + ansLocal,
                    optIndices = IntArray(optLocal.size) { base + optLocal[it] },
                ),
            )
        }

        return EncodedRequest(
            ids = ids.toIntArray(),
            pos = pos.toIntArray(),
            blk = blk.toIntArray(),
            kind = kind.toIntArray(),
            stIdx = stIdx,
            stStarts = stStarts,
            stEnds = stEnds,
            qs = qs,
            truncated = st.ids.size > n,
        )
    }

    private fun continues(kind: IntArray): BooleanArray =
        BooleanArray(kind.size) { i ->
            when (fusion) {
                "all" -> true
                "markers" -> kind[i] == K_STATE || kind[i] == K_ANS || kind[i] == K_OPT || kind[i] == K_LV
                else -> kind[i] == K_STATE || kind[i] == K_ANS
            }
        }

    private fun allowedSets(enc: EncodedRequest, cont: BooleanArray, fusionLayer: Boolean): Array<IntArray> {
        val tLen = enc.ids.size
        val byBlk = HashMap<Int, ArrayList<Int>>()
        for (j in 0 until tLen) {
            if (fusionLayer && !cont[j]) continue
            byBlk.getOrPut(enc.blk[j]) { ArrayList() }.add(j)
        }
        val state = byBlk[0] ?: emptyList()
        return Array(tLen) { i ->
            if (fusionLayer && !cont[i]) {
                intArrayOf(i)
            } else {
                val own = byBlk[enc.blk[i]]
                if (own == null) {
                    intArrayOf(i)
                } else if (!fusionLayer || enc.blk[i] == 0) {
                    own.toIntArray()
                } else {
                    IntArray(state.size + own.size).also { merged ->
                        for (idx in state.indices) merged[idx] = state[idx]
                        for (idx in own.indices) merged[state.size + idx] = own[idx]
                    }
                }
            }
        }
    }

    private fun attend(
        q: FloatArray,
        k: FloatArray,
        v: FloatArray,
        tLen: Int,
        allowed: Array<IntArray>,
        scale: Float,
    ): FloatArray {
        val dh = d / heads
        val out = FloatArray(tLen * d)
        val scores = FloatArray(tLen)
        for (i in 0 until tLen) {
            val keys = allowed[i]
            val kLen = keys.size
            if (kLen == 0) continue
            val iBase = i * d
            for (h in 0 until heads) {
                val hOff = h * dh
                val qo = iBase + hOff
                var mx = Float.NEGATIVE_INFINITY
                for (a in 0 until kLen) {
                    val ko = keys[a] * d + hOff
                    var s = 0f
                    var c = 0
                    while (c + 3 < dh) {
                        s += q[qo + c] * k[ko + c] +
                            q[qo + c + 1] * k[ko + c + 1] +
                            q[qo + c + 2] * k[ko + c + 2] +
                            q[qo + c + 3] * k[ko + c + 3]
                        c += 4
                    }
                    while (c < dh) {
                        s += q[qo + c] * k[ko + c]
                        c++
                    }
                    s *= scale
                    scores[a] = s
                    if (s > mx) mx = s
                }
                var z = 0f
                for (a in 0 until kLen) {
                    val e = exp((scores[a] - mx).toDouble()).toFloat()
                    scores[a] = e
                    z += e
                }
                val invZ = 1f / z
                val oo = iBase + hOff
                for (a in 0 until kLen) {
                    val p = scores[a] * invZ
                    val vo = keys[a] * d + hOff
                    var c = 0
                    while (c + 3 < dh) {
                        out[oo + c] += p * v[vo + c]
                        out[oo + c + 1] += p * v[vo + c + 1]
                        out[oo + c + 2] += p * v[vo + c + 2]
                        out[oo + c + 3] += p * v[vo + c + 3]
                        c += 4
                    }
                    while (c < dh) {
                        out[oo + c] += p * v[vo + c]
                        c++
                    }
                }
            }
        }
        return out
    }

    private fun hidden(enc: EncodedRequest): FloatArray {
        val tLen = enc.ids.size
        val cont = continues(enc.kind)
        val setsC = allowedSets(enc, cont, fusionLayer = false)
        val setsF = allowedSets(enc, cont, fusionLayer = true)

        val posEmb = w("m.posemb.weight")
        val type0 = w("m.type0")
        val raw = FloatArray(tLen * embDim)

        if (weights.has("m.word.a.weight")) {
            val aTensor = weights.get("m.word.a.weight")
            val bData = w("m.word.b.weight")
            val r = aTensor.shape[1]
            val aData = aTensor.data
            for (t in 0 until tLen) {
                val ao = enc.ids[t] * r
                val po = enc.pos[t] * embDim
                val ro = t * embDim
                for (c in 0 until embDim) {
                    var s = 0f
                    val bo = c * r
                    for (j in 0 until r) s += aData[ao + j] * bData[bo + j]
                    raw[ro + c] = s + posEmb[po + c] + type0[c]
                }
            }
        } else {
            val rowBuf = FloatArray(embDim)
            for (t in 0 until tLen) {
                weights.copyWordEmbedding(enc.ids[t], rowBuf, 0)
                val po = enc.pos[t] * embDim
                val ro = t * embDim
                for (c in 0 until embDim) {
                    raw[ro + c] = rowBuf[c] + posEmb[po + c] + type0[c]
                }
            }
        }

        val normEmb = layerNorm(raw, tLen, embDim, w("m.eln.weight"), w("m.eln.bias"), lnEps)
        var x = linear(normEmb, tLen, embDim, w("m.proj.weight"), w("m.proj.bias"), d)
        val attnScale = (1.0 / sqrt((d / heads).toDouble())).toFloat()

        for (l in 0 until layers) {
            val p = "m.blocks.$l."
            val sets = if (l < layerA) setsC else setsF
            val q = linear(x, tLen, d, w(p + "q.weight"), w(p + "q.bias"), d)
            val k = linear(x, tLen, d, w(p + "k.weight"), w(p + "k.bias"), d)
            val v = linear(x, tLen, d, w(p + "v.weight"), w(p + "v.bias"), d)
            val y = attend(q, k, v, tLen, sets, attnScale)
            val o = linear(y, tLen, d, w(p + "o.weight"), w(p + "o.bias"), d)
            for (i in o.indices) o[i] += x[i]
            x = layerNorm(o, tLen, d, w(p + "ln1.weight"), w(p + "ln1.bias"), lnEps)

            val fcW = weights.get(p + "fc.weight")
            val ffDim = fcW.shape[0]
            val f = linear(x, tLen, d, fcW.data, w(p + "fc.bias"), ffDim)
            for (i in f.indices) f[i] = gelu(f[i])
            val f2 = linear(f, tLen, ffDim, w(p + "fc2.weight"), w(p + "fc2.bias"), d)
            for (i in f2.indices) f2[i] += x[i]
            x = layerNorm(f2, tLen, d, w(p + "ln2.weight"), w(p + "ln2.bias"), lnEps)
        }
        return x
    }

    fun answer(req: TinyDecideRequest): TinyDecideResponse {
        val startNs = System.nanoTime()
        val enc = encodeRequest(req)
        val x = hidden(enc)
        val tLen = enc.ids.size
        val hq = layerNorm(x, tLen, d, w("h.norm.weight"), w("h.norm.bias"), 1e-5f)
        val scale = w("h.scale")
        val invSqrtDh = (1.0 / sqrt(dhHead.toDouble())).toFloat()

        fun rowSlice(idx: Int): FloatArray = hq.copyOfRange(idx * d, (idx + 1) * d)
        fun mv(name: String, v: FloatArray): FloatArray {
            val tensor = weights.get(name)
            return linear(v, 1, d, tensor.data, null, tensor.shape[0])
        }

        val out = ArrayList<TinyDecideAnswer>(enc.qs.size)
        for (q in enc.qs) {
            val hAns = rowSlice(q.ansIdx)
            when (q.type) {
                "choice", "score" -> {
                    val sel = if (q.type == "score") 1 else 0
                    val qa = mv("h.A.$sel.weight", hAns)
                    val s = exp(scale[sel].toDouble()).toFloat()
                    val logits = FloatArray(q.optIndices.size) { i ->
                        val ov = mv("h.O.$sel.weight", rowSlice(q.optIndices[i]))
                        s * dot(qa, ov) * invSqrtDh
                    }
                    val tt = temp[if (sel == 1) 2 else 0]
                    var mx = Float.NEGATIVE_INFINITY
                    for (z in logits) if (z > mx) mx = z
                    val ex = FloatArray(logits.size)
                    var sum = 0f
                    for (i in logits.indices) {
                        val e = exp(((logits[i] - mx) / tt).toDouble()).toFloat()
                        ex[i] = e
                        sum += e
                    }
                    val probs = FloatArray(logits.size) { ex[it] / sum }
                    var bestIdx = 0
                    var bestProb = -1f
                    var entropy = 0.0
                    for (i in probs.indices) {
                        val p = probs[i]
                        if (p > bestProb) {
                            bestProb = p
                            bestIdx = i
                        }
                        if (p > 0f) entropy -= p * ln(p.toDouble())
                    }
                    val confidence = (1.0 - entropy / ln(probs.size.toDouble())).toFloat().coerceIn(0f, 1f)
                    val scoreVal = if (q.type == "score") {
                        var acc = 0f
                        val denom = (probs.size - 1).coerceAtLeast(1).toFloat()
                        for (i in probs.indices) acc += probs[i] * (i / denom)
                        acc
                    } else {
                        0f
                    }
                    out.add(
                        TinyDecideAnswer(
                            type = q.type,
                            probs = probs,
                            pick = bestIdx,
                            confidence = confidence,
                            score = scoreVal,
                        ),
                    )
                }
                "noul" -> {
                    val z = dot(w("h.noul.weight"), hAns) + w("h.noul.bias")[0]
                    val p = (1.0 / (1.0 + exp((-z / temp[1]).toDouble()))).toFloat()
                    out.add(TinyDecideAnswer(type = "noul", p = p, confidence = abs(p - 0.5f) * 2f))
                }
                "span" -> {
                    val sLen = enc.stIdx.size
                    val qS = mv("h.sq.weight", hAns)
                    val startLogits = FloatArray(sLen + 1)
                    startLogits[0] = dot(w("h.snull.weight"), hAns) + w("h.snull.bias")[0]
                    for (t in 0 until sLen) {
                        val kk = mv("h.sk.weight", rowSlice(enc.stIdx[t]))
                        startLogits[1 + t] = dot(qS, kk) * invSqrtDh
                    }
                    val ls = logSoftmax(startLogits, temp[3])
                    var best = 0
                    for (t in 1 until sLen) {
                        if (ls[1 + t] > ls[1 + best]) best = t
                    }
                    val pPresent = (1.0 - exp(ls[0].toDouble())).toFloat().coerceIn(0f, 1f)
                    if (sLen == 0) {
                        out.add(TinyDecideAnswer(type = "span", pPresent = pPresent, pSpan = 0f, text = ""))
                    } else {
                        val eq = mv("h.eq.weight", hAns)
                        val es = mv("h.es.weight", rowSlice(enc.stIdx[best]))
                        val qe = FloatArray(eq.size) { eq[it] + es[it] }
                        val endLogits = FloatArray(sLen) { t ->
                            if (t in best until (best + spanMax)) {
                                dot(qe, mv("h.ek.weight", rowSlice(enc.stIdx[t]))) * invSqrtDh
                            } else {
                                -1e4f
                            }
                        }
                        val le = logSoftmax(endLogits, temp[3])
                        var bestE = best
                        for (t in 0 until sLen) {
                            if (le[t] > le[bestE]) bestE = t
                        }
                        val pSpan = exp((ls[1 + best] + le[bestE]).toDouble()).toFloat().coerceIn(0f, 1f)
                        val charStart = enc.stStarts[best].coerceIn(0, req.state.length)
                        val charEnd = enc.stEnds[bestE].coerceIn(charStart, req.state.length)
                        val extracted = req.state.substring(charStart, charEnd).trim()
                        out.add(
                            TinyDecideAnswer(
                                type = "span",
                                pPresent = pPresent,
                                pSpan = pSpan,
                                text = extracted,
                            ),
                        )
                    }
                }
                else -> throw IllegalArgumentException("Unknown TinyDecide question type: ${q.type}")
            }
        }

        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
        return TinyDecideResponse(
            answers = out,
            truncated = enc.truncated,
            latencyMs = elapsedMs,
        )
    }

    companion object {
        private const val K_STATE = 0
        private const val K_QTEXT = 1
        private const val K_ANS = 2
        private const val K_OPT = 3
        private const val K_LV = 4
        private const val INV_SQRT2 = 0.7071067811865475

        fun load(metaFile: File, modelFile: File): TinyDecideEngine {
            val meta = JsonParser.parseString(metaFile.readText(Charsets.UTF_8)).asJsonObject
            val bytes = modelFile.readBytes()
            return TinyDecideEngine(meta, bytes)
        }

        fun load(metaJson: String, modelBytes: ByteArray): TinyDecideEngine =
            TinyDecideEngine(JsonParser.parseString(metaJson).asJsonObject, modelBytes)

        private fun dot(a: FloatArray, b: FloatArray): Float {
            var s = 0f
            val n = a.size
            var i = 0
            while (i + 3 < n) {
                s += a[i] * b[i] + a[i + 1] * b[i + 1] + a[i + 2] * b[i + 2] + a[i + 3] * b[i + 3]
                i += 4
            }
            while (i < n) {
                s += a[i] * b[i]
                i++
            }
            return s
        }

        private fun linear(
            x: FloatArray,
            tLen: Int,
            din: Int,
            w: FloatArray,
            b: FloatArray?,
            dout: Int,
        ): FloatArray {
            val y = FloatArray(tLen * dout)
            for (t in 0 until tLen) {
                val xo = t * din
                val yo = t * dout
                for (j in 0 until dout) {
                    val wo = j * din
                    var s = b?.get(j) ?: 0f
                    var i = 0
                    while (i + 3 < din) {
                        s += w[wo + i] * x[xo + i] +
                            w[wo + i + 1] * x[xo + i + 1] +
                            w[wo + i + 2] * x[xo + i + 2] +
                            w[wo + i + 3] * x[xo + i + 3]
                        i += 4
                    }
                    while (i < din) {
                        s += w[wo + i] * x[xo + i]
                        i++
                    }
                    y[yo + j] = s
                }
            }
            return y
        }

        private fun layerNorm(
            x: FloatArray,
            tLen: Int,
            dim: Int,
            w: FloatArray,
            b: FloatArray?,
            eps: Float,
        ): FloatArray {
            val y = FloatArray(tLen * dim)
            val invDim = 1f / dim
            for (t in 0 until tLen) {
                val o = t * dim
                var mean = 0f
                for (i in 0 until dim) mean += x[o + i]
                mean *= invDim
                var variance = 0f
                for (i in 0 until dim) {
                    val z = x[o + i] - mean
                    variance += z * z
                }
                variance *= invDim
                val invStd = (1.0 / sqrt((variance + eps).toDouble())).toFloat()
                if (b != null) {
                    for (i in 0 until dim) {
                        y[o + i] = (x[o + i] - mean) * invStd * w[i] + b[i]
                    }
                } else {
                    for (i in 0 until dim) {
                        y[o + i] = (x[o + i] - mean) * invStd * w[i]
                    }
                }
            }
            return y
        }

        private fun erf(x: Double): Double {
            val s = sign(x)
            val a = abs(x)
            if (a < 0.5) {
                var sum = a
                var term = a
                val a2 = a * a
                for (n in 1 until 12) {
                    term *= -a2 / n
                    sum += term / (2 * n + 1)
                }
                return s * 2.0 / sqrt(Math.PI) * sum
            }
            val t = 1.0 / (1.0 + 0.5 * a)
            val y = t * exp(
                -a * a - 1.26551223 + t * (
                    1.00002368 + t * (
                        0.37409196 + t * (
                            0.09678418 + t * (
                                -0.18628806 + t * (
                                    0.27886807 + t * (
                                        -1.13520398 + t * (
                                            1.48851587 + t * (
                                                -0.82215223 + t * 0.17087277
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                ),
            )
            return s * (1.0 - y)
        }

        private fun gelu(x: Float): Float =
            (0.5 * x * (1.0 + erf(x * INV_SQRT2))).toFloat()

        private fun logSoftmax(a: FloatArray, temperature: Float): FloatArray {
            var mx = Float.NEGATIVE_INFINITY
            for (v in a) if (v > mx) mx = v
            var sum = 0.0
            for (v in a) sum += exp(((v - mx) / temperature).toDouble())
            val logZ = ln(sum)
            return FloatArray(a.size) { ((a[it] - mx) / temperature - logZ).toFloat() }
        }
    }
}
