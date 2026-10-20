package ru.reader.rsvp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/** Spoiler-free companion replies for all evolution stages. */
public final class ChteicoPhrases {
    public static final String INTRO_CHICK="Привет! Я Чтенчик!";
    public static final String INTRO_READER="Я подрос! Теперь я Чтенец!";
    public static final String INTRO_ADULT="Я вырос! Теперь я Чтун!";

    public static final String[] GREETINGS={
        "Ещё одна история? Я весь во внимании!",
        "Каждая страница — новое приключение!",
        "Продолжим с того места, где остановились?",
        "Мне нравится проводить время с тобой!",
        "С хорошей книгой время летит незаметно.",
        "У тебя отличный вкус на приключения!",
        "Сегодня отличный день для новой главы.",
        "Открой книгу — я рядом!"
    };
    public static final String[] GROWTH={
        "Твои книжные приключения делают меня сильнее!",
        "Кажется, я скоро тебя удивлю!",
        "Чем больше историй, тем крепче мои крылышки!",
        "Книги помогают мне расти. Не спеши — наслаждайся!",
        "Мне нравится, когда ты возвращаешься к чтению.",
        "Хорошая история — маленький шаг к большому чуду.",
        "Любая любимая история — часть нашего пути."
    };
    public static final String[] CLASSIC={
        "Люблю неспешно путешествовать по страницам.",
        "Одна глава — и мы снова в приключении!"
    };
    public static final String[] SPEED={
        "Вот это темп! Еле успеваю крылышками!",
        "Слова мелькают, а приключения продолжаются!"
    };
    public static final String[] AUDIO={
        "Я тоже обожаю слушать истории!",
        "Включим следующую главу? Я уже слушаю!"
    };
    public static final String[] CHICK={
        "Я только учусь взмахивать книжными крылышками!",
        "Кажется, ещё немного — и я стану смелее!",
        "Давай почитаем ещё чуть-чуть?"
    };
    public static final String[] READER={
        "Я уже увереннее лечу между страницами!",
        "Мне нравится быть рядом в новых главах.",
        "С каждым днём я становлюсь мудрее."
    };
    public static final String[] ADULT={
        "Мои крылья теперь как раскрытая книга!",
        "Столько историй впереди — полетели!",
        "Я вырос, но всё так же люблю хорошие истории!"
    };
    public static final String[] RETURNING={
        "Я соскучился по нашим историям!",
        "На полке наверняка ждёт новая глава.",
        "Может, послушаем что-нибудь уютное?"
    };
    public static final String[] ACHIEVEMENTS={
        "Вот это финал! Поздравляю!",
        "Каждая завершённая история — маленькая победа!",
        "Какая замечательная книжная серия у нас получается!"
    };

    private ChteicoPhrases(){}
    private static final class Line {
        final int id;
        final String text;
        Line(int id,String text){this.id=id;this.text=text;}
    }
    public static final class Reply {
        public final int id;
        public final String text;
        public final String seen;
        private Reply(int id,String text,String seen){this.id=id;this.text=text;this.seen=seen;}
    }
    private static void put(ArrayList<Line> result,int idStart,String[] lines){
        for(int i=0;i<lines.length;i++)result.add(new Line(idStart+i,lines[i]));
    }
    /** Stage 0 = egg: an egg only animates, it does not speak.
     * Branch 1 = classic; 2 = speed (including Cursor); 3 = audiobooks.
     */
    public static Reply next(int stage,int branch,boolean returningAfterBreak,boolean achieved,
                             String alreadySeen,int lastId,Random random){
        if(stage!=ChteicoStore.CHICK&&stage!=ChteicoStore.READER&&stage!=ChteicoStore.ADULT)return null;
        ArrayList<Line> all=new ArrayList<>();
        put(all,100,GREETINGS);
        put(all,200,GROWTH);
        if(branch==ChteicoStore.CLASSIC)put(all,300,CLASSIC);
        else if(branch==ChteicoStore.SPEED)put(all,400,SPEED);
        else if(branch==ChteicoStore.AUDIO)put(all,500,AUDIO);
        if(stage==ChteicoStore.CHICK)put(all,600,CHICK);
        else if(stage==ChteicoStore.READER)put(all,700,READER);
        else put(all,800,ADULT);
        if(returningAfterBreak)put(all,900,RETURNING);
        if(achieved)put(all,1000,ACHIEVEMENTS);
        Set<Integer> used=parseSeen(alreadySeen);
        ArrayList<Line> available=new ArrayList<>();
        for(Line l:all)if(!used.contains(l.id)&&l.id!=lastId)available.add(l);
        if(available.isEmpty()){
            used.clear();
            for(Line l:all)if(l.id!=lastId)available.add(l);
        }
        if(available.isEmpty())return null;
        Line chosen=available.get(random.nextInt(available.size()));
        used.add(chosen.id);
        StringBuilder sb=new StringBuilder();
        for(Integer id:used){if(sb.length()!=0)sb.append(',');sb.append(id);}        
        return new Reply(chosen.id,chosen.text,sb.toString());
    }
    private static Set<Integer> parseSeen(String raw){
        HashSet<Integer> result=new HashSet<>();
        if(raw==null||raw.isEmpty())return result;
        for(String token:raw.split(",")){try{result.add(Integer.parseInt(token));}catch(NumberFormatException ignored){}}
        return result;
    }
    public static int tapPhraseCount(){return GREETINGS.length+GROWTH.length+CLASSIC.length+
        SPEED.length+AUDIO.length+CHICK.length+READER.length+ADULT.length+RETURNING.length+ACHIEVEMENTS.length;}
}
