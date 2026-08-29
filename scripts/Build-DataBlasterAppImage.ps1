<#
.SYNOPSIS
    Builds the Spring Boot fat jar with Maven, then packages it into a self-contained
    Windows app-image (bundled JRE, no separate Java install required) using jpackage.

.PARAMETER Console
    Include a console window (--win-console) alongside the app window. Useful for
    seeing Spring Boot / JavaFX log output. Omit for a normal windowed app with no
    console.

.NOTES
    Not meant to be run directly by end users - use build-windowed.ps1 or
    build-console.ps1 in this same folder instead.
#>
param(
    [switch]$Console
)

$ErrorActionPreference = 'Stop'

$scriptDir  = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectDir = Split-Path -Parent $scriptDir

foreach ($tool in 'mvn', 'jpackage') {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
        throw "'$tool' was not found on PATH."
    }
}

Push-Location $projectDir
try {
    Write-Host "==> Building fat jar with Maven..." -ForegroundColor Cyan
    & mvn -q clean package "-DskipTests"
    if ($LASTEXITCODE -ne 0) { throw "Maven build failed (exit $LASTEXITCODE)." }

    function Get-PomValue([string]$expression) {
        $value = & mvn -q "-Dexpression=$expression" -DforceStdout help:evaluate
        if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($value)) {
            throw "Could not evaluate '$expression' from the pom."
        }
        return $value.Trim()
    }

    $appName    = "DataBlaster"
    $appVersion = (Get-PomValue 'project.version') -replace '-SNAPSHOT', ''
    $fxVersion  = Get-PomValue 'javafx.version'
    $localRepo  = Get-PomValue 'settings.localRepository'

    Write-Host "==> App version: $appVersion, JavaFX: $fxVersion" -ForegroundColor Cyan

    $fxModules = 'javafx-base', 'javafx-graphics', 'javafx-controls', 'javafx-fxml'
    $modulePathEntries = foreach ($module in $fxModules) {
        $jar = Join-Path $localRepo "org\openjfx\$module\$fxVersion\$module-$fxVersion-win.jar"
        if (-not (Test-Path $jar)) {
            throw "Missing JavaFX module jar: $jar`nRun 'mvn -q dependency:resolve' first, or check javafx.version in pom.xml."
        }
        $jar
    }
    $modulePath = $modulePathEntries -join ';'

    # Match the artifact by its exact Maven coordinates rather than taking whatever jar sorts
    # first: adding maven-source-plugin would otherwise stage '...-sources.jar' as --main-jar
    # (it sorts before '...-SNAPSHOT.jar'), giving an exe that fails with "Failed to launch JVM".
    # Resolved through Maven, not by reading the XML: raw XML returns properties unexpanded, so a
    # coordinate like <version>${revision}</version> would produce a filename that matches nothing.
    $mainJarName = "$(Get-PomValue 'project.artifactId')-$(Get-PomValue 'project.version').jar"
    $mainJar = Get-ChildItem -Path (Join-Path $projectDir 'target') -Filter $mainJarName |
        Select-Object -First 1
    if (-not $mainJar) {
        throw "Expected jar 'target\$mainJarName' not found. Did the Maven build succeed?"
    }

    # jpackage copies its whole --input directory into the app image, so stage just
    # the runnable jar rather than pointing it at all of target\ (classes, reports, etc).
    $stagingDir = Join-Path $projectDir 'target\jpackage-input'
    if (Test-Path $stagingDir) { Remove-Item -Recurse -Force $stagingDir }
    New-Item -ItemType Directory -Path $stagingDir | Out-Null
    Copy-Item $mainJar.FullName $stagingDir

    # Output lives outside target\ so `mvn clean` on one variant's build doesn't
    # delete the other variant's already-built app image.
    $variant = if ($Console) { 'console' } else { 'windowed' }
    $destDir = Join-Path $projectDir "dist\$variant"
    if (Test-Path $destDir) { Remove-Item -Recurse -Force $destDir }

    $jpackageArgs = @(
        '--type', 'app-image'
        '--input', $stagingDir
        '--dest', $destDir
        '--name', $appName
        '--app-version', $appVersion
        '--vendor', 'com.culberth.tools'
        '--description', 'Mode-based data tool with an Office-style Ribbon, backed by a Spring Boot context'
        '--main-jar', $mainJar.Name
        '--module-path', $modulePath
        # javafx.controls/javafx.fxml are what the UI needs. The five java.* modules after them
        # were added for the embedded Tomcat servlet container, which this project no longer has.
        #
        # They are deliberately still listed. Some are plausibly still required by the Spring
        # context alone (java.instrument for LoadTimeWeaver support, java.naming and java.sql for
        # types spring-core references), and java.security.jgss almost certainly is not. But a
        # module missing from a jpackage image fails with NoClassDefFoundError when the packaged
        # .exe is launched, not when it is built -- and this script is a manual Windows step that
        # CI never runs, so a wrong trim would stay invisible until someone ran the shipped app.
        # Narrowing this list means building both variants and actually launching them; until
        # someone does that, the cost of keeping them is a slightly larger runtime image.
        '--add-modules', 'javafx.controls,javafx.fxml,java.management,java.naming,java.instrument,java.sql,java.security.jgss'
    )
    if ($Console) { $jpackageArgs += '--win-console' }

    Write-Host "==> Running jpackage ($variant)..." -ForegroundColor Cyan
    & jpackage @jpackageArgs
    if ($LASTEXITCODE -ne 0) { throw "jpackage failed (exit $LASTEXITCODE)." }

    $exePath = Join-Path $destDir "$appName\$appName.exe"
    Write-Host "==> Done: $exePath" -ForegroundColor Green
}
finally {
    Pop-Location
}
