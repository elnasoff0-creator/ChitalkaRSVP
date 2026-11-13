package ru.reader.rsvp;

import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;
import androidx.documentfile.provider.DocumentFile;

/** Resolve a persisted tree URI, including a document URI of its nested directory.
 * AndroidX DocumentFile.fromTreeUri alone returns the grant root, not its nested child.
 */
final class SafTreeDirectory {
    private SafTreeDirectory() { }

    static DocumentFile resolve(Context ctx,Uri uri) {
        if(uri==null||!"content".equalsIgnoreCase(uri.getScheme()))return null;
        try {
            DocumentFile root=DocumentFile.fromTreeUri(ctx,uri);
            if(root==null||!root.isDirectory())return null;
            String ancestor=DocumentsContract.getTreeDocumentId(uri);
            String target;
            try{target=DocumentsContract.getDocumentId(uri);}catch(Exception ignored){target=ancestor;}
            if(target==null||target.equals(ancestor))return root;
            return find(root,target,0);
        } catch(Exception ex) {
            return null;
        }
    }
    /** Reject copies into themselves: otherwise traversal can recursively copy its own output. */
    static boolean containsOrSame(Uri source,Uri target) {
        if(source==null||target==null||!source.getAuthority().equals(target.getAuthority()))return false;
        try{
            String s=docId(source),t=docId(target);
            return !s.isEmpty()&&(t.equals(s)||t.startsWith(s+"/")||t.startsWith(s+":"));
        }catch(Exception ignored){return source.equals(target);}
    }
    private static String docId(Uri uri){
        try{return DocumentsContract.getDocumentId(uri);}
        catch(Exception ignored){return DocumentsContract.getTreeDocumentId(uri);}
    }
    private static DocumentFile find(DocumentFile current,String id,int depth) {
        try {
            if(id.equals(DocumentsContract.getDocumentId(current.getUri())))return current;
            if(depth>=8)return null;
            for(DocumentFile sub:current.listFiles()) {
                if(!sub.isDirectory())continue;
                String child=DocumentsContract.getDocumentId(sub.getUri());
                if(id.equals(child))return sub;
                // For conventional tree providers, IDs retain their parent prefix.
                if(id.startsWith(child+"/")||id.startsWith(child+":")){
                    DocumentFile found=find(sub,id,depth+1);
                    if(found!=null)return found;
                }
            }
        } catch(Exception ignored) { }
        return null;
    }
}
