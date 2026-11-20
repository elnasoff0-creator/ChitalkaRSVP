package ru.reader.rsvp;

import java.util.Random;

/** A fair 50/50 category selection, independent of number of saved quotes. */
final class PetQuoteChoice {
    private PetQuoteChoice(){}
    static boolean chooseQuote(Random random,boolean hasEnabledQuotes){
        return hasEnabledQuotes && random.nextBoolean();
    }
}
