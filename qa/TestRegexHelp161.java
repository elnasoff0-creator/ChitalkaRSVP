package ru.reader.rsvp;
import java.util.regex.Pattern;
/** Pure-Java smoke test: the reference contains examples and Java Pattern accepts them. */
public class TestRegexHelp161 {
    public static void main(String[] args){
        String help=RegexHelp.content();
        if(!help.contains("\\d")||!help.contains("\\p{L}")||!help.contains("(?<!abc)"))
            throw new AssertionError("Missing Java Regex help syntax");
        if(!Pattern.compile("(?iu)\\bт\\.д\\.",Pattern.UNICODE_CHARACTER_CLASS).matcher("Т.Д.").find())
            throw new AssertionError("Abbreviation example invalid");
        if(!Pattern.compile("(?<name>\\d+)(?=\\s*кг)").matcher("150 кг").find())
            throw new AssertionError("Named group example invalid");
        if(Pattern.compile("\\bзамок\\b",Pattern.UNICODE_CHARACTER_CLASS).matcher("замок").find()==false)
            throw new AssertionError("Word-boundary example invalid");
        System.out.println("PASS: Java Regex quick reference and sample expressions");
    }
}
