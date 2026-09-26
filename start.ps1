# 启动拾忆：构建前端 → 打包后端 → 运行。用法：在项目根目录执行 .\start.ps1
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$root\.tools\apache-maven-3.9.11\bin;C:\Program Files\nodejs;$env:Path"

Write-Host '==> 构建前端' -ForegroundColor Cyan
Push-Location "$root\frontend"
npm run build
Pop-Location

Write-Host '==> 打包后端' -ForegroundColor Cyan
Push-Location $root
mvn -B -DskipTests package

Write-Host '==> 启动服务：http://localhost:8080' -ForegroundColor Green
java -jar "$root\target\shiyi-memo-1.0.0.jar"
