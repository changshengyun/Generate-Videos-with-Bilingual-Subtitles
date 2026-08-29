[Console]::OutputEncoding = [Text.Encoding]::UTF8
$adb = 'D:\DevData\Android\Sdk\platform-tools\adb.exe'
$tools = 'd:\DevEnv\Projects\lyric-captioner-android\tools'
Write-Output '=== tap generate_captions (610, 2058) ==='
& $adb shell input tap 610 2058
Start-Sleep -Seconds 3
Write-Output '=== ui status after tap ==='
& $adb shell uiautomator dump /sdcard/ui-c.xml | Out-Null
& $adb pull /sdcard/ui-c.xml "$tools\ui-c.xml" | Out-Null
$xml = Get-Content -Raw -Encoding UTF8 "$tools\ui-c.xml"
$ts = [regex]::Matches($xml, 'text="([^"]{2,})"')
$seen = @{}
foreach ($t in $ts) {
  $v = $t.Groups[1].Value
  if (-not $seen.ContainsKey($v)) { $seen[$v] = 1; Write-Output ('text: ' + $v) }
}
