import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.tools.*;
import java.io.File;
import java.nio.file.Path;
import java.util.*;

/** Catch missing ReaderActivity handlers even without Android SDK or Gradle.
 *  This is a structural smoke test, NOT a substitute for Android javac.
 */
public final class ReaderActivityMethodCheck {
    private static final Set<String> INHERITED = Set.of(
        "bindService", "findViewById", "getContentResolver", "getHeight", "getIntent",
        "getResources", "getSharedPreferences", "getWidth", "postInvalidateDelayed",
        "runOnUiThread", "setClickable", "setContentView", "setFocusable",
        "setImportantForAccessibility", "startActivity", "startActivityForResult",
        "startService", "super", "unbindService");
    private static final Set<String> REQUIRED = Set.of(
        "removeCountdownRing", "showCountdownRing", "setRsvpContextVisible",
        "stepRsvpByScroll", "updateSelectWordButton", "updateProgress",
        "formatReadingTime", "updatePlayButton", "togglePlayback", "stop");

    public static void main(String[] args) throws Exception {
        Path p = Path.of(args.length > 0 ? args[0] :
                "app/src/main/java/ru/reader/rsvp/ReaderActivity.java");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("Java compiler required");
        try (StandardJavaFileManager files = compiler.getStandardFileManager(null, null, null)) {
            JavacTask task = (JavacTask) compiler.getTask(null, files, null,
                List.of("-proc:none", "-source", "17"), null,
                files.getJavaFileObjects(new File(p.toString())));
            Set<String> declared = new HashSet<>();
            Set<String> invoked = new HashSet<>();
            for (CompilationUnitTree unit : task.parse()) {
                new TreeScanner<Void, Void>() {
                    @Override public Void visitMethod(MethodTree node, Void ignored) {
                        declared.add(node.getName().toString());
                        return super.visitMethod(node, ignored);
                    }
                    @Override public Void visitMethodInvocation(MethodInvocationTree node, Void ignored) {
                        ExpressionTree method = node.getMethodSelect();
                        if (method instanceof IdentifierTree) {
                            invoked.add(((IdentifierTree) method).getName().toString());
                        } else if (method instanceof MemberSelectTree) {
                            MemberSelectTree member = (MemberSelectTree) method;
                            if (member.getExpression().toString().equals("ReaderActivity.this"))
                                invoked.add(member.getIdentifier().toString());
                        }
                        return super.visitMethodInvocation(node, ignored);
                    }
                }.scan(unit, null);
            }
            Set<String> missing = new TreeSet<>(REQUIRED);
            missing.removeAll(declared);
            if (!missing.isEmpty()) throw new AssertionError("Removed required handlers: " + missing);
            invoked.removeAll(declared);
            invoked.removeAll(INHERITED);
            if (!invoked.isEmpty()) throw new AssertionError("Unresolved local method calls: " + invoked);
            System.out.println("PASS: ReaderActivity handlers exist and all unqualified calls resolve structurally");
        }
    }
}
