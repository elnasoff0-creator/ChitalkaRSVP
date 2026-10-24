import com.sun.source.util.JavacTask;
import javax.tools.*;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/** Parse-only Java QA: catches invalid syntax without needing Android SDK jars. */
public final class JavaSourceSyntaxCheck {
    public static void main(String[] args) throws Exception {
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        if(compiler==null)throw new IllegalStateException("JDK compiler is not installed");
        try(Stream<Path> paths=Files.walk(Paths.get("app/src/main/java"))){
            List<File> source=paths.filter(p->p.toString().endsWith(".java"))
                    .map(Path::toFile).collect(Collectors.toList());
            DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
            try(StandardJavaFileManager manager=compiler.getStandardFileManager(diagnostics,null,null)){
                JavacTask task=(JavacTask)compiler.getTask(null,manager,diagnostics,
                    Arrays.asList("-proc:none","-source","17"),null,manager.getJavaFileObjectsFromFiles(source));
                task.parse();
            }
            long errors=diagnostics.getDiagnostics().stream()
                    .filter(d->d.getKind()==Diagnostic.Kind.ERROR).count();
            if(errors>0){
                diagnostics.getDiagnostics().stream().filter(d->d.getKind()==Diagnostic.Kind.ERROR)
                        .forEach(System.err::println);
                throw new IllegalStateException(errors+" Java syntax errors");
            }
            System.out.println("PASS: parsed "+source.size()+" Java files (syntax only, no Android type checks)");
        }
    }
}
