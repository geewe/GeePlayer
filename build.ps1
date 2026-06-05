<#
.SYNOPSIS
DLNA Receiver Android 构建与签名脚本

.DESCRIPTION
自动执行:
1. 清理 & 构建 Release APK
2. 生成签名密钥 (如果不存在)
3. 签名 APK
4. 对齐 & 生成最终 APK

.USAGE
.\build.ps1                   # 构建 Debug APK
.\build.ps1 -Release          # 构建 Release (签名) APK
.\build.ps1 -Clean            # 清理后构建
.\build.ps1 -Install          # 构建并安装到连接的设备
#>

param(
    [switch]$Release,
    [switch]$Clean,
    [switch]$Install,
    [switch]$Bundle        # 生成 AAB
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$AppPath = Join-Path $ProjectRoot "app"
$Gradlew = Join-Path $ProjectRoot "gradlew.bat"
$KeystorePath = Join-Path $ProjectRoot "app\dlna_receiver.keystore"
$KeystoreProps = Join-Path $AppPath "keystore.properties"
$ApkOutput = Join-Path $AppPath "build\outputs\apk"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  DLNA Receiver 构建脚本" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "项目路径: $ProjectRoot" -ForegroundColor Gray

# 检查 Gradle
if (-not (Test-Path $Gradlew)) {
    Write-Error "gradlew.bat 未找到！请确认项目结构正确。"
    exit 1
}

# Step 1: 清理
if ($Clean) {
    Write-Host "`n[1/4] 清理旧构建..." -ForegroundColor Yellow
    & $Gradlew clean
    if ($LASTEXITCODE -ne 0) { Write-Error "清理失败"; exit 1 }
    Write-Host "  ✓ 清理完成" -ForegroundColor Green
}

# Step 2: 生成签名密钥 (Release 构建)
if ($Release) {
    Write-Host "`n[2/4] 检查签名密钥..." -ForegroundColor Yellow
    
    if (-not (Test-Path $KeystorePath)) {
        Write-Host "  生成新的签名密钥..." -ForegroundColor Gray
        $dname = "CN=DLNA Receiver, OU=Development, O=Codex, L=Unknown, ST=Unknown, C=CN"
        keytool -genkey -v -keystore $KeystorePath `
            -alias dlna_receiver -keyalg RSA -keysize 2048 -validity 10000 `
            -dname $dname -storepass "dlna1234" -keypass "dlna1234"
        
        if ($LASTEXITCODE -eq 0) {
            Write-Host "  ✓ 密钥已生成: $KeystorePath" -ForegroundColor Green
        } else {
            Write-Error "密钥生成失败，请手动执行: keytool -genkey ..."
            exit 1
        }
    } else {
        Write-Host "  ✓ 密钥已存在" -ForegroundColor Green
    }

    # 生成 keystore.properties
    @"
storeFile=$KeystorePath
storePassword=dlna1234
keyAlias=dlna_receiver
keyPassword=dlna1234
"@ | Set-Content -Path $KeystoreProps -Force
    Write-Host "  ✓ keystore.properties 已生成" -ForegroundColor Green
}

# Step 3: 构建 APK
Write-Host "`n[3/4] 构建 APK..." -ForegroundColor Yellow

if ($Bundle) {
    Write-Host "  构建 AAB (Android App Bundle)..." -ForegroundColor Gray
    & $Gradlew bundleRelease
    $ApkPath = Join-Path $ProjectRoot "app\build\outputs\bundle\release\app-release.aab"
    $BuildType = "AAB"
} elseif ($Release) {
    Write-Host "  构建 Release APK..." -ForegroundColor Gray
    & $Gradlew assembleRelease
    $ApkPath = Join-Path $ProjectRoot "app\build\outputs\apk\release\app-release.apk"
    $BuildType = "APK (Release)"
} else {
    Write-Host "  构建 Debug APK..." -ForegroundColor Gray
    & $Gradlew assembleDebug
    $ApkPath = Join-Path $ProjectRoot "app\build\outputs\apk\debug\app-debug.apk"
    $BuildType = "APK (Debug)"
}

if ($LASTEXITCODE -ne 0) {
    Write-Error "构建失败，请检查错误信息"
    exit 1
}

# Step 4: 安装 (可选)
if ($Install) {
    Write-Host "`n[4/4] 安装到设备..." -ForegroundColor Yellow
    
    $adbPath = Get-Command "adb" -ErrorAction SilentlyContinue
    if (-not $adbPath) {
        Write-Warning "adb 未找到，请确保 Android SDK platform-tools 在 PATH 中"
        Write-Host "  手动安装: adb install $ApkPath" -ForegroundColor Gray
    } else {
        & adb install -r $ApkPath
        if ($LASTEXITCODE -eq 0) {
            Write-Host "  ✓ 安装成功！" -ForegroundColor Green
        } else {
            Write-Warning "安装失败，请确保设备已连接并启用 USB 调试"
        }
    }
}

# 完成
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  构建完成！" -ForegroundColor Green
Write-Host "  类型: $BuildType" -ForegroundColor Gray
Write-Host "  输出: $ApkPath" -ForegroundColor Gray
Write-Host "========================================" -ForegroundColor Cyan
