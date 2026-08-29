<#
.SYNOPSIS
    Builds a self-contained JFXRibbon.exe WITH a console window attached, so
    Spring Boot / JavaFX log output (stdout/stderr) is visible while it runs.
    Output: dist\console\JFXRibbon\JFXRibbon.exe
#>
& "$PSScriptRoot\Build-JFXRibbonAppImage.ps1" -Console
