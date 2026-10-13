package android.content;
import android.net.Uri;import android.database.Cursor;import java.io.InputStream;
public class ContentResolver {
 public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){return null;}
 public InputStream openInputStream(Uri uri){return new java.io.ByteArrayInputStream(new byte[]{1,2,3});}
}
