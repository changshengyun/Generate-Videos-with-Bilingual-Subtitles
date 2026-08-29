[Console]::OutputEncoding = [Text.Encoding]::UTF8
$adb = 'D:\DevData\Android\Sdk\platform-tools\adb.exe'
$tools = 'd:\DevEnv\Projects\lyric-captioner-android\tools'
& $adb shell uiautomator dump /sdcard/ui-now.xml | Out-Null
& $adb pull /sdcard/ui-now.xml "$tools\ui-now.xml" | Out-Null
& $adb shell screencap -p /sdcard/screen-now.png | Out-Null
& $adb pull /sdcard/screen-now.png "$tools\device-screen-now.png" | Out-Null
$xml = Get-Content -Raw -Encoding UTF8 "$tools\ui-now.xml"
$ms = [regex]::Matches($xml, 'content-desc="([^"]+)"')
foreach ($m in $ms) { if ($m.Groups[1].Value -ne '') { Write-Output ('desc: ' + $m.Groups[1].Value) } }
$ts = [regex]::Matches($xml, 'text="([^"]{2,})"')
$seen = @{}
foreach ($m in $ts) {
  $v = $m.Groups[1].Value
  if (-not $seen.ContainsKey($v)) { $seen[$v] = 1; Write-Output ('text: ' + $v) }
}
