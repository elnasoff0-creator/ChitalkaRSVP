package ru.reader.rsvp;
import java.util.Random;

/** Exact 50/50 category decision independent of archive size. */
public final class TestPetQuoteChoice184 {
    public static void main(String[] args){
        Random rng=new Random(184);
        for(int i=0;i<10000;i++)if(PetQuoteChoice.chooseQuote(rng,false))throw new AssertionError("No quotes must imply ordinary lines");
        int count=0;int total=200000;
        for(int i=0;i<total;i++)if(PetQuoteChoice.chooseQuote(rng,true))count++;
        if(Math.abs(count-total/2)>total*.015)throw new AssertionError("Unfair 50/50 distribution: "+count);
        System.out.println("PASS: 50/50 tap categories, 100% normal without enabled quotes, samples="+total);
    }
}
