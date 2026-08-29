[Console]::OutputEncoding = [Text.Encoding]::UTF8
$adb = 'D:\DevData\Android\Sdk\platform-tools\adb.exe'
$log = 'd:\DevEnv\Projects\lyric-captioner-android\test-artifacts\device-capture\live-run-logcat.txt'
$out = 'd:\DevEnv\Projects\lyric-captioner-android\test-artifacts\device-capture'

$deadline = (Get-Date).AddSeconds(240)
$done = $false
while ((Get-Date) -lt $deadline) {
    if (Test-Path $log) {
        $tail = Get-Content $log -Tail 400 -ErrorAction SilentlyContinue
        if ($tail -and ($tail | Select-String -Pattern 'workflow_finished|event=asr_completed|READY_FOR_EDIT' -Quiet)) {
            $done = $true
            break
        }
    }
    Start-Sleep -Seconds 3
}
if (-not $done) { Write-Output 'TIMEOUT: no recognition events seen in 240s'; exit 1 }

# give the workflow a moment to finish writing everything
Start-Sleep -Seconds 8
Write-Output 'recognition events detected, grabbing artifacts...'

# 1) fresh trace (raw bytes)
& $adb -s fcf4b0cb shell "run-as com.example.lyriccaptioner cat /data/data/com.example.lyriccaptioner/cache/ai-trace.jsonl > /data/local/tmp/ai-trace.jsonl"
& $adb -s fcf4b0cb pull /data/local/tmp/ai-trace.jsonl "$out\live-ai-trace.jsonl"
& $adb -s fcf4b0cb shell "rm /data/local/tmp/ai-trace.jsonl"

# 2) UI dump to read displayed caption text
& $adb -s fcf4b0cb shell "uiautomator dump /sdcard/ui-dump.xml" | Out-Null
& $adb -s fcf4b0cb pull /sdcard/ui-dump.xml "$out\live-ui-dump.xml" | Out-Null
& $adb -s fcf4b0cb shell "rm /sdcard/ui-dump.xml"

# 3) app log lines from the stream
Get-Content $log | Where-Object { $_ -match 'MainViewModel|WhisperSession|WhisperProcessSession|LyricCaptionerTrace' } > "$out\live-app-events.txt"

Write-Output '--- trace lines ---'
(Get-Content "$out\live-ai-trace.jsonl").Count
Write-Output '--- app event count ---'
(Get-Content "$out\live-app-events.txt").Count
Write-Output 'DONE'
