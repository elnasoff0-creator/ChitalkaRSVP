import com.sun.source.util.JavacTask;
import javax.tools.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
/** Syntax-only parser; deliberately does not typecheck Android references. */
public final class JavaParseVerifier {
  public static void main(String[] args) throws Exception {
    JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
    if (compiler==null) throw new IllegalStateException("JDK required");
    List<Path> paths;
    try (Stream<Path> files=Files.walk(Path.of(args[0]))) {
      paths=files.filter(f->f.toString().endsWith(".java")).sorted().collect(Collectors.toList());
    }
    DiagnosticCollector<JavaFileObject> issues=new DiagnosticCollector<>();
    try (StandardJavaFileManager manager=compiler.getStandardFileManager(issues,null,null)) {
      Iterable<? extends JavaFileObject> sources=manager.getJavaFileObjectsFromPaths(paths);
      JavacTask task=(JavacTask)compiler.getTask(null,manager,issues,List.of("-proc:none","--release","17"),null,sources);
      task.parse();
      long errors=issues.getDiagnostics().stream().filter(d->d.getKind()==Diagnostic.Kind.ERROR).count();
      issues.getDiagnostics().stream().filter(d->d.getKind()==Diagnostic.Kind.ERROR).limit(15).forEach(System.err::println);
      if(errors>0)throw new IllegalStateException("Java parse errors="+errors);
      System.out.println("PASS "+paths.size()+" Java sources parsed without syntax errors");
    }
  }
}
