package ru.reader.rsvp;

import java.math.BigDecimal;
import java.util.Locale;

/** Natural chapter order: volume 2 before volume 10 and chapter 1 before 1.5. */
final class HtmlChapterOrder {
    private HtmlChapterOrder(){}
    static int compare(String a,String b){
        String left=a.toLowerCase(Locale.ROOT).replace('\\','/');
        String right=b.toLowerCase(Locale.ROOT).replace('\\','/');
        int i=0,j=0;
        while(i<left.length()&&j<right.length()){
            char x=left.charAt(i),y=right.charAt(j);
            if(Character.isDigit(x)&&Character.isDigit(y)){
                int startI=i,startJ=j;
                while(i<left.length()&&Character.isDigit(left.charAt(i)))i++;
                if(i+1<left.length()&&(left.charAt(i)=='.'||left.charAt(i)==',')&&Character.isDigit(left.charAt(i+1))){
                    i++;while(i<left.length()&&Character.isDigit(left.charAt(i)))i++;
                }
                while(j<right.length()&&Character.isDigit(right.charAt(j)))j++;
                if(j+1<right.length()&&(right.charAt(j)=='.'||right.charAt(j)==',')&&Character.isDigit(right.charAt(j+1))){
                    j++;while(j<right.length()&&Character.isDigit(right.charAt(j)))j++;
                }
                String nx=left.substring(startI,i).replace(',','.');
                String ny=right.substring(startJ,j).replace(',','.');
                int diff;
                if(nx.length()>100||ny.length()>100)diff=nx.length()==ny.length()?nx.compareTo(ny):Integer.compare(nx.length(),ny.length());
                else diff=new BigDecimal(nx).compareTo(new BigDecimal(ny));
                if(diff!=0)return diff;
                // Consider 01 and 1 equivalent, but use stable secondary order.
                continue;
            }
            if(x!=y)return Character.compare(x,y);
            i++;j++;
        }
        return Integer.compare(left.length()-i,right.length()-j);
    }
}
