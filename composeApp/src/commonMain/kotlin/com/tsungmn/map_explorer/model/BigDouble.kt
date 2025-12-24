package com.tsungmn.map_explorer.model

class BigDouble(raw: Double) {
    val value = preprocess(raw)

    private fun preprocess(raw: Double): String {
        val rawStr = raw.toString()
        if ("E" !in rawStr) return rawStr

        val exp = rawStr.substringAfter("E").toInt()
        val base = rawStr.substringBefore("E")

        val (intPart, fracPart) = base.split(".").let {
            it[0] to it.getOrElse(1) { "" }
        }

        return if (exp < 0) {
            val zeros = "0".repeat(-exp - 1)
            "0.$zeros$intPart$fracPart".take(18)
        } else {
            val combined = intPart + fracPart
            if (exp >= fracPart.length) {
                combined + "0".repeat(exp - fracPart.length)
            } else {
                val idx = intPart.length + exp
                combined.substring(0, idx) + "." + combined.substring(idx)
            }
        }
    }
}
