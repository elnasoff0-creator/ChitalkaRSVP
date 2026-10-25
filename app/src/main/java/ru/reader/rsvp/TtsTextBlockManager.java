package ru.reader.rsvp;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;

/** UI navigation uses the same prepared BookChunkIndex blocks as ReaderActivity.
 * Audio SentenceChunks remain independent and keep their 600-character limit. */
final class TtsTextBlockManager {
    interface Choose {void onChoose(BookChunkIndex.Chunk chunk);}
    interface ChooseChapter {void onChoose(int startChar);}
    private TtsTextBlockManager(){}
    static void show(Activity owner,BookChunkIndex chunks,BookChapterIndex chapters,String bookText,
                     int bg,int fg,int currentChar,ChooseChapter chapterHandler,Choose handler){
        if(chapters!=null && chapters.size()>0){
            showChapters(owner,chapters,bg,fg,currentChar,chapterHandler);
            return;
        }
        LinearLayout contents=new LinearLayout(owner);
        contents.setOrientation(LinearLayout.VERTICAL);
        final AlertDialog[] dialogHolder=new AlertDialog[1];
        RecyclerView rows=new RecyclerView(owner);
        contents.addView(rows,new LinearLayout.LayoutParams(-1,0,1));
        rows.setLayoutManager(new LinearLayoutManager(owner));
        rows.setPadding(dp(owner,12),dp(owner,4),dp(owner,12),dp(owner,10));
        rows.setClipToPadding(false);
        AlertDialog dialog=new SwipeDialogs.Builder(owner)
            .setTitle("Оглавление")
            .setView(contents)
            .create();
        dialogHolder[0]=dialog;
        rows.setAdapter(new RecyclerView.Adapter<Item>(){
            @Override public Item onCreateViewHolder(ViewGroup parent,int type){
                LinearLayout card=new LinearLayout(owner);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(owner,12),dp(owner,10),dp(owner,12),dp(owner,10));
                GradientDrawable back=new GradientDrawable();
                back.setColor(bg);back.setCornerRadius(dp(owner,13));
                back.setStroke(dp(owner,1),((fg&0x00ffffff)|0x33000000));
                card.setBackground(back);
                LinearLayout line=new LinearLayout(owner);line.setGravity(Gravity.CENTER_VERTICAL);
                card.addView(line,new LinearLayout.LayoutParams(-1,-2));
                TextView number=new TextView(owner);number.setTextSize(14);number.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
                number.setTextColor(fg);line.addView(number,new LinearLayout.LayoutParams(0,-2,1));
                TextView range=new TextView(owner);range.setTextSize(11);
                range.setTextColor(fg);range.setAlpha(0.65f);
                line.addView(range,new LinearLayout.LayoutParams(-2,-2));
                TextView sample=new TextView(owner);sample.setTextSize(13);sample.setTextColor(fg);
                sample.setSingleLine(true);sample.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams st=new LinearLayout.LayoutParams(-1,-2);st.topMargin=dp(owner,4);
                card.addView(sample,st);
                RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(-1,-2);
                lp.bottomMargin=dp(owner,8);card.setLayoutParams(lp);
                return new Item(card,number,range,sample);
            }
            @Override public void onBindViewHolder(Item item,int index){
                BookChunkIndex.Chunk part=chunks.get(index);
                item.number.setText((index+1)+".");
                item.range.setText(part.startChar+"–"+Math.max(part.startChar,part.endChar-1)+" симв.");
                int snippetEnd=Math.min(part.endChar,part.startChar+240);
                item.sample.setText(bookText.substring(part.startChar,snippetEnd).replace('\n',' ').replaceAll("\\s+"," " ).trim());
                item.itemView.setOnClickListener(v->{dialog.dismiss();handler.onChoose(part);});
            }
            @Override public int getItemCount(){return chunks.size();}
        });
        dialog.show();
        android.view.Window window=dialog.getWindow();
        if(window!=null){
            android.util.DisplayMetrics dm=owner.getResources().getDisplayMetrics();
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(-1,Math.min(dm.heightPixels-dp(owner,70),dp(owner,720)));
            View panel=window.getDecorView();panel.setTranslationY(dp(owner,80));
            panel.animate().translationY(0f).setDuration(230).start();
        }
    }
    /** Chapter list is independent from UI BookChunkIndex and audio SentenceChunks.
     * RecyclerView keeps the navigation cheap even for hundreds of sections. */
    private static void showChapters(Activity owner,BookChapterIndex chapters,
            int bg,int fg,int currentChar,ChooseChapter callback){
        RecyclerView list=new RecyclerView(owner);
        list.setLayoutManager(new LinearLayoutManager(owner));
        list.setPadding(dp(owner,12),dp(owner,6),dp(owner,12),dp(owner,8));
        AlertDialog dialog=new SwipeDialogs.Builder(owner)
            .setTitle("Оглавление").setView(list).create();
        // Reuse the prepared global char offset, never rebuild a full-book word index.
        BookChapterIndex.Chapter active=chapters.atChar(currentChar);
        list.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>(){
            @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent,int viewType){
                TextView row=new TextView(owner);
                row.setTextColor(fg);row.setTextSize(15);
                row.setPadding(dp(owner,14),dp(owner,12),dp(owner,14),dp(owner,12));
                GradientDrawable back=new GradientDrawable();
                back.setColor(bg);back.setCornerRadius(dp(owner,12));
                back.setStroke(dp(owner,1),((fg&0x00ffffff)|0x33000000));
                row.setBackground(back);
                RecyclerView.LayoutParams params=new RecyclerView.LayoutParams(-1,-2);
                params.bottomMargin=dp(owner,6);row.setLayoutParams(params);
                return new RecyclerView.ViewHolder(row){};
            }
            @Override public void onBindViewHolder(RecyclerView.ViewHolder holder,int position){
                BookChapterIndex.Chapter chapter=chapters.get(position);
                TextView row=(TextView)holder.itemView;
                row.setSingleLine(true);row.setEllipsize(TextUtils.TruncateAt.END);
                row.setText(chapter.title);
                boolean selected=active!=null&&chapter.startChar==active.startChar;
                row.setTextColor(selected?0xffa53b4c:fg);
                row.setBackground(selected?bg(owner,0xffead7d1,12):bg(owner,bg,12));
                row.setOnClickListener(v->{dialog.dismiss();callback.onChoose(chapter.startChar);});
            }
            @Override public int getItemCount(){return chapters.size();}
        });
        dialog.show();
        android.view.Window window=dialog.getWindow();
        if(window!=null){
            android.util.DisplayMetrics dm=owner.getResources().getDisplayMetrics();
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(-1,Math.min(dm.heightPixels-dp(owner,70),dp(owner,720)));
            View panel=window.getDecorView();panel.setTranslationY(dp(owner,80));
            panel.animate().translationY(0f).setDuration(230).start();
        }
    }
    private static GradientDrawable bg(Activity a,int fill,int radius){
        GradientDrawable drawable=new GradientDrawable();drawable.setColor(fill);
        drawable.setCornerRadius(dp(a,radius));return drawable;
    }
    private static int dp(Activity a,int d){return Math.round(d*a.getResources().getDisplayMetrics().density);}
    private static final class Item extends RecyclerView.ViewHolder {
        final TextView number,range,sample;
        Item(View view,TextView number,TextView range,TextView sample){
            super(view);this.number=number;this.range=range;this.sample=sample;
        }
    }
}
