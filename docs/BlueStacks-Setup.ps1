param([string]$Serial='', [int]$AdbPort=0)
$ErrorActionPreference='Stop'
$appPackage='jp.akagumi.routegps'
$adbPath=Join-Path $PSScriptRoot 'platform-tools\adb.exe'
$apkPath=Join-Path $PSScriptRoot 'RouteGPS.apk'
function Invoke-Android {
    param([string[]]$Arguments)
    $output=& $adbPath -s $script:targetSerial @Arguments 2>&1
    $code=$LASTEXITCODE
    if($code -ne 0){throw "ADB failed: $($output -join ' ')"}
    return $output
}
try {
    if(!(Test-Path -LiteralPath $adbPath)){throw 'Bundled platform-tools/adb.exe is missing. Extract the full ZIP first.'}
    if($AdbPort -gt 0){
        & $adbPath connect "127.0.0.1:$AdbPort"
        if($LASTEXITCODE -ne 0){throw 'Cannot connect to the selected ADB port.'}
        if(!$Serial){$Serial="127.0.0.1:$AdbPort"}
    }
    $deviceLines=& $adbPath devices
    $devices=@($deviceLines | Where-Object {$_ -match '^([^\s]+)\s+device$'} | ForEach-Object {($_ -split '\s+')[0]})
    if(!$Serial){
        if($devices.Count -eq 0){throw 'No Android device connected. Enable ADB in BlueStacks Settings > Advanced and try again with -AdbPort PORT.'}
        if($devices.Count -gt 1){throw "Multiple devices found: $($devices -join ', '). Run with -Serial DEVICE to select your BlueStacks instance."}
        $Serial=$devices[0]
    }
    $script:targetSerial=$Serial
    Write-Host "Installing Route GPS on $Serial ..."
    Invoke-Android -Arguments @('install','--no-incremental','-r',$apkPath) | Write-Host
    Invoke-Android -Arguments @('shell','appops','set',$appPackage,'android:mock_location','allow') | Write-Host
    Invoke-Android -Arguments @('shell','pm','grant',$appPackage,'android.permission.ACCESS_FINE_LOCATION') | Write-Host
    Invoke-Android -Arguments @('shell','pm','grant',$appPackage,'android.permission.ACCESS_COARSE_LOCATION') | Write-Host
    $api=(Invoke-Android -Arguments @('shell','getprop','ro.build.version.sdk') | Out-String).Trim()
    if([int]$api -ge 33){Invoke-Android -Arguments @('shell','pm','grant',$appPackage,'android.permission.POST_NOTIFICATIONS') | Write-Host}
    Invoke-Android -Arguments @('shell','appops','get',$appPackage,'android:mock_location') | Write-Host
    Invoke-Android -Arguments @('shell','am','start','-n',"$appPackage/.MainActivity") | Write-Host
    Write-Host 'Ready. Choose a route and speed in the app. Playback has not been started.'
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    Write-Host 'If ADB reports error: closed, enable ADB in BlueStacks Settings > Advanced. You can also install RouteGPS.apk manually using Install APK.'
    exit 1
}
