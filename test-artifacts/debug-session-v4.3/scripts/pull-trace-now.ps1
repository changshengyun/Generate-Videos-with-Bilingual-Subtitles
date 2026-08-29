[Console]::OutputEncoding = [Text.Encoding]::UTF8
$adb = 'D:\DevData\Android\Sdk\platform-tools\adb.exe'
$tools = 'd:\DevEnv\Projects\lyric-captioner-android\tools'
Write-Output '=== trace mtime ==='
& $adb shell run-as com.example.lyriccaptioner stat -c '%y' cache/ai-trace.jsonl
Write-Output '=== pull trace ==='
& $adb shell run-as com.example.lyriccaptioner cat cache/ai-trace.jsonl | Out-File -Encoding utf8 "$tools\device-trace-newrun.jsonl"
Get-Item "$tools\device-trace-newrun.jsonl" | Select-Object Length, LastWriteTime
Write-Output '=== cache dir listing ==='
& $adb shell run-as com.example.lyriccaptioner ls cache
