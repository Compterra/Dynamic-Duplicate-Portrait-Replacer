$ErrorActionPreference = "Stop"

$mod = Split-Path -Parent $MyInvocation.MyCommand.Path
$core = Resolve-Path (Join-Path $mod "..\..\starsector-core")
$classes = Join-Path $mod "jars\classes"
$jar = Join-Path $mod "jars\DynamicPortraits.jar"

New-Item -ItemType Directory -Force -Path (Join-Path $mod "jars") | Out-Null
Remove-Item -LiteralPath $classes -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $classes | Out-Null

$classpath = @(
    Join-Path $core "starfarer.api.jar"
    Join-Path $core "starfarer_obf.jar"
    Join-Path $core "fs.common_obf.jar"
    Join-Path $core "json.jar"
    Join-Path $core "log4j-1.2.9.jar"
    Join-Path $core "lwjgl.jar"
    Join-Path $core "lwjgl_util.jar"
) -join ";"

$sources = Get-ChildItem -LiteralPath (Join-Path $mod "src") -Recurse -Filter *.java | ForEach-Object { $_.FullName }
if (-not $sources) {
    throw "No Java source files found."
}

javac -encoding UTF-8 -source 8 -target 8 -classpath $classpath -d $classes $sources
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE"
}

jar cf $jar -C $classes .
if ($LASTEXITCODE -ne 0) {
    throw "jar failed with exit code $LASTEXITCODE"
}

Remove-Item -LiteralPath $classes -Recurse -Force

Write-Host "Built $jar"
