param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$MavenRepository = (Join-Path $env:USERPROFILE '.m2\repository')
)
$ErrorActionPreference = 'Stop'
if (!$JavaHome) { throw 'Set JAVA_HOME or pass -JavaHome pointing to your JDK.' }
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Push-Location $projectRoot
try {
    $env:JAVA_HOME = $JavaHome
    & .\mvnw.cmd test
    if ($LASTEXITCODE -ne 0) { throw 'Maven compilation failed.' }
    $netbeansJars = (Get-ChildItem (Join-Path $MavenRepository 'org\netbeans\api') -Recurse -Filter '*RELEASE310.jar').FullName
    $gsonJar = Join-Path $MavenRepository 'com\google\code\gson\gson\2.14.0\gson-2.14.0.jar'
    $classpath = (@('target/test-classes', 'target/classes') + $netbeansJars + @($gsonJar)) -join ';'
    & (Join-Path $JavaHome 'bin\java.exe') '-Djava.awt.headless=true' -cp $classpath com.suraj.netbeans.codex.ContextAndProgressChecks
    if ($LASTEXITCODE -ne 0) { throw 'Editor-context or progress checks failed.' }
    & (Join-Path $JavaHome 'bin\java.exe') '-Djava.awt.headless=true' -cp $classpath com.suraj.netbeans.codex.HistoryChecks
    if ($LASTEXITCODE -ne 0) { throw 'Chat history checks failed.' }
    & (Join-Path $JavaHome 'bin\java.exe') '-Djava.awt.headless=true' -cp $classpath com.suraj.netbeans.codex.TranscriptLayoutChecks
    if ($LASTEXITCODE -ne 0) { throw 'Transcript layout checks failed.' }
    & (Join-Path $JavaHome 'bin\java.exe') '-Djava.awt.headless=true' -cp $classpath com.suraj.netbeans.codex.MultiRepositoryChecks
    if ($LASTEXITCODE -ne 0) { throw 'Multi-repository checks failed.' }
    & (Join-Path $JavaHome 'bin\java.exe') '-Djava.awt.headless=true' -cp $classpath com.suraj.netbeans.codex.AccessModeChecks
    if ($LASTEXITCODE -ne 0) { throw 'Access mode checks failed.' }
    & (Join-Path $JavaHome 'bin\java.exe') '-Djava.awt.headless=true' -cp $classpath com.suraj.netbeans.codex.ImagePasteChecks
    if ($LASTEXITCODE -ne 0) { throw 'Image paste checks failed.' }
} finally {
    Pop-Location
}
