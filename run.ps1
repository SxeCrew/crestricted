param(
    [string]$Source = '',
    [string]$ClassName = 'Program'
)
$ErrorActionPreference = 'Stop'
$taskHasInput = $MyInvocation.ExpectingInput
$taskStandardInput = @($input)
if (-not $Source) {
    $Source = Join-Path $PSScriptRoot 'Crestricted.c'
    if (-not (Test-Path -LiteralPath $Source)) {
        $Source = Join-Path $PSScriptRoot 'lab-examples/AllFeatures.c'
    }
}
$taskSource = (Resolve-Path -LiteralPath $Source).Path
Push-Location -LiteralPath $PSScriptRoot
try {
    & javac --release 17 -encoding UTF-8 -sourcepath . -d build CRestrictedCompiler.java
    if ($LASTEXITCODE -ne 0) { throw 'Java compiler build failed.' }
    $taskRunDirectory = Join-Path (Join-Path $PSScriptRoot 'build') ('run-' + [Guid]::NewGuid().ToString('N'))
    $null = New-Item -ItemType Directory -Path $taskRunDirectory
    $taskAssembly = Join-Path $taskRunDirectory 'program.j'
    & java -cp build CRestrictedCompiler $taskSource $taskAssembly $ClassName
    if ($LASTEXITCODE -ne 0) { throw 'Crestricted translation failed.' }
    & java -jar jasmin.jar -d $taskRunDirectory $taskAssembly
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath (Join-Path $taskRunDirectory "$ClassName.class"))) { throw 'Jasmin assembly failed.' }
    if ($taskHasInput) { $taskStandardInput | & java -Xverify:all -cp $taskRunDirectory $ClassName }
    else { & java -Xverify:all -cp $taskRunDirectory $ClassName }
    if ($LASTEXITCODE -ne 0) { throw 'The generated JVM program failed.' }
} finally {
    Pop-Location
}
