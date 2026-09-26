# 开发模式：一个窗口起后端（8080），一个窗口起前端（5173，带热更新）
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$root\.tools\apache-maven-3.9.11\bin;C:\Program Files\nodejs;$env:Path"

Start-Process powershell -ArgumentList '-NoExit', '-Command', "cd '$root'; mvn spring-boot:run"
Start-Sleep -Seconds 2
Start-Process powershell -ArgumentList '-NoExit', '-Command', "cd '$root\frontend'; npm run dev"

Write-Host '后端 http://localhost:8080 ，前端 http://localhost:5173' -ForegroundColor Green
