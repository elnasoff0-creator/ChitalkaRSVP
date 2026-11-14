import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import javax.tools.*;
import com.sun.source.util.JavacTask;

public class ParseSources162 {
    public static void main(String[] args) throws Exception {
        Path dir=Paths.get(args[0]);
        List<File> javaFiles;
        try(Stream<Path> walk=Files.walk(dir)){
            javaFiles=walk.filter(p->p.toString().endsWith(".java")).map(Path::toFile).collect(Collectors.toList());
        }
        DiagnosticCollector<JavaFileObject> errors=new DiagnosticCollector<>();
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        try(StandardJavaFileManager fm=compiler.getStandardFileManager(errors,null,null)) {
            JavacTask task=(JavacTask)compiler.getTask(null,fm,errors,
                List.of("--release","17","-proc:none","-Xlint:none"),null,fm.getJavaFileObjectsFromFiles(javaFiles));
            task.parse();
        }
        int count=0;
        for(Diagnostic<? extends JavaFileObject> diag:errors.getDiagnostics()) {
            if(diag.getKind()==Diagnostic.Kind.ERROR){count++;System.out.println(diag);}
        }
        System.out.println("PARSED="+javaFiles.size()+" SYNTAX_ERRORS="+count);
        if(count>0)System.exit(1);
    }
}
