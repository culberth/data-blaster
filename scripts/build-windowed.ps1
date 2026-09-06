<#
.SYNOPSIS
    Builds a self-contained DataBlaster.exe with NO console window (normal GUI app).
    Output: dist\windowed\DataBlaster\DataBlaster.exe

.NOTES
    A wrapper, not the implementation. The jpackage invocation lives in pom.xml under
    the `app-image` profile, so there is one copy of it rather than one per variant.
#>
$ErrorActionPreference = 'Stop'

& mvn -f (Join-Path (Split-Path -Parent $PSScriptRoot) 'pom.xml') -Papp-image clean verify
if ($LASTEXITCODE -ne 0) { throw "Maven build failed (exit $LASTEXITCODE)." }
