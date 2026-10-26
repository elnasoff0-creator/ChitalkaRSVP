package ru.reader.rsvp;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;

/** Shared bottom sheet for reading modes and actual book contents. Never creates
 * full-book TextViews; chapters or indexed UI blocks are bound only while visible. */
final class UiNavigationSheet {
    interface IndexChoice { void onSelect(int index); }
    private UiNavigationSheet(){}
    private static int dp(Activity a,int n){return (int)(a.getResources().getDisplayMetrics().density*n+.5f);}
    private static GradientDrawable shape(Activity a,int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(a,radius));return g;}
    private static TextView label(Activity a,String value,int size,int color){TextView t=new TextView(a);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
    private static final class Sheet {
        final Dialog dialog;
        final LinearLayout content;
        Sheet(Dialog dialog,LinearLayout content){this.dialog=dialog;this.content=content;}
        boolean fromTop;
        void show(){Window window=dialog.getWindow();if(window==null)return;
            window.setWindowAnimations(fromTop?R.style.ChitalkaSheetTop:R.style.ChitalkaSheetBottom);
            if(content instanceof SheetDragLayout)((SheetDragLayout)content).attach(dialog,fromTop);
            dialog.show();
            window.setBackgroundDrawableResource(android.R.color.transparent);window.setGravity(fromTop?Gravity.TOP:Gravity.BOTTOM);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams p=window.getAttributes();p.dimAmount=.38f;window.setAttributes(p);
            // The window animation handles entry; no second nested translation.
        }
    }
    private static Sheet sheet(Activity a,String heading){
        int bg=a.getSharedPreferences("reader",0).getInt("bg",0xfff3e7d5);
        int ink=a.getSharedPreferences("reader",0).getInt("fg",0xff30221e);
        Dialog dialog=new Dialog(a);
        SheetDragLayout card=new SheetDragLayout(a);
        card.setPadding(dp(a,17),dp(a,11),dp(a,17),dp(a,20));card.setBackground(shape(a,bg,27));
        LinearLayout.LayoutParams handleLp=new LinearLayout.LayoutParams(dp(a,44),dp(a,5));handleLp.gravity=Gravity.CENTER_HORIZONTAL;
        TextView handle=label(a,"",1,ink);handle.setBackground(shape(a,0xffc3bcb3,6));card.addView(handle,handleLp);
        // Start a drag only from the handle so scrollable chapter lists are never intercepted.
        handle.setOnTouchListener(new View.OnTouchListener(){float down;
            @Override public boolean onTouch(View view,MotionEvent e){
                if(e.getActionMasked()==MotionEvent.ACTION_DOWN){down=e.getRawY();return true;}
                if(e.getActionMasked()==MotionEvent.ACTION_MOVE||e.getActionMasked()==MotionEvent.ACTION_UP){
                    float dy=e.getRawY()-down;
                    boolean fromTop=dialog.getWindow()!=null&&dialog.getWindow().getAttributes().gravity==Gravity.TOP;
                    if((fromTop?-dy:dy)>dp(a,42))dialog.dismiss();
                    return true;
                }
                return true;
            }
        });
        TextView header=label(a,heading,23,ink);header.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.setMargins(0,dp(a,12),0,dp(a,12));card.addView(header,hp);
        dialog.setContentView(card);
        dialog.setCanceledOnTouchOutside(true);
        return new Sheet(dialog,card);
    }
    static void showModes(Activity a,int current,IndexChoice choice){
        Sheet sh=sheet(a,"Выбор режима чтения");sh.fromTop=true;
        String[] names={"Чтение","Скорочтение","Курсор","Озвучка"};
        String[] captions={"Обычное чтение текста","Показ слов с заданной скоростью","Следование по тексту","Голосовое воспроизведение"};
        int[] icons={R.drawable.ic_book,R.drawable.ic_mode_speedreading,R.drawable.ic_mode_cursor,R.drawable.ic_microphone};
        int ink=a.getSharedPreferences("reader",0).getInt("fg",0xff30221e);
        int neutral=ReaderButtonColors.neutral(a,0xfffff9f0);
        for(int i=0;i<names.length;i++){
            final int option=i;
            LinearLayout line=new LinearLayout(a);line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(dp(a,12),dp(a,9),dp(a,12),dp(a,9));
            line.setBackground(shape(a,i==current?0xffdae9df:neutral,15));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(a,69));lp.bottomMargin=dp(a,7);
            sh.content.addView(line,lp);
            ImageView icon=new ImageView(a);icon.setImageResource(icons[i]);icon.setColorFilter(ink);
            line.addView(icon,new LinearLayout.LayoutParams(dp(a,27),dp(a,27)));
            LinearLayout text=new LinearLayout(a);text.setOrientation(LinearLayout.VERTICAL);text.setPadding(dp(a,12),0,0,0);
            line.addView(text,new LinearLayout.LayoutParams(0,-2,1f));
            TextView name=label(a,names[i],17,ink);text.addView(name);
            TextView sub=label(a,captions[i],12,ink);sub.setAlpha(.72f);text.addView(sub);
            if(i==current){TextView check=label(a,"✓",19,0xff388458);line.addView(check);}
            line.setOnClickListener(v->{sh.dialog.dismiss();choice.onSelect(option);});
        }
        sh.show();
    }
    interface SheetContentBuilder { void build(LinearLayout content,Dialog dialog); }
    /** The canonical top-sheet implementation, also used by reading-mode selection. */
    static void showTopCustom(Activity a,String heading,SheetContentBuilder builder){
        Sheet sh=sheet(a,heading);sh.fromTop=true;
        builder.build(sh.content,sh.dialog);
        sh.show();
    }
    static void showTopChoices(Activity a,String heading,String[] labels,int selected,IndexChoice choice){
        Sheet sh=sheet(a,heading);sh.fromTop=true;
        int ink=a.getSharedPreferences("reader",0).getInt("fg",0xff30221e);
        int neutral=ReaderButtonColors.neutral(a,0xfffff9f0);
        LinearLayout destination=sh.content;
        if(labels.length>7){
            ScrollView scrolling=new ScrollView(a);
            scrolling.setFillViewport(false);
            destination=new LinearLayout(a);destination.setOrientation(LinearLayout.VERTICAL);
            scrolling.addView(destination);
            int available=(int)(a.getResources().getDisplayMetrics().heightPixels*.58f);
            sh.content.addView(scrolling,new LinearLayout.LayoutParams(-1,Math.min(dp(a,475),available)));
        }
        for(int i=0;i<labels.length;i++){
            final int position=i;
            LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(a,16),dp(a,8),dp(a,16),dp(a,8));
            row.setBackground(shape(a,position==selected?0xffdae9df:neutral,14));
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(a,56));rp.bottomMargin=dp(a,7);
            destination.addView(row,rp);
            TextView label=label(a,labels[i],17,ink);
            row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
            if(position==selected)row.addView(label(a,"✓",20,0xff388458));
            row.setOnClickListener(v->{sh.dialog.dismiss();choice.onSelect(position);});
        }
        sh.show();
    }
    /** Icon actions share the exact same stable top sheet as the reading-mode picker. */
    static void showActions(Activity a,String heading,String[] names,int[] icons,IndexChoice choice){
        if(names.length!=icons.length)throw new IllegalArgumentException("Action icons mismatch");
        Sheet sh=sheet(a,heading);sh.fromTop=true;
        int ink=a.getSharedPreferences("reader",0).getInt("fg",0xff30221e);
        int neutral=ReaderButtonColors.neutral(a,0xfffff9f0);
        for(int i=0;i<names.length;i++){
            int option=i;
            LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(a,14),dp(a,10),dp(a,14),dp(a,10));
            row.setBackground(shape(a,neutral,15));
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(a,61));rp.bottomMargin=dp(a,8);
            sh.content.addView(row,rp);
            ImageView image=new ImageView(a);image.setImageResource(icons[i]);image.setColorFilter(ink);
            row.addView(image,new LinearLayout.LayoutParams(dp(a,25),dp(a,25)));
            TextView text=label(a,names[i],18,ink);text.setPadding(dp(a,15),0,0,0);
            row.addView(text);
            row.setContentDescription(names[i]);
            row.setOnClickListener(v->{sh.dialog.dismiss();choice.onSelect(option);});
        }
        sh.show();
    }
    /** Bottom control sheets use the same dialog, animation and swipe as Contents. */
    static void showBottomForm(Activity a,String heading,View form,Runnable done){
        Sheet sh=sheet(a,heading);
        if(form.getParent() instanceof ViewGroup)((ViewGroup)form.getParent()).removeView(form);
        LinearLayout.LayoutParams body=new LinearLayout.LayoutParams(-1,-2);
        body.bottomMargin=dp(a,12);
        sh.content.addView(form,body);
        int bg=ReaderButtonColors.accent(a,0xff94583b);
        int ink=ReaderButtonColors.contrastingText(bg);
        TextView confirm=label(a,"Готово",17,ink);
        confirm.setGravity(Gravity.CENTER);confirm.setBackground(shape(a,bg,14));
        sh.content.addView(confirm,new LinearLayout.LayoutParams(-1,dp(a,50)));
        confirm.setOnClickListener(v->{sh.dialog.dismiss();if(done!=null)done.run();});
        sh.show();
    }
    static void showAudiobookChapters(Activity owner,String[] chapters,int selected,IndexChoice choice){
        Sheet sh=sheet(owner,"Аудиоглавы");sh.fromTop=true;
        RecyclerView list=new RecyclerView(owner);list.setLayoutManager(new LinearLayoutManager(owner));
        int color=owner.getSharedPreferences("reader",0).getInt("fg",0xff30221e);
        list.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>(){
            @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent,int type){
                TextView text=label(owner,"",17,color);
                text.setPadding(dp(owner,14),dp(owner,16),dp(owner,14),dp(owner,16));
                return new RecyclerView.ViewHolder(text){};
            }
            @Override public void onBindViewHolder(RecyclerView.ViewHolder holder,int position){
                TextView row=(TextView)holder.itemView;
                row.setText(chapters[position]);row.setSingleLine(false);row.setMaxLines(2);
                row.setBackground(shape(owner,position==selected?0xffead7d1:Color.TRANSPARENT,13));
                row.setTextColor(position==selected?0xffa53b4c:color);
                row.setOnClickListener(v->{sh.dialog.dismiss();choice.onSelect(position);});
            }
            @Override public int getItemCount(){return chapters.length;}
        });
        sh.content.addView(list,new LinearLayout.LayoutParams(-1,dp(owner,Math.min(550,chapters.length*66))));
        sh.show();if(chapters.length>0)list.scrollToPosition(Math.max(0,selected-2));
    }
    private static final class TocEntry {
        final String title;final String details;final int word;
        TocEntry(String title,String details,int word){this.title=title;this.details=details;this.word=word;}
    }
    static void showContents(Activity a,BookChapterIndex chapters,BookChunkIndex blocks,
                             String full,ReadingWordMap map,int activeWord,IndexChoice choice){
        Sheet sh=sheet(a,chapters!=null&&chapters.size()>0?"Оглавление":"Текстовые блоки");
        ArrayList<TocEntry> choices=new ArrayList<>();int selected=0;
        if(chapters!=null&&chapters.size()>0){
            int currentChar=map.starts.length==0?0:map.starts[Math.max(0,Math.min(activeWord,map.starts.length-1))];
            for(int i=0;i<chapters.size();i++){
                BookChapterIndex.Chapter c=chapters.get(i);
                int word=map.atCharacter(c.startChar);
                choices.add(new TocEntry(c.title,"",word));
                if(c.startChar<=currentChar)selected=i;
            }
        }else if(blocks!=null){
            selected=blocks.chunkForWord(activeWord);
            for(int i=0;i<blocks.size();i++){
                BookChunkIndex.Chunk c=blocks.get(i);
                String preview=full.substring(c.startChar,Math.min(c.endChar,c.startChar+140)).replaceAll("\\s+"," ").trim();
                choices.add(new TocEntry((i+1)+". "+preview,c.startChar+"–"+(c.endChar-1)+" симв.",c.startWord));
            }
        }
        RecyclerView list=new RecyclerView(a);list.setLayoutManager(new LinearLayoutManager(a));
        list.setItemAnimator(null);
        final int current=selected;
        final int ink=a.getSharedPreferences("reader",0).getInt("fg",0xff30221e);
        list.setAdapter(new RecyclerView.Adapter<TocHolder>(){
            @Override public TocHolder onCreateViewHolder(ViewGroup parent,int type){
                LinearLayout row=new LinearLayout(a);row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(a,15),dp(a,11),dp(a,15),dp(a,11));
                RecyclerView.LayoutParams rowLp=new RecyclerView.LayoutParams(-1,-2);rowLp.bottomMargin=dp(a,5);row.setLayoutParams(rowLp);
                TextView name=label(a,"",16,ink);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);row.addView(name);
                TextView secondary=label(a,"",12,ink);secondary.setAlpha(.65f);row.addView(secondary);
                return new TocHolder(row,name,secondary);
            }
            @Override public void onBindViewHolder(TocHolder h,int i){TocEntry entry=choices.get(i);
                h.name.setText(entry.title);h.details.setText(entry.details);
                h.details.setVisibility(entry.details.isEmpty()?View.GONE:View.VISIBLE);
                h.row.setBackground(shape(a,i==current?0xffe7d5cf:Color.TRANSPARENT,14));
                h.name.setTextColor(i==current?0xffa53b4c:ink);
                h.row.setOnClickListener(v->{sh.dialog.dismiss();choice.onSelect(entry.word);});
            }
            @Override public int getItemCount(){return choices.size();}
        });
        LinearLayout.LayoutParams listLp=new LinearLayout.LayoutParams(-1,dp(a,(int)(a.getResources().getDisplayMetrics().heightPixels/a.getResources().getDisplayMetrics().density*.55f)));
        sh.content.addView(list,listLp);sh.show();
        if(!choices.isEmpty())list.scrollToPosition(Math.max(0,selected-2));
    }
    static final class TocHolder extends RecyclerView.ViewHolder {
        final LinearLayout row;final TextView name,details;
        TocHolder(LinearLayout row,TextView name,TextView details){super(row);this.row=row;this.name=name;this.details=details;}
    }
}
