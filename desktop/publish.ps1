# 一键发布桌面端：改版本号 → 打包 → 把安装包和 latest.yml 传到服务器的下载目录。
#
# 用法：
#   .\publish.ps1                  用 package.json 里现有的版本打包并上传
#   .\publish.ps1 -Version 1.1.0   先把版本号写成 1.1.0 再打包上传
#   .\publish.ps1 -SkipBuild       用 release\ 里已有的产物，只上传
#   .\publish.ps1 -SkipUpload      只打包，不传服务器
#
# 发布目标（服务器地址和目录）每个人都不一样，所以不写死在脚本里：
# 把 publish.local.psd1.example 复制成 publish.local.psd1 填好，或者用
# -Server / -RemoteDir 直接传。前者已经在 .gitignore 里，不会被提交。
#
# 前提：本机能 scp / ssh 到服务器；配好 SSH 公钥就不用每次输密码。

[CmdletBinding()]
param(
    [string]$Version,
    # 形如 ubuntu@1.2.3.4，不传就读 publish.local.psd1
    [string]$Server,
    [string]$RemoteDir,
    [switch]$SkipBuild,
    [switch]$SkipUpload
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$packagePath = Join-Path $root 'package.json'
$releaseDir = Join-Path $root 'release'

# ---- 发布目标：参数 > publish.local.psd1 > 环境变量 ----
if (-not $SkipUpload) {
    $localConfigPath = Join-Path $root 'publish.local.psd1'
    $localConfig = @{}
    if (Test-Path -LiteralPath $localConfigPath) {
        $localConfig = Import-PowerShellDataFile -Path $localConfigPath
    }
    if (-not $Server) { $Server = $localConfig.Server }
    if (-not $RemoteDir) { $RemoteDir = $localConfig.RemoteDir }
    if (-not $Server) { $Server = $env:SHIYI_PUBLISH_SERVER }
    if (-not $RemoteDir) { $RemoteDir = $env:SHIYI_PUBLISH_REMOTE_DIR }

    if (-not $Server -or -not $RemoteDir) {
        throw @"
没配置发布目标。二选一：
  1. 把 publish.local.psd1.example 复制成 publish.local.psd1，填上你的服务器和目录
  2. 直接传参：.\publish.ps1 -Server 账号@服务器 -RemoteDir /服务器上的目录
"@
    }
}

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
# 部署时这个目录多半是 root 建的，先探一下当前账号能不能直接写
ssh $Server "mkdir -p '$RemoteDir' 2>/dev/null && test -w '$RemoteDir'"
$canWriteDirectly = ($LASTEXITCODE -eq 0)

if ($canWriteDirectly) {
    scp $artifacts ($Server + ':' + $RemoteDir + '/')
    if ($LASTEXITCODE -ne 0) { throw '上传失败，检查服务器地址和 SSH 登录' }
    # 网页上那个「下载桌面端」指向固定文件名，在服务器上拷一份，省得再传 100MB
    ssh $Server "cp '$RemoteDir/$installer' '$RemoteDir/shiyi-memo-setup.exe'"
    if ($LASTEXITCODE -ne 0) { throw '生成固定文件名副本失败' }
} else {
    # 目录属于 root：先传到 ubuntu 能写的临时目录，再用 sudo 搬进去。
    # -t 是为了让 sudo 需要密码时有地方弹提示。
    Write-Host '这个目录当前账号写不了，改用 sudo 搬运（可能要输一次 sudo 密码）' -ForegroundColor Yellow
    $staging = '/tmp/shiyi-memo-release'
    ssh $Server "rm -rf '$staging' && mkdir -p '$staging'"
    if ($LASTEXITCODE -ne 0) { throw '连接服务器失败，检查地址和 SSH 登录' }

    scp $artifacts ($Server + ':' + $staging + '/')
    if ($LASTEXITCODE -ne 0) { throw '上传到服务器临时目录失败' }

    ssh -t $Server "sudo mkdir -p '$RemoteDir' && sudo mv '$staging'/* '$RemoteDir'/ && sudo chmod 644 '$RemoteDir'/* && sudo cp '$RemoteDir/$installer' '$RemoteDir/shiyi-memo-setup.exe' && rm -rf '$staging'"
    if ($LASTEXITCODE -ne 0) { throw 'sudo 搬运失败' }
}

Write-Host ''
Write-Host "发布完成：$current" -ForegroundColor Green
Write-Host '已装的客户端下次启动会看到更新提示。'
Write-Host '验证：curl -I https://memo.goldminer.cloud/downloads/latest.yml'
