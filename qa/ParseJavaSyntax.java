import com.sun.source.util.JavacTask;
import javax.tools.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** AST-only check without Android SDK; parses source but does NOT typecheck or build APK. */
public class ParseJavaSyntax {
    public static void main(String[] args)throws Exception{
        Path dir=Paths.get(args.length==0?"app/src/main/java":args[0]);
        List<File> sources=new ArrayList<>();
        try(java.util.stream.Stream<Path> paths=Files.walk(dir)){
            paths.filter(p->p.toString().endsWith(".java")).forEach(p->sources.add(p.toFile()));
        }
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
        try(StandardJavaFileManager manager=compiler.getStandardFileManager(diagnostics,Locale.ROOT,null)){
            JavacTask task=(JavacTask)compiler.getTask(null,manager,diagnostics,
                Arrays.asList("-proc:none","-source","17"),null,manager.getJavaFileObjectsFromFiles(sources));
            task.parse();
        }
        int errors=0;for(Diagnostic<?> d:diagnostics.getDiagnostics()){
            if(d.getKind()==Diagnostic.Kind.ERROR){System.err.println(d);errors++;}
        }
        System.out.println("Parsed "+sources.size()+" Java sources; syntax errors="+errors);
        if(errors>0)System.exit(1);
    }
}
