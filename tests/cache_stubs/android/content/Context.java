package android.content;
import java.io.File;
public class Context {
  final File root;
  public Context(File root) {this.root=root;}
  public File getExternalFilesDir(String directory){File out=new File(root,"ext/"+directory);out.mkdirs();return out;}
  public File getFilesDir(){return root;}
  public File getCacheDir(){File dir=new File(root,"cache");dir.mkdirs();return dir;}
  public ContentResolver getContentResolver(){return new ContentResolver();}
}
