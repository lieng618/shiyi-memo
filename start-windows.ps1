# 启动拾忆（Windows）：构建前端 → 打包后端（jar 输出到 start\）→ pm2 启动 / 重启。
# 用法：在项目根目录执行 .\start-windows.ps1
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$jarName = 'shiyi-memo-1.0.0.jar'

if (Test-Path 'C:\Program Files\Java\jdk-21') { $env:JAVA_HOME = 'C:\Program Files\Java\jdk-21' }
$toolPaths = @(
    "$env:JAVA_HOME\bin",
    "$root\.tools\apache-maven-3.9.11\bin",
    'C:\java\apache-maven-3.10.0-bin\bin',
    'C:\Program Files\nodejs'
) | Where-Object { $_ -and (Test-Path $_) }
$env:Path = ($toolPaths + $env:Path) -join ';'

Write-Host '==> [1/3] 构建前端' -ForegroundColor Cyan
Push-Location "$root\frontend"
# 用 npm.cmd：PowerShell 默认禁止执行 npm.ps1，直接写 npm 会被执行策略拦下
npm.cmd run build
$code = $LASTEXITCODE
Pop-Location
if ($code -ne 0) { throw "前端构建失败（退出码 $code）" }

Write-Host '==> [2/3] 打包后端（jar 输出到 start\）' -ForegroundColor Cyan
Push-Location $root
mvn -B -DskipTests clean package
$code = $LASTEXITCODE
Pop-Location
if ($code -ne 0) { throw "后端打包失败（退出码 $code）" }

$jarPath = Join-Path "$root\start" $jarName
if (-not (Test-Path $jarPath)) { throw "打包结束但未找到 $jarPath" }

# pm2 从 start 目录加载 jar，也在这里读 start\application-local.yml
$env:APP_DIR = "$root\start"
$env:APP_JAR = $jarName

if (Get-Command pm2 -ErrorAction SilentlyContinue) {
    Write-Host '==> [3/3] pm2 启动 / 重启服务：http://localhost:8080' -ForegroundColor Green
    pm2 startOrReload "$root\ecosystem.config.js" --update-env
    pm2 save
    pm2 status
} else {
    Write-Host '==> [3/3] 未安装 pm2，直接用 java 启动：http://localhost:8080' -ForegroundColor Yellow
    java -jar $jarPath
}
