package com.emanueledipietro.remodex.feature.turn;

import io.noties.prism4j.Prism4j;

public final class RemodexPrism4jFactory {
    private RemodexPrism4jFactory() {
    }

    public static Prism4j create() {
        return new Prism4j(new RemodexPrismGrammarLocator());
    }
}
