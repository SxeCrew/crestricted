package crestricted;

public final class CompileError extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public CompileError(String message) {
        super(message);
    }
}
