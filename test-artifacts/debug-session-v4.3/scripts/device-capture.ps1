[Console]::OutputEncoding = [Text.Encoding]::UTF8
$adb = 'D:\DevData\Android\Sdk\platform-tools\adb.exe'
$out = 'd:\DevEnv\Projects\lyric-captioner-android\test-artifacts\device-capture'
New-Item -ItemType Directory -Force -Path $out | Out-Null

# app process pid
$pidLine = & $adb -s fcf4b0cb shell pidof com.example.lyriccaptioner
Write-Output "pid=$pidLine"

# 1) app tag filtered log
& $adb -s fcf4b0cb logcat -d -v time MainViewModel:I WhisperSession:I FfmpegKitSubtitleExporter:I '*:S' > "$out\device-app-log.txt"
Write-Output "app-log lines: $((Get-Content "$out\device-app-log.txt").Count)"

# 2) recent events of interest across the whole buffer (last 3000 lines)
& $adb -s fcf4b0cb logcat -d -t 3000 > "$out\device-recent-full.txt"
Write-Output "recent-full saved"

Get-Content "$out\device-app-log.txt" | Select-Object -Last 60
