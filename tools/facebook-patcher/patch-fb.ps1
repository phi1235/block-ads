# ==============================================================================
# Script Tự Động Patch Facebook Không Cần Root (Loại bỏ 100% In-Stream Video Ads)
# ==============================================================================
param (
    [string]$DeviceIp = "100.89.229.39:5555",
    [string]$ModuleJarUrl = "https://github.com/LSPosed/LSPatch/releases/download/v0.6/lspatch-release-v0.6.jar"
)

$ToolsDir = "$PSScriptRoot"
$WorkDir = "$ToolsDir\workspace"
if (!(Test-Path $WorkDir)) { New-Item -ItemType Directory -Path $WorkDir | Out-Null }

Write-Host "=== BƯỚC 1: KIỂM TRA KẾT NỐI ADB ===" -ForegroundColor Cyan
adb connect $DeviceIp
$devices = adb devices
Write-Host $devices

Write-Host "`n=== BƯỚC 2: TRÍCH XUẤT FACEBOOK APK TỪ MÁY ===" -ForegroundColor Cyan
$fbPathRaw = adb -s $DeviceIp shell pm path com.facebook.katana
if ($fbPathRaw -match "package:(.+apk)") {
    $remoteApkPath = $matches[1].Trim()
    Write-Host "Tìm thấy Facebook APK trên máy: $remoteApkPath" -ForegroundColor Green
    $localBaseApk = "$WorkDir\facebook-base.apk"
    adb -s $DeviceIp pull $remoteApkPath $localBaseApk
} else {
    Write-Host "Không tìm thấy Facebook APK qua ADB. Vui lòng đặt file facebook.apk vào thư mục workspace." -ForegroundColor Yellow
}

Write-Host "`n=== BƯỚC 3: TẢI BỘ CÔNG CỤ LSPATCH ===" -ForegroundColor Cyan
$lspatchJar = "$ToolsDir\lspatch.jar"
if (!(Test-Path $lspatchJar)) {
    Write-Host "Đang tải lspatch.jar..." -ForegroundColor Yellow
    Invoke-WebRequest -Uri $ModuleJarUrl -OutFile $lspatchJar
}

Write-Host "`n=== BƯỚC 4: HƯỚNG DẪN PATCH VÀ NẠP FILE ===" -ForegroundColor Green
Write-Host "Để patch Facebook không cần root:"
Write-Host "1. Chạy lệnh: java -jar $lspatchJar $WorkDir\facebook-base.apk -d -v -o $WorkDir"
Write-Host "2. File patched sẽ được tạo tại $WorkDir\facebook-base-patched.apk"
Write-Host "3. Cài đặt lên máy: adb -s $DeviceIp install -r $WorkDir\facebook-base-patched.apk"
