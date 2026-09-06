param(
    [Parameter(Mandatory)][string]$JdkDirectory,
    [Parameter(Mandatory)][string]$KotlinCompilerHome,
    [Parameter(Mandatory)][string]$JunitJar,
    [Parameter(Mandatory)][string]$HamcrestJar
)
$ErrorActionPreference = 'Stop'
$java = Join-Path $JdkDirectory 'bin/java.exe'
$stdlib = Join-Path $KotlinCompilerHome 'lib/kotlin-stdlib.jar'
$compilerClasspath = Join-Path $KotlinCompilerHome 'lib/*'
$testJar = Join-Path ([System.IO.Path]::GetTempPath()) ('territory-tests-' + [guid]::NewGuid() + '.jar')
$testClasspath = "$stdlib;$JunitJar;$HamcrestJar"
Push-Location (Join-Path $PSScriptRoot '..')
try {
    & $java -cp $compilerClasspath org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib `
        app/src/main/java/com/daengs/app/territory/TerritoryClaim.kt `
        app/src/main/java/com/daengs/app/territory/InMemoryTerritoryClaimRepository.kt `
        app/src/test/java/com/daengs/app/territory/TerritoryClaimTest.kt `
        -cp $testClasspath -d $testJar
    if ($LASTEXITCODE -ne 0) { throw 'Kotlin compilation failed' }
    & $java -cp "$testJar;$testClasspath;app/src/test/resources" `
        org.junit.runner.JUnitCore com.daengs.app.territory.TerritoryClaimTest
    if ($LASTEXITCODE -ne 0) { throw 'Territory claim tests failed' }
} finally {
    Pop-Location
    if (Test-Path -LiteralPath $testJar) { Remove-Item -LiteralPath $testJar }
}
