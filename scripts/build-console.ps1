<#
.SYNOPSIS
    Builds a self-contained DataBlaster.exe WITH a console window attached, so
    Spring Boot / JavaFX log output (stdout/stderr) is visible while it runs.
    Output: dist\console\DataBlaster\DataBlaster.exe

.NOTES
    A wrapper, not the implementation. The jpackage invocation lives in pom.xml under
    the `app-image-console` profile, which is the `app-image` one plus --win-console.
#>
$ErrorActionPreference = 'Stop'

& mvn -f (Join-Path (Split-Path -Parent $PSScriptRoot) 'pom.xml') -Papp-image-console clean verify
if ($LASTEXITCODE -ne 0) { throw "Maven build failed (exit $LASTEXITCODE)." }
