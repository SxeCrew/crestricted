param([string]$GoldenDirectory = '')
$ErrorActionPreference = 'Stop'
Push-Location -LiteralPath $PSScriptRoot
try {
    & javac --release 17 -encoding UTF-8 -sourcepath . -Xlint:all -Werror -d build CRestrictedCompiler.java CompilerTests.java
    if ($LASTEXITCODE -ne 0) { throw 'Compilation of the compiler/test runner failed.' }
    if ($GoldenDirectory) { & java -cp build CompilerTests $GoldenDirectory }
    else { & java -cp build CompilerTests }
    if ($LASTEXITCODE -ne 0) { throw 'Compiler tests failed. See the FAIL messages above.' }
} finally {
    Pop-Location
}
