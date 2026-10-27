package crestricted;

import crestricted.ast.Ast.Program;
import crestricted.codegen.JasminGenerator;
import crestricted.lexer.Lexer;
import crestricted.parser.Parser;

public final class CompilerPipeline {
    private CompilerPipeline() { }

    public static String compile(String source, String className) {
        if (className.isEmpty() || !Character.isJavaIdentifierStart(className.charAt(0))
                || !className.chars().allMatch(Character::isJavaIdentifierPart)) {
            throw new CompileError("invalid JVM class name '" + className + "'");
        }
        Lexer lexer = new Lexer(source);
        Parser parser = new Parser(lexer.scan());
        Program program = parser.parseProgram();
        return new JasminGenerator(program, className).generate();
    }
}
