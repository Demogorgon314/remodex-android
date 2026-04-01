package com.emanueledipietro.remodex.feature.turn;

import io.noties.prism4j.annotations.PrismBundle;

@PrismBundle(
        include = {
                "clike",
                "c",
                "clojure",
                "cpp",
                "csharp",
                "css",
                "dart",
                "git",
                "go",
                "groovy",
                "java",
                "javascript",
                "json",
                "kotlin",
                "makefile",
                "markdown",
                "markup",
                "python",
                "scala",
                "sql",
                "swift",
                "yaml"
        },
        grammarLocatorClassName = ".RemodexPrismGrammarLocator"
)
final class RemodexPrismBundle {
    private RemodexPrismBundle() {
    }
}
