<#
.SYNOPSIS
    Builds a self-contained DataBlaster.exe WITH a console window attached, so
    Spring Boot / JavaFX log output (stdout/stderr) is visible while it runs.
    Output: dist\console\DataBlaster\DataBlaster.exe
#>
& "$PSScriptRoot\Build-DataBlasterAppImage.ps1" -Console
