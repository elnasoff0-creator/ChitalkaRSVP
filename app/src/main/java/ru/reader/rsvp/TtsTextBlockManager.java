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
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;

/** Existing SentenceChunks, NOT a second segmentation scheme. */
final class TtsTextBlockManager {
    interface Choose {void onChoose(SentenceChunks.Chunk chunk);}
    private TtsTextBlockManager(){}
    static void show(Activity owner,ArrayList<SentenceChunks.Chunk> chunks,
                     int bg,int fg,Choose handler){
        int density=Math.round(owner.getResources().getDisplayMetrics().density);
        RecyclerView rows=new RecyclerView(owner);
        rows.setLayoutManager(new LinearLayoutManager(owner));
        rows.setPadding(dp(owner,12),dp(owner,4),dp(owner,12),dp(owner,10));
        rows.setClipToPadding(false);
        AlertDialog dialog=new SwipeDialogs.Builder(owner)
            .setTitle("Менеджер текстовых блоков")
            .setView(rows)
            .create();
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
                SentenceChunks.Chunk part=chunks.get(index);
                item.number.setText((index+1)+".");
                item.range.setText(part.fromChar+"–"+Math.max(part.fromChar,part.toChar-1)+" симв.");
                item.sample.setText(part.text.replace('\n',' ').replaceAll("\\s+"," ").trim());
                item.itemView.setOnClickListener(v->{dialog.dismiss();handler.onChoose(part);});
            }
            @Override public int getItemCount(){return chunks.size();}
        });
        dialog.show();
        android.view.Window window=dialog.getWindow();
        if(window!=null){
            android.util.DisplayMetrics dm=owner.getResources().getDisplayMetrics();
            window.setLayout(Math.min(dm.widthPixels-dp(owner,24),dp(owner,480)),
                Math.min(dm.heightPixels-dp(owner,90),dp(owner,660)));
        }
    }
    private static int dp(Activity a,int d){return Math.round(d*a.getResources().getDisplayMetrics().density);}
    private static final class Item extends RecyclerView.ViewHolder {
        final TextView number,range,sample;
        Item(View view,TextView number,TextView range,TextView sample){
            super(view);this.number=number;this.range=range;this.sample=sample;
        }
    }
}
