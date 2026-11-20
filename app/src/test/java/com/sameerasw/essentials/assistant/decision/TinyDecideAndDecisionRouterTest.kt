package com.sameerasw.essentials.assistant.decision

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.sameerasw.essentials.assistant.LocalIntentDecisionEngine
import com.sameerasw.essentials.assistant.tools.AssistantToolRegistry
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TinyDecideAndDecisionRouterTest {

    @Test
    fun `wordpiece tokenizer normalizes accents splits punctuation and preserves utf16 offsets`() {
        val vocab = listOf(
            "[PAD]", "[UNK]", "cafe", "don", "'", "t", "turn", "off", "light", "##s", "!",
        )
        val wp = TinyDecideWordPiece(
            vocabList = vocab,
            unkToken = "[UNK]",
            prefix = "##",
            maxChars = 100,
        )
        val text = "Café lights!"
        val enc = wp.encode(text)
        assertEquals(listOf(2, 8, 9, 10), enc.ids.toList())
        assertEquals("Café", text.substring(enc.starts[0], enc.ends[0]))
        assertEquals("light", text.substring(enc.starts[1], enc.ends[1]))
        assertEquals("s", text.substring(enc.starts[2], enc.ends[2]))
        assertEquals("!", text.substring(enc.starts[3], enc.ends[3]))
    }

    @Test
    fun `tinydecide engine decodes f32 q4 and int8 weights and answers choice noul score and span`() {
        val (metaJson, modelBytes) = buildSyntheticTinyDecideModel()
        val engine = TinyDecideEngine.load(metaJson, modelBytes)

        val resp = engine.answer(
            TinyDecideRequest(
                state = "turn on flashlight now",
                questions = listOf(
                    TinyDecideQuestion(
                        type = "choice",
                        text = "pick action",
                        options = listOf("flashlight", "timer", "none"),
                    ),
                    TinyDecideQuestion(type = "noul", text = "is urgent"),
                    TinyDecideQuestion(
                        type = "score",
                        text = "confidence",
                        options = listOf("low", "mid", "high"),
                    ),
                    TinyDecideQuestion(type = "span", text = "target word"),
                ),
            ),
        )

        assertFalse(resp.truncated)
        assertEquals(4, resp.answers.size)

        val choice = resp.answers[0]
        assertEquals("choice", choice.type)
        assertEquals(3, choice.probs.size)
        assertEquals(1.0f, choice.probs.sum(), 1e-4f)
        assertTrue(choice.pick in 0..2)
        assertTrue(choice.confidence in 0f..1f)

        val noul = resp.answers[1]
        assertEquals("noul", noul.type)
        assertTrue(noul.p in 0f..1f)

        val score = resp.answers[2]
        assertEquals("score", score.type)
        assertEquals(1.0f, score.probs.sum(), 1e-4f)
        assertTrue(score.score in 0f..1f)

        val span = resp.answers[3]
        assertEquals("span", span.type)
        assertTrue(span.pPresent in 0f..1f)
        assertTrue(span.pSpan in 0f..1f)
        assertTrue("turn on flashlight now".contains(span.text))
    }

    @Test
    fun `two-stage neural router picks tool extracts args and respects span hint`() = runBlocking {
        val tools = AssistantToolRegistry.all()
        var callIndex = 0
        val result = LocalDecisionRouter.routeWithNeuralBackend(
            query = "remind me to buy oat milk",
            model = LocalDecisionModel.TINYDECIDE_Q4,
            tools = tools,
            feedbackWeights = emptyMap(),
            limit = 3,
        ) { req ->
            callIndex++
            if (callIndex == 1) {
                val q = req.questions.first()
                val tasksIdx = LocalDecisionRouter.CATEGORIES.indexOfFirst { it.id == "tasks_calendar" }
                val probs = FloatArray(q.options.size) { idx -> if (idx == tasksIdx) 0.88f else 0.02f }
                TinyDecideResponse(
                    answers = listOf(TinyDecideAnswer(type = "choice", probs = probs, pick = tasksIdx, confidence = 0.85f)),
                    truncated = false,
                    latencyMs = 12L,
                )
            } else {
                val qChoice = req.questions.first()
                val addIdx = qChoice.options.indexOfFirst { it.startsWith("Add Task", ignoreCase = true) }
                    .coerceAtLeast(0)
                val probs = FloatArray(qChoice.options.size) { idx -> if (idx == addIdx) 0.91f else 0.01f }
                TinyDecideResponse(
                    answers = listOf(
                        TinyDecideAnswer(type = "choice", probs = probs, pick = addIdx, confidence = 0.9f),
                        TinyDecideAnswer(type = "span", pPresent = 0.85f, pSpan = 0.72f, text = "buy oat milk"),
                    ),
                    truncated = false,
                    latencyMs = 14L,
                )
            }
        }

        assertTrue(result.decisions.isNotEmpty())
        val top = result.decisions.first()
        assertEquals("add_task", top.tool.id)
        assertEquals("buy oat milk", top.extractedArgs["title"])
        assertTrue(top.reason.contains("TinyDecide 10.4M"))
    }

    @Test
    fun `two-stage neural router returns empty when none of these wins`() = runBlocking {
        val tools = AssistantToolRegistry.all()
        val result = LocalDecisionRouter.routeWithNeuralBackend(
            query = "asdkjhasdkj random gibberish",
            model = LocalDecisionModel.TINYDECIDE_FULL,
            tools = tools,
            feedbackWeights = emptyMap(),
            limit = 3,
        ) { req ->
            val opts = req.questions.first().options.size
            val probs = FloatArray(opts) { idx -> if (idx == opts - 1) 0.84f else 0.02f }
            TinyDecideResponse(
                answers = listOf(TinyDecideAnswer(type = "choice", probs = probs, pick = opts - 1, confidence = 0.8f)),
                truncated = false,
                latencyMs = 10L,
            )
        }
        assertTrue(result.decisions.isEmpty())
    }

    @Test
    fun `local decision model enum maps all ids and falls back cleanly`() {
        for (m in LocalDecisionModel.entries) {
            assertEquals(m, LocalDecisionModel.fromId(m.id))
        }
        assertEquals(LocalDecisionModel.BUILT_IN_LEXICAL, LocalDecisionModel.fromId("unknown_id"))
        assertEquals(LocalDecisionModel.BUILT_IN_LEXICAL, LocalDecisionModel.fromId(null))
    }

    @Test
    fun `extractArgsForTool uses span hint when regex does not strip conversational prefix`() {
        val args = LocalIntentDecisionEngine.extractArgsForTool(
            toolId = "add_task",
            query = "can you note down pick up dry cleaning",
            spanHint = "pick up dry cleaning",
        )
        assertEquals("pick up dry cleaning", args["title"])
    }

    private fun intJsonArray(vararg values: Int): JsonArray =
        JsonArray().apply { values.forEach { add(it) } }

    private fun buildSyntheticTinyDecideModel(): Pair<String, ByteArray> {
        val embDim = 32
        val d = 32
        val ff = 32
        val dhHead = 16
        val vocabSize = 32

        val bin = ByteArrayOutputStream()
        val tensors = JsonArray()

        fun writeF32(name: String, shape: IntArray, fill: Float = 0.05f) {
            val offset = bin.size()
            var n = 1
            for (dim in shape) n *= dim
            val bb = ByteBuffer.allocate(n * 4).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until n) bb.putFloat(fill * ((i % 5) - 2))
            bin.write(bb.array())
            tensors.add(
                JsonObject().apply {
                    addProperty("name", name)
                    add("shape", intJsonArray(*shape))
                    addProperty("dtype", "f32")
                    addProperty("offset", offset)
                },
            )
        }

        fun writeQ4(name: String, rows: Int, cols: Int) {
            val nb = cols / 32
            val offset = bin.size()
            val nibbles = ByteArray(rows * nb * 16) { 0x97.toByte() }
            bin.write(nibbles)
            val scaleOffset = bin.size()
            val scales = ByteBuffer.allocate(rows * nb * 2).order(ByteOrder.LITTLE_ENDIAN)
            val bf16Scale = (0.05f.toBits() ushr 16).toShort()
            for (i in 0 until rows * nb) scales.putShort(bf16Scale)
            bin.write(scales.array())
            tensors.add(
                JsonObject().apply {
                    addProperty("name", name)
                    add("shape", intJsonArray(rows, cols))
                    addProperty("dtype", "q4")
                    addProperty("offset", offset)
                    addProperty("scale_offset", scaleOffset)
                },
            )
        }

        fun writeInt8(name: String, rows: Int, cols: Int) {
            val offset = bin.size()
            val data = ByteArray(rows * cols) { ((it % 7) - 3).toByte() }
            bin.write(data)
            val scaleOffset = bin.size()
            val scales = ByteBuffer.allocate(rows * 4).order(ByteOrder.LITTLE_ENDIAN)
            for (r in 0 until rows) scales.putFloat(0.02f)
            bin.write(scales.array())
            tensors.add(
                JsonObject().apply {
                    addProperty("name", name)
                    add("shape", intJsonArray(rows, cols))
                    addProperty("dtype", "int8")
                    addProperty("offset", offset)
                    addProperty("scale_offset", scaleOffset)
                },
            )
        }

        writeF32("m.type0", intArrayOf(embDim), 0.01f)
        writeQ4("m.word.weight", vocabSize, embDim)
        writeInt8("m.posemb.weight", 320, embDim)
        writeF32("m.eln.weight", intArrayOf(embDim), 1.0f)
        writeF32("m.eln.bias", intArrayOf(embDim), 0.0f)
        writeInt8("m.proj.weight", d, embDim)
        writeF32("m.proj.bias", intArrayOf(d), 0.0f)

        val p = "m.blocks.0."
        writeQ4(p + "q.weight", d, d)
        writeF32(p + "q.bias", intArrayOf(d), 0.0f)
        writeQ4(p + "k.weight", d, d)
        writeF32(p + "k.bias", intArrayOf(d), 0.0f)
        writeQ4(p + "v.weight", d, d)
        writeF32(p + "v.bias", intArrayOf(d), 0.0f)
        writeQ4(p + "o.weight", d, d)
        writeF32(p + "o.bias", intArrayOf(d), 0.0f)
        writeF32(p + "ln1.weight", intArrayOf(d), 1.0f)
        writeF32(p + "ln1.bias", intArrayOf(d), 0.0f)
        writeQ4(p + "fc.weight", ff, d)
        writeF32(p + "fc.bias", intArrayOf(ff), 0.0f)
        writeQ4(p + "fc2.weight", d, ff)
        writeF32(p + "fc2.bias", intArrayOf(d), 0.0f)
        writeF32(p + "ln2.weight", intArrayOf(d), 1.0f)
        writeF32(p + "ln2.bias", intArrayOf(d), 0.0f)

        writeF32("h.norm.weight", intArrayOf(d), 1.0f)
        writeF32("h.norm.bias", intArrayOf(d), 0.0f)
        writeF32("h.scale", intArrayOf(2), 0.0f)
        writeInt8("h.A.0.weight", dhHead, d)
        writeInt8("h.O.0.weight", dhHead, d)
        writeInt8("h.A.1.weight", dhHead, d)
        writeInt8("h.O.1.weight", dhHead, d)
        writeF32("h.noul.weight", intArrayOf(d), 0.1f)
        writeF32("h.noul.bias", intArrayOf(1), 0.0f)
        writeInt8("h.sq.weight", dhHead, d)
        writeInt8("h.sk.weight", dhHead, d)
        writeF32("h.snull.weight", intArrayOf(d), 0.05f)
        writeF32("h.snull.bias", intArrayOf(1), 0.0f)
        writeInt8("h.eq.weight", dhHead, d)
        writeInt8("h.es.weight", dhHead, d)
        writeInt8("h.ek.weight", dhHead, d)

        val vocab = listOf(
            "[PAD]", "[UNK]", "turn", "on", "flashlight", "now", "pick", "action",
            "timer", "none", "is", "urgent", "confidence", "low", "mid", "high",
            "target", "word", "a", "b",
            "<|pad|>", "<|unk|>", "<|mask|>", "<|state|>", "<|choice|>", "<|noul|>",
            "<|score|>", "<|span|>", "<|sep|>", "<|o|>", "<|lv|>", "<|ans|>",
        )

        val meta = JsonObject().apply {
            addProperty("name", "synthetic-tinydecide")
            add(
                "cfg",
                JsonObject().apply {
                    addProperty("arch", "electra")
                    addProperty("d", d)
                    addProperty("layers", 1)
                    addProperty("L_a", 0)
                    addProperty("fusion", "all")
                    addProperty("dh_head", dhHead)
                    addProperty("heads", 2)
                    addProperty("ln_eps", 1e-12)
                    addProperty("emb_dim", embDim)
                },
            )
            add(
                "format",
                JsonObject().apply {
                    addProperty("ts_max", 128)
                    addProperty("p_q", 128)
                    addProperty("q_max", 192)
                    addProperty("k_max", 32)
                    addProperty("span_max", 16)
                },
            )
            add(
                "specials",
                JsonObject().apply {
                    addProperty("<|pad|>", 20)
                    addProperty("<|unk|>", 21)
                    addProperty("<|mask|>", 22)
                    addProperty("<|state|>", 23)
                    addProperty("<|choice|>", 24)
                    addProperty("<|noul|>", 25)
                    addProperty("<|score|>", 26)
                    addProperty("<|span|>", 27)
                    addProperty("<|sep|>", 28)
                    addProperty("<|o|>", 29)
                    addProperty("<|lv|>", 30)
                    addProperty("<|ans|>", 31)
                },
            )
            add("temp", JsonArray().apply { add(1.15); add(1.05); add(1.30); add(1.0) })
            add(
                "tokenizer",
                JsonObject().apply {
                    addProperty("kind", "wordpiece")
                    add("vocab", JsonArray().apply { vocab.forEach { add(it) } })
                    addProperty("unk", "[UNK]")
                    addProperty("prefix", "##")
                    addProperty("max_chars", 100)
                },
            )
            add("tensors", tensors)
        }

        return meta.toString() to bin.toByteArray()
    }
}
