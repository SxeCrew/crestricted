package crestricted.lexer;

public record Token(Kind kind, String text, int line, int column) {
    public enum Kind { ID, NUMBER, STRING, SYMBOL, EOF }
}
