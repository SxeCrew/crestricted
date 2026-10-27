import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class CompilerTests {
    private static final Path ROOT = Path.of("").toAbsolutePath().normalize();
    private static final boolean WINDOWS = System.getProperty("os.name").startsWith("Windows");
    private static final String JAVA = Path.of(System.getProperty("java.home"), "bin", WINDOWS ? "java.exe" : "java").toString();
    private static final Pattern META_IO = Pattern.compile("\\bin:\\s*(\\[[^\\]\\r\\n]*])(?s:.*?)\\bout:\\s*(\\[[^\\]\\r\\n]*])");
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]*)\"");
    private final Path work;
    private final Path jasmin;
    private final Path clang;
    private int checks, programs, goldenCases, nativeCases, rejected, failed;
    private final StringBuilder log = new StringBuilder();

    private record Example(String input, String output) { }
    private record Result(int exit, String out, String err) { }
    private record Generated(String source, int value) { }

    private CompilerTests() throws IOException {
        Files.createDirectories(ROOT.resolve("build"));
        work = Files.createTempDirectory(ROOT.resolve("build"), "tests-");
        jasmin = ROOT.resolve("jasmin.jar");
        clang = findExecutable("clang");
        if (!Files.isRegularFile(jasmin)) throw new IOException("jasmin.jar is required in " + ROOT);
    }

    public static void main(String[] args) throws Exception {
        CompilerTests tests = new CompilerTests();
        tests.core();
        Path golden = args.length == 0 ? ROOT.resolve("tests/golden") : Path.of(args[0]);
        if (Files.isDirectory(golden)) tests.golden(golden);
        else tests.note("Optional golden directory is absent; standalone regression suite ran.");
        String summary = "Checks: " + tests.checks + ", failed: " + tests.failed
                + ", assembled programs: " + tests.programs + ", rejected invalid programs: " + tests.rejected
                + ", original golden scenarios: " + tests.goldenCases + ", native/JVM comparisons: " + tests.nativeCases;
        tests.note(summary);
        tests.note("Artifacts: " + tests.work);
        Files.writeString(tests.work.resolve("results.txt"), tests.log, StandardCharsets.UTF_8);
        if (tests.failed != 0) System.exit(1);
    }

    private void core() throws Exception {
        valid("MultipleScan", "int main(){int a;int b;scanf(\"%d\", &a);scanf(\"%d\", &b);printf(\"%d\",a+b);return 0;}",
                new Example("7\n3\n", "10"), new Example(" +17  -7 ", "10"), new Example("-2147483648 2147483647", "-1"));
        valid("SharedScannerAcrossCalls", "int read(){int x;scanf(\"%d\",&x);return x;} int main(){int a=read();int b=read();printf(\"%d\",a+b+read());return 0;}",
                new Example("1 2 3", "6"), new Example("-1\n0\n9", "8"));
        valid("PrintFormats", "int main(){printf(\"%d\",1);printf(\"%d\\n\",2);printf(\"%d\",3);return 0;}", new Example("", "12\n3"));
        valid("Precedence", prints("2+3*4", "(2+3)*4", "10-3-2", "10-(3-2)", "-(-7)", "-2*3+10", "2- -3", "1<2==3<4", "2==1<2", "1<2<1", "1==1==1"),
                new Example("", "14\n20\n5\n9\n7\n4\n5\n1\n0\n0\n1\n"));
        valid("AllComparisons", prints("2==2", "2>2", "2<2", "2>=2", "2<=2", "-2147483648<2147483647", "2147483647>-2147483648", "(1<2)+(3>2)"),
                new Example("", "1\n0\n0\n1\n1\n1\n1\n2\n"));
        int[] constants = {Integer.MIN_VALUE, -32769, -32768, -129, -128, -1, 0, 5, 6, 127, 128, 32767, 32768, Integer.MAX_VALUE};
        valid("IntegerBoundaries", prints(Arrays.stream(constants).mapToObj(Integer::toString).toArray(String[]::new)),
                new Example("", Arrays.stream(constants).mapToObj(n -> n + "\n").collect(Collectors.joining())));
        valid("IfAndWhile", "int main(){int n=3;int sum=0;while(n>0){int j=2;while(j>0){sum=sum+n;j=j-1;}n=n-1;}if(-7) sum=sum+1; if(0) sum=99; printf(\"%d\",sum);return 0;}", new Example("", "13"));
        valid("DanglingElse", "int main(){int x=0;if(1) if(0) x=1;else x=2;printf(\"%d\",x);return 0;}", new Example("", "2"));
        valid("DefiniteAssignment", "int main(){int n;scanf(\"%d\",&n);int x;if(n>0){x=7;}else{x=9;}printf(\"%d\",x);return 0;}", new Example("1", "7"), new Example("0", "9"));
        valid("BlockShadowing", "int f(int x){int r=x;{int x=8;r=r+x;}return r;}int main(){int x=3;{int x=4;printf(\"%d\",x);}printf(\"%d\",f(x));return 0;}", new Example("", "411"));
        valid("ForwardCall", "int main(){printf(\"%d\",later(7));return 0;}int later(int x){return x*2;}", new Example("", "14"));
        valid("RecursiveFactorial", "int factorial(int n){int r;if(n<=1){r=1;}else{r=n*factorial(n-1);}return r;}int main(){int n;scanf(\"%d\",&n);printf(\"%d\",factorial(n));return 0;}",
                new Example("0", "1"), new Example("5", "120"), new Example("12", "479001600"));
        valid("MutualRecursion", "int even(int n){int r;if(n==0){r=1;}else{r=odd(n-1);}return r;}int odd(int n){int r;if(n==0){r=0;}else{r=even(n-1);}return r;}int main(){printf(\"%d\",even(8));printf(\"%d\",odd(9));return 0;}", new Example("", "11"));
        valid("MainAsIntFunction", "int forward(){return main();}int main(){int n;scanf(\"%d\",&n);int r=42;if(n>0){r=forward();}printf(\"%d\\n\",r);return 0;}", new Example("1 0", "42\n0\n"));
        valid("AssemblerNames", "int iadd(int x){return x;}int _scanner(int x){return iadd(x);}int main(){printf(\"%d\",_scanner(42));return 0;}", new Example("", "42"));
        valid("CommentsAndBom", "\uFEFF#include <stdio.h>\n/* block */int main(){; // a comment\n printf(\"%d\",0008);return 0;} // end", new Example("", "8"));
        String deep = "1";
        for (int i = 0; i < 100; i++) deep = "1+(" + deep + ")";
        valid("DeepStack", prints(deep), new Example("", "101\n"));
        List<String> params = new ArrayList<>(), arguments = new ArrayList<>();
        for (int i = 0; i < 80; i++) { params.add("int p" + i); arguments.add(i == 79 ? deep : "1"); }
        valid("PendingArgumentsStack", "int f(" + String.join(",", params) + "){return p0+p79;}" + prints("f(" + String.join(",", arguments) + ")"), new Example("", "102\n"));
        List<String> boundaryParams = new ArrayList<>(), boundaryArguments = new ArrayList<>();
        for (int i = 0; i < 255; i++) { boundaryParams.add("int p" + i); boundaryArguments.add(Integer.toString(i)); }
        valid("ParameterBoundary", "int f(" + String.join(",", boundaryParams) + "){return p254;}" + prints("f(" + String.join(",", boundaryArguments) + ")"), new Example("", "254\n"));
        StringBuilder wide = new StringBuilder("int main(){");
        for (int i = 0; i < 300; i++) wide.append("int x").append(i).append('=').append(i).append(';');
        valid("WideLocals", wide + "x299=x299+1;printf(\"%d\",x299);return 0;}", new Example("", "300"));
        Random random = new Random(20261027L);
        List<String> expressions = new ArrayList<>();
        StringBuilder expected = new StringBuilder();
        for (int i = 0; i < 150; i++) { Generated expr = generated(random, 4); expressions.add(expr.source); expected.append(expr.value).append('\n'); }
        valid("GeneratedExpressions", prints(expressions.toArray(String[]::new)), new Example("", expected.toString()));
        Path demo = ROOT.resolve("Crestricted.c");
        if (Files.isRegularFile(demo)) {
            valid("OriginalDemo", Files.readString(demo), new Example("3", "1874161"));
        } else {
            valid("IncludedDemo", Files.readString(ROOT.resolve("lab-examples/AllFeatures.c")), new Example("-3 4", "49\n1\n"));
        }

        invalid("NoMain", "int f(){return 0;}", "main");
        invalid("MainParameters", "int main(int x){return x;}", "no parameters");
        invalid("DuplicateMain", "int main(){return 0;}int main(){return 0;}", "duplicate function");
        invalid("DuplicateFunction", "int f(){return 0;}int f(){return 1;}int main(){return 0;}", "duplicate function");
        invalid("DuplicateParameter", "int f(int x,int x){return x;}int main(){return 0;}", "duplicate local");
        invalid("DuplicateLocal", "int main(){int x=0;int x=1;return 0;}", "duplicate local");
        invalid("ParameterRedeclared", "int f(int x){int x=1;return x;}int main(){return 0;}", "duplicate local");
        invalid("ConditionalReturn", "int main(){if(1){return 0;}}", "must end with return");
        invalid("EmptyFunction", "int main(){}", "must end with return");
        invalid("MissingReturn", "int f(){int x=1;}int main(){return 0;}", "must end with return");
        invalid("EarlyReturn", "int main(){return 0;return 0;}", "only as the final statement");
        invalid("NestedReturn", "int main(){if(1){return 1;}return 0;}", "only as the final statement");
        invalid("LoopReturn", "int main(){while(1){return 1;}return 0;}", "only as the final statement");
        invalid("Uninitialized", "int main(){int x;printf(\"%d\",x);return 0;}", "uninitialized");
        invalid("SelfInitializer", "int main(){int x=x;return 0;}", "uninitialized");
        invalid("OneBranchInitialized", "int main(){int x;if(1){x=2;}printf(\"%d\",x);return 0;}", "uninitialized");
        invalid("LoopOnlyInitialized", "int main(){int x;while(0){x=2;}printf(\"%d\",x);return 0;}", "uninitialized");
        invalid("BranchAssignmentLeak", "int main(){int x;if(1){x=2;}else{printf(\"%d\",x);}return 0;}", "uninitialized");
        invalid("BranchScope", "int main(){if(1){int x=1;}else{printf(\"%d\",x);}return 0;}", "unknown variable");
        invalid("LoopScope", "int main(){while(0){int x=1;}printf(\"%d\",x);return 0;}", "unknown variable");
        invalid("BeforeDeclaration", "int main(){x=1;int x;return 0;}", "unknown variable");
        invalid("UnknownFunction", "int main(){return missing();}", "unknown function");
        invalid("TooFewArguments", "int f(int x){return x;}int main(){return f();}", "expects 1");
        invalid("TooManyArguments", "int f(int x){return x;}int main(){return f(1,2);}", "expects 1");
        invalid("ShadowedFunction", "int f(){return 1;}int main(){int f=2;return f();}", "not a function");
        invalid("UnknownScanVariable", "int main(){scanf(\"%d\",&x);return 0;}", "unknown variable");
        invalid("ScanWithoutAddress", "int main(){int x;scanf(\"%d\",x);return 0;}", "expected '&'");
        invalid("BadPrintFormat", "int main(){printf(\"%s\",1);return 0;}", "printf supports only");
        invalid("BadScanFormat", "int main(){int x;scanf(\"%d\\n\",&x);return 0;}", "scanf supports only");
        invalid("BadStringEscape", "int main(){printf(\"%d\\t\",1);return 0;}", "unsupported string escape");
        invalid("Pointer", "int main(){int *x;return 0;}", "expected identifier");
        invalid("Division", "int main(){return 4/2;}", "unexpected character");
        invalid("FloatType", "float main(){return 0;}", "expected 'int'");
        invalid("PositiveOverflow", "int main(){return 2147483648;}", "int range");
        invalid("NegativeOverflow", "int main(){return -2147483649;}", "int range");
        invalid("HugeInteger", "int main(){return -999999999999999999999999999999;}", "int range");
        invalid("KeywordIdentifier", "int main(){int if=2;return 0;}", "reserved name");
        invalid("BuiltinFunctionName", "int printf(){return 0;}int main(){return 0;}", "reserved name");
        invalid("BareDeclaration", "int main(){if(1) int x=2;return 0;}", "requires a braced block");
        invalid("Increment", "int main(){int x=1;return ++x;}", "expected identifier");
        invalid("Decrement", "int main(){int x=1;return --x;}", "expected identifier");
        invalid("WrongInclude", "#include <other.h>\nint main(){return 0;}", "expected 'stdio'");
        invalid("DuplicateInclude", "#include <stdio.h>\n#include <stdio.h>\nint main(){return 0;}", "expected 'int'");
        invalid("UnterminatedComment", "int main(){return 0;}/*", "unterminated block comment");
        invalid("MissingBrace", "int main(){return 0;", "expected '}'");
        List<String> tooManyParams = new ArrayList<>();
        for (int i = 0; i < 256; i++) tooManyParams.add("int p" + i);
        invalid("ParameterLimit", "int f(" + String.join(",", tooManyParams) + "){return 0;}int main(){return 0;}", "255 int parameters");
        try {
            CRestrictedCompiler.compile("int main(){return 0;}", "Bad/Name");
            check("InvalidClassName", false, "accepted invalid class name");
        } catch (IllegalArgumentException e) { throw e; }
        catch (RuntimeException e) { check("InvalidClassName", e.getMessage().contains("invalid JVM class name"), e.toString()); }
        cli();
    }

    private void golden(Path directory) throws Exception {
        List<Path> files;
        try (var paths = Files.walk(directory)) { files = paths.filter(p -> p.toString().endsWith(".c")).sorted().toList(); }
        for (Path source : files) {
            String text = Files.readString(source);
            if (text.contains("//T illness:")) continue;
            List<Example> examples = new ArrayList<>();
            Matcher matcher = META_IO.matcher(text);
            while (matcher.find()) examples.add(new Example(String.join("\n", strings(matcher.group(1))) + "\n", String.join("\n", strings(matcher.group(2))) + "\n"));
            if (examples.isEmpty()) throw new IllegalStateException("missing IO metadata in " + source);
            String name = "Golden" + programs;
            runValid(name, text, examples, true);
            goldenCases += examples.size();
        }
    }

    private void valid(String name, String source, Example... examples) throws Exception {
        runValid(name, source, List.of(examples), false);
    }

    private void runValid(String name, String source, List<Example> examples, boolean differential) throws Exception {
        Path directory = Files.createDirectory(work.resolve(name));
        Path asm = directory.resolve(name + ".j");
        String assembly;
        try { assembly = CRestrictedCompiler.compile(source, name); }
        catch (RuntimeException e) { check(name, false, "compile: " + e); return; }
        Files.writeString(asm, assembly, StandardCharsets.UTF_8);
        Result assembled = run(List.of(JAVA, "-jar", jasmin.toString(), "-d", directory.toString(), asm.toString()), directory, "");
        if (assembled.exit != 0 || !assembled.err.isEmpty() || !Files.isRegularFile(directory.resolve(name + ".class"))) {
            check(name, false, "assembler: " + assembled); return;
        }
        programs++;
        Path nativeBinary = directory.resolve(WINDOWS ? "native.exe" : "native");
        boolean compareNative = differential && clang != null;
        if (compareNative) {
            Path nativeSource = directory.resolve("source.c");
            Files.writeString(nativeSource, source, StandardCharsets.UTF_8);
            Result compiled = run(List.of(clang.toString(), "-std=c11", nativeSource.toString(), "-o", nativeBinary.toString()), directory, "");
            if (compiled.exit != 0) { check(name + " native compilation", false, compiled.toString()); return; }
        }
        for (int i = 0; i < examples.size(); i++) {
            Example example = examples.get(i);
            Result actual = run(List.of(JAVA, "-Xverify:all", "-cp", directory.toString(), name), directory, example.input);
            check(name + " IO " + (i + 1), actual.exit == 0 && actual.err.isEmpty() && actual.out.equals(example.output),
                    "expected=" + quote(example.output) + ", actual=" + actual);
            if (compareNative) {
                Result nativeResult = run(List.of(nativeBinary.toString()), directory, example.input);
                check(name + " native/JVM " + (i + 1), nativeResult.equals(actual), "native=" + nativeResult + ", JVM=" + actual);
                nativeCases++;
            }
        }
    }

    private void invalid(String name, String source, String diagnostic) {
        try { CRestrictedCompiler.compile(source, name); check(name, false, "invalid program was accepted"); }
        catch (RuntimeException e) {
            boolean correct = e.getMessage() != null && e.getMessage().contains(diagnostic);
            check(name, correct, "expected diagnostic " + diagnostic + ", got " + e);
            if (correct) rejected++;
        }
    }

    private void cli() throws Exception {
        String classPath = Arrays.stream(System.getProperty("java.class.path").split(Pattern.quote(System.getProperty("path.separator"))))
                .map(p -> ROOT.resolve(p).normalize().toString()).collect(Collectors.joining(System.getProperty("path.separator")));
        List<String> base = List.of(JAVA, "-cp", classPath, "CRestrictedCompiler");
        Result usage = run(base, work, "");
        check("CLI missing arguments", usage.exit == 2 && usage.err.contains("Usage:"), usage.toString());
        List<String> missingCommand = new ArrayList<>(base); missingCommand.add("does-not-exist.c");
        Result missing = run(missingCommand, work, "");
        check("CLI missing file", missing.exit == 1 && missing.err.contains("Compilation failed:"), missing.toString());
        Path invalidSource = work.resolve("invalid.c"), invalidOutput = work.resolve("invalid.j");
        Files.writeString(invalidSource, "int main(){}", StandardCharsets.UTF_8);
        List<String> invalidCommand = new ArrayList<>(base); invalidCommand.addAll(List.of(invalidSource.toString(), invalidOutput.toString(), "Invalid"));
        Result invalid = run(invalidCommand, work, "");
        check("CLI no partial output", invalid.exit == 1 && invalid.err.contains("must end with return") && !Files.exists(invalidOutput), invalid.toString());
        Path validSource = work.resolve("sample-file.c");
        Files.writeString(validSource, "int main(){return 0;}", StandardCharsets.UTF_8);
        List<String> validCommand = new ArrayList<>(base); validCommand.add(validSource.toString());
        Result valid = run(validCommand, work, "");
        Path output = work.resolve("sample-file.j");
        check("CLI default output and name", valid.exit == 0 && Files.exists(output) && Files.readString(output).contains(".class public sample_file"), valid.toString());
    }

    private void check(String name, boolean passed, String detail) {
        checks++;
        if (!passed) { failed++; note("FAIL " + name + ": " + detail); }
    }

    private void note(String value) { System.out.println(value); log.append(value).append('\n'); }

    private static List<String> strings(String jsonArray) {
        List<String> result = new ArrayList<>();
        Matcher matcher = QUOTED.matcher(jsonArray);
        while (matcher.find()) result.add(matcher.group(1));
        return result;
    }

    private static String prints(String... expressions) {
        StringBuilder source = new StringBuilder("int main(){");
        for (String expression : expressions) source.append("printf(\"%d\\n\",").append(expression).append(");");
        return source.append("return 0;}").toString();
    }

    private static Generated generated(Random random, int depth) {
        if (depth == 0 || random.nextInt(4) == 0) { int n = random.nextInt(41) - 20; return new Generated(Integer.toString(n), n); }
        Generated a = generated(random, depth - 1), b = generated(random, depth - 1);
        int op = random.nextInt(8);
        String token = List.of("+", "-", "*", "==", ">", "<", ">=", "<=").get(op);
        int value = switch (op) {
            case 0 -> a.value + b.value; case 1 -> a.value - b.value; case 2 -> a.value * b.value;
            case 3 -> a.value == b.value ? 1 : 0; case 4 -> a.value > b.value ? 1 : 0;
            case 5 -> a.value < b.value ? 1 : 0; case 6 -> a.value >= b.value ? 1 : 0;
            default -> a.value <= b.value ? 1 : 0;
        };
        return new Generated("(" + a.source + ")" + token + "(" + b.source + ")", value);
    }

    private static Path findExecutable(String name) {
        String path = System.getenv("PATH");
        if (path == null) return null;
        for (String directory : path.split(Pattern.quote(System.getProperty("path.separator")))) {
            Path candidate = Path.of(directory, WINDOWS ? name + ".exe" : name);
            if (Files.isRegularFile(candidate)) return candidate.toAbsolutePath();
        }
        return null;
    }

    private static Result run(List<String> command, Path directory, String input) throws Exception {
        Process process = new ProcessBuilder(command).directory(directory.toFile()).start();
        CompletableFuture<String> out = CompletableFuture.supplyAsync(() -> read(process.getInputStream()));
        CompletableFuture<String> err = CompletableFuture.supplyAsync(() -> read(process.getErrorStream()));
        try {
            process.getOutputStream().write(input.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().close();
            if (!process.waitFor(15, TimeUnit.SECONDS)) throw new IOException("process timed out: " + command);
            return new Result(process.exitValue(), out.get(5, TimeUnit.SECONDS).replace("\r\n", "\n"), err.get(5, TimeUnit.SECONDS).replace("\r\n", "\n"));
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private static String read(java.io.InputStream stream) {
        try (stream) { return new String(stream.readAllBytes(), StandardCharsets.UTF_8); }
        catch (IOException e) { throw new java.io.UncheckedIOException(e); }
    }

    private static String quote(String value) { return '"' + value.replace("\n", "\\n") + '"'; }
}
