# 一键发布桌面端：改版本号 → 打包 → 把安装包和 latest.yml 传到服务器的下载目录。
#
# 用法：
#   .\publish.ps1                  用 package.json 里现有的版本打包并上传
#   .\publish.ps1 -Version 1.1.0   先把版本号写成 1.1.0 再打包上传
#   .\publish.ps1 -SkipBuild       用 release\ 里已有的产物，只上传
#   .\publish.ps1 -SkipUpload      只打包，不传服务器
#
# 前提：本机能直接 scp / ssh 到服务器（配好免密，或者接受手动输密码）。

[CmdletBinding()]
param(
    [string]$Version,
    [string]$Server = 'root@49.232.236.230',
    [string]$RemoteDir = '/home/shiyi-memo/start/downloads',
    [switch]$SkipBuild,
    [switch]$SkipUpload
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$packagePath = Join-Path $root 'package.json'
$releaseDir = Join-Path $root 'release'

# ---- 版本号只有 package.json 一处，后端会去读传上去的 latest.yml ----
# 必须显式按 UTF-8 读：Windows PowerShell 5.1 的 Get-Content 会把没有 BOM 的
# UTF-8 当 ANSI 解，package.json 里的中文会变乱码，还会把后面的引号一起吃掉
$raw = [System.IO.File]::ReadAllText($packagePath)
$versionPattern = [regex]'"version"\s*:\s*"([^"]+)"'
$current = $versionPattern.Match($raw).Groups[1].Value
if (-not $current) { throw '没能从 package.json 里读出 version' }

if ($Version) {
    if ($Version -notmatch '^\d+\.\d+\.\d+$') {
        throw "版本号要写成 1.2.3 这种形式，收到的是：$Version"
    }
    if ($Version -eq $current) {
        Write-Host "版本号没变（还是 $current）" -ForegroundColor Yellow
    } else {
        $updated = $versionPattern.Replace($raw, '"version": "' + $Version + '"', 1)
        # 写回去之前先确认还是合法 JSON，改坏了就宁可什么都不动
        try {
            $null = $updated | ConvertFrom-Json
        } catch {
            throw "改完版本号后 package.json 不是合法 JSON，已放弃写入：$($_.Exception.Message)"
        }
        [System.IO.File]::WriteAllText($packagePath, $updated, [System.Text.UTF8Encoding]::new($false))
        Write-Host "版本号：$current → $Version" -ForegroundColor Green
    }
    $current = $Version
}

# ---- 打包 ----
if ($SkipBuild) {
    Write-Host "跳过打包，直接用 $releaseDir 里的产物" -ForegroundColor Yellow
} else {
    Write-Host "==> 打包 $current" -ForegroundColor Cyan
    $env:ELECTRON_MIRROR = 'https://npmmirror.com/mirrors/electron/'
    $env:ELECTRON_BUILDER_BINARIES_MIRROR = 'https://npmmirror.com/mirrors/electron-builder-binaries/'
    Push-Location $root
    try {
        npm.cmd run dist
        if ($LASTEXITCODE -ne 0) { throw '打包失败' }
    } finally {
        Pop-Location
    }
}

# ---- 这三个文件必须一起传，缺一个自动更新就废 ----
$installer = "shiyi-memo-setup-$current.exe"
$artifacts = @(
    (Join-Path $releaseDir $installer),
    (Join-Path $releaseDir "$installer.blockmap"),
    (Join-Path $releaseDir 'latest.yml')
)
foreach ($file in $artifacts) {
    if (-not (Test-Path -LiteralPath $file)) { throw "缺少构建产物：$file" }
}

if ($SkipUpload) {
    Write-Host "==> 产物已就绪，跳过上传：$releaseDir" -ForegroundColor Green
    exit 0
}

# ---- 上传 ----
foreach ($tool in @('scp', 'ssh')) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
        throw "找不到 $tool，需要装 Windows 自带的 OpenSSH 客户端"
    }
}

Write-Host "==> 上传到 ${Server}:${RemoteDir}" -ForegroundColor Cyan
# 第一次跑的时候目录还不存在，scp 不会自己建
ssh $Server "mkdir -p '$RemoteDir'"
if ($LASTEXITCODE -ne 0) { throw '连接服务器失败，检查地址和 SSH 免密' }

scp $artifacts ($Server + ':' + $RemoteDir + '/')
if ($LASTEXITCODE -ne 0) { throw '上传失败，检查服务器地址和 SSH 免密' }

# 网页上那个「下载桌面端」指向固定文件名，在服务器上拷一份，省得再传 100MB
ssh $Server "cp '$RemoteDir/$installer' '$RemoteDir/shiyi-memo-setup.exe'"
if ($LASTEXITCODE -ne 0) { throw '生成固定文件名副本失败' }

Write-Host ''
Write-Host "发布完成：$current" -ForegroundColor Green
Write-Host '已装的客户端下次启动会看到更新提示。'
Write-Host '验证：curl -I https://memo.goldminer.cloud/downloads/latest.yml'
