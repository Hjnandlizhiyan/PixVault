package com.pixvault.ml

import android.content.Context
import org.json.JSONObject
import java.text.Normalizer

class ClipTokenizer private constructor(
    private val vocab: Map<String, Int>,
    private val bpeRanks: Map<Pair<String, String>, Int>,
    private val byteToUnicode: Map<Int, Char>
) {

    private val cache = HashMap<String, List<String>>()

    fun encode(text: String): LongArray {
        val cleaned = clean(text)
        val body = ArrayList<Int>()
        for (match in PATTERN.findAll(cleaned)) {
            val byteEncoded = encodeBytes(match.value)
            for (piece in bpe(byteEncoded)) {
                body.add(vocab[piece] ?: EOS_ID)
            }
        }
        return buildSequence(body)
    }

    private fun clean(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFC)
            .replace(WHITESPACE, " ")
            .lowercase()
            .trim()

    private fun encodeBytes(token: String): String {
        val sb = StringBuilder()
        for (b in token.toByteArray(Charsets.UTF_8)) {
            sb.append(byteToUnicode[b.toInt() and 0xFF] ?: ' ')
        }
        return sb.toString()
    }

    private fun bpe(token: String): List<String> {
        cache[token]?.let { return it }
        if (token.isEmpty()) return emptyList()
        var word = ArrayList<String>(token.length)
        for (i in 0 until token.length - 1) word.add(token[i].toString())
        word.add(token[token.length - 1] + "</w>")
        var pairs = getPairs(word)
        if (pairs.isEmpty()) {
            val single = listOf(token + "</w>")
            cache[token] = single
            return single
        }
        while (true) {
            var bigram: Pair<String, String>? = null
            var bestRank = Int.MAX_VALUE
            for (p in pairs) {
                val rank = bpeRanks[p] ?: continue
                if (rank < bestRank) {
                    bestRank = rank
                    bigram = p
                }
            }
            if (bigram == null) break
            val first = bigram.first
            val second = bigram.second
            val newWord = ArrayList<String>(word.size)
            var i = 0
            while (i < word.size) {
                val j = indexOfFrom(word, first, i)
                if (j < 0) {
                    newWord.addAll(word.subList(i, word.size))
                    break
                }
                newWord.addAll(word.subList(i, j))
                i = j
                if (word[i] == first && i < word.size - 1 && word[i + 1] == second) {
                    newWord.add(first + second)
                    i += 2
                } else {
                    newWord.add(word[i])
                    i += 1
                }
            }
            word = newWord
            if (word.size == 1) break
            pairs = getPairs(word)
        }
        cache[token] = word
        return word
    }

    private fun buildSequence(body: List<Int>): LongArray {
        val ids = ArrayList<Int>(CONTEXT_LENGTH + 1)
        ids.add(BOS_ID)
        ids.addAll(body)
        ids.add(EOS_ID)
        if (ids.size > CONTEXT_LENGTH) {
            val result = LongArray(CONTEXT_LENGTH)
            for (i in 0 until CONTEXT_LENGTH - 1) result[i] = ids[i].toLong()
            result[CONTEXT_LENGTH - 1] = EOS_ID.toLong()
            return result
        }
        while (ids.size < CONTEXT_LENGTH) ids.add(EOS_ID)
        return LongArray(CONTEXT_LENGTH) { ids[it].toLong() }
    }

    companion object {
        const val CONTEXT_LENGTH = 77
        const val BOS_ID = 49406
        const val EOS_ID = 49407

        private val WHITESPACE = Regex("\\s+")

        private val PATTERN = Regex(
            "<\\|startoftext\\|>|<\\|endoftext\\|>|'s|'t|'re|'ve|'m|'ll|'d" +
                "|[\\p{L}]+|[\\p{N}]|[^\\s\\p{L}\\p{N}]+"
        )

        fun fromAssets(
            context: Context,
            vocabAsset: String = "vocab.json",
            mergesAsset: String = "merges.txt"
        ): ClipTokenizer {
            val byteToUnicode = buildByteToUnicode()

            val vocabText = context.assets.open(vocabAsset)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            val json = JSONObject(vocabText)
            val vocab = HashMap<String, Int>(json.length() * 2)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                vocab[key] = json.getInt(key)
            }

            val ranks = HashMap<Pair<String, String>, Int>(56000)
            context.assets.open(mergesAsset)
                .bufferedReader(Charsets.UTF_8)
                .use { reader ->
                    var index = 0
                    var line = reader.readLine()
                    while (line != null) {
                        if (line.isNotBlank() && !line.startsWith("#")) {
                            val sp = line.indexOf(' ')
                            if (sp > 0) {
                                ranks[line.substring(0, sp) to line.substring(sp + 1)] = index
                                index++
                            }
                        }
                        line = reader.readLine()
                    }
                }

            return ClipTokenizer(vocab, ranks, byteToUnicode)
        }

        private fun getPairs(word: List<String>): Set<Pair<String, String>> {
            val pairs = LinkedHashSet<Pair<String, String>>()
            for (i in 0 until word.size - 1) pairs.add(word[i] to word[i + 1])
            return pairs
        }

        private fun indexOfFrom(list: List<String>, element: String, start: Int): Int {
            for (i in start until list.size) if (list[i] == element) return i
            return -1
        }

        private fun buildByteToUnicode(): Map<Int, Char> {
            val bs = ArrayList<Int>(256)
            for (b in '!'.code..'~'.code) bs.add(b)
            for (b in 0xA1..0xAC) bs.add(b)
            for (b in 0xAE..0xFF) bs.add(b)
            val cs = ArrayList<Int>(bs)
            var n = 0
            for (b in 0..255) {
                if (!bs.contains(b)) {
                    bs.add(b)
                    cs.add(256 + n)
                    n++
                }
            }
            val map = HashMap<Int, Char>(256)
            for (i in bs.indices) map[bs[i]] = cs[i].toChar()
            return map
        }
    }
}