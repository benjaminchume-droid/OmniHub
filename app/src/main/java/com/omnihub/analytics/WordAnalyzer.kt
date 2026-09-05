package com.omnihub.analytics

object WordAnalyzer {
    private val stop = setOf(
        "the","a","an","and","or","but","in","on","at","to","for","of","is","are","was","were",
        "be","been","being","have","has","had","do","does","did","will","would","could","should",
        "may","might","must","shall","can","need","i","you","he","she","it","we","they","me","my",
        "your","his","her","its","our","their","this","that","these","those","with","from","by",
        "as","into","about","than","then","so","if","not","no","yes","just","like","also","very",
        "what","when","where","who","which","how","why","all","each","every","both","few","more",
        "most","other","some","such","only","own","same","too","over","after","before","between",
        "out","up","down","off","again","further","once","here","there","when","while","during"
    )

    fun extractUserWords(text: String): List<String> {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s-]"), " ")
            .split(Regex("\\s+"))
            .map { it.trim('-') }
            .filter { it.length >= 3 && it !in stop && !it.all { c -> c.isDigit() } }
    }
}
