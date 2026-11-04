package ru.reader.rsvp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import com.googlecode.tesseract.android.TessBaseAPI;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import android.text.Html;

/** Offline Russian+English OCR; HOCR paragraph bounding boxes become tappable dialogue regions. */
final class ComicOcrEngine {
    private static final Pattern PAR=Pattern.compile("(?is)<p\\b([^>]*)>(.*?)</p>");
    private static final Pattern TITLE=Pattern.compile("bbox\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)");
    private static void install(Context context,String name)throws Exception{
        File dest=new File(context.getFilesDir(),"tessdata/"+name+".traineddata");
        if(dest.isFile()&&dest.length()>100000)return;
        File folder=dest.getParentFile();if(!folder.exists()&&!folder.mkdirs())throw new Exception("Невозможно создать папку OCR");
        try(InputStream in=context.getAssets().open("tessdata/"+name+".traineddata");FileOutputStream out=new FileOutputStream(dest)){
            byte[] data=new byte[16384];int n;while((n=in.read(data))!=-1)out.write(data,0,n);
        }
    }
    static JSONObject recognize(Context context,InputStream input)throws Exception{
        install(context,"rus");install(context,"eng");
        byte[] bytes=readImage(input);
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
        if(options.outWidth<=0||options.outHeight<=0)throw new Exception("Не удаётся открыть изображение");
        int sample=1;while(Math.max(options.outWidth/sample,options.outHeight/sample)>2600)sample*=2;
        options.inJustDecodeBounds=false;options.inSampleSize=sample;
        Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
        if(bitmap==null)throw new Exception("Изображение не декодировано");
        TessBaseAPI api=new TessBaseAPI();
        try{
            if(!api.init(context.getFilesDir().getAbsolutePath(),"rus+eng"))throw new Exception("Не удалось запустить OCR");
            api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO);
            api.setImage(bitmap);
            String hocr=api.getHOCRText(0);
            JSONArray blocks=new JSONArray();
            Matcher matches=PAR.matcher(hocr==null?"":hocr);
            while(matches.find()){
                Matcher rect=TITLE.matcher(matches.group(1));if(!rect.find())continue;
                String text=Html.fromHtml(matches.group(2).replaceAll("(?i)</span>","</span> "),Html.FROM_HTML_MODE_LEGACY).toString()
                    .replaceAll("\\s+"," ").trim();
                if(text.length()<2)continue;
                float x1=Float.parseFloat(rect.group(1))/bitmap.getWidth();
                float y1=Float.parseFloat(rect.group(2))/bitmap.getHeight();
                float x2=Float.parseFloat(rect.group(3))/bitmap.getWidth();
                float y2=Float.parseFloat(rect.group(4))/bitmap.getHeight();
                if(x2<=x1||y2<=y1)continue;
                blocks.put(ComicOcrStore.block(text,clamp(x1),clamp(y1),clamp(x2),clamp(y2)));
            }
            JSONObject result=new JSONObject();result.put("version",1);result.put("width",options.outWidth);
            result.put("height",options.outHeight);result.put("blocks",blocks);return result;
        }finally{api.recycle();bitmap.recycle();}
    }
    private static float clamp(float f){return Math.max(0,Math.min(1,f));}
    private static byte[] readImage(InputStream in)throws Exception{
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[16384];int n;
        while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>32*1024*1024)throw new Exception("Изображение больше 32 МБ");}
        return out.toByteArray();
    }
}
