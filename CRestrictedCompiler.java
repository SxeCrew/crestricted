import crestricted.CompileError;
import crestricted.CompilerPipeline;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CRestrictedCompiler {
    public static void main(String[] args) {
        if (args.length < 1 || args.length > 3) {
            System.err.println("Usage: java CRestrictedCompiler <source.c> [output.j] [ClassName]");
            System.exit(2);
        }
        try {
            Path source = Path.of(args[0]);
            String base = stripExtension(source.getFileName().toString());
            Path output = args.length >= 2 ? Path.of(args[1]) : source.resolveSibling(base + ".j");
            String className = args.length >= 3 ? args[2] : javaName(base);
            String jasmin = compile(Files.readString(source, StandardCharsets.UTF_8), className);
            Files.writeString(output, jasmin, StandardCharsets.UTF_8);
            System.out.println("Created " + output.toAbsolutePath());
        } catch (CompileError | IOException e) {
            System.err.println("Compilation failed: " + e.getMessage());
            System.exit(1);
        }
    }

    static String compile(String source, String className) {
        return CompilerPipeline.compile(source, className);
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String javaName(String value) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            result.append(Character.isJavaIdentifierPart(ch) ? ch : '_');
        }
        if (result.isEmpty() || !Character.isJavaIdentifierStart(result.charAt(0))) result.insert(0, '_');
        return result.toString();
    }
}
