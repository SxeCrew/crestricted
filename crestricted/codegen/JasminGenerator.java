package crestricted.codegen;

import crestricted.CompileError;
import crestricted.ast.Ast.*;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JasminGenerator {
    private final Program program;
    private final String className;
    private final Map<String, Function> functions = new LinkedHashMap<>();
    private final StringBuilder output = new StringBuilder();

    public JasminGenerator(Program program, String className) {
        this.program = program;
        this.className = className;
    }

    public String generate() {
        for (Function f : program.functions()) {
            if (functions.putIfAbsent(f.name(), f) != null) throw new CompileError("duplicate function '" + f.name() + "'");
            if (f.params().size() > 255) throw new CompileError("function '" + f.name() + "' exceeds the JVM limit of 255 int parameters");
        }
        Function main = functions.get("main");
        if (main == null) throw new CompileError("a program requires exactly one main function");
        if (!main.params().isEmpty()) throw new CompileError("main must have no parameters");
        output.append(".bytecode 49.0\n.class public ").append(className).append("\n.super java/lang/Object\n\n");
        output.append(".field private static _scanner Ljava/util/Scanner;\n\n");
        scannerInitializer();
        for (Function function : program.functions()) generateFunction(function);
        output.append(".method public static main([Ljava/lang/String;)V\n")
                .append("  .limit stack 1\n  .limit locals 1\n")
                .append("  invokestatic ").append(className).append("/main()I\n")
                .append("  pop\n  return\n.end method\n");
        return output.toString();
    }

    private void scannerInitializer() {
        output.append(".method static <clinit>()V\n")
                .append("  .limit stack 3\n  .limit locals 0\n")
                .append("  new java/util/Scanner\n  dup\n")
                .append("  getstatic java/lang/System/in Ljava/io/InputStream;\n")
                .append("  invokespecial java/util/Scanner/<init>(Ljava/io/InputStream;)V\n")
                .append("  putstatic ").append(className).append("/_scanner Ljava/util/Scanner;\n")
                .append("  return\n.end method\n\n");
    }

    private void generateFunction(Function function) {
        output.append(new MethodGenerator(function, className, functions).generate());
    }
}
