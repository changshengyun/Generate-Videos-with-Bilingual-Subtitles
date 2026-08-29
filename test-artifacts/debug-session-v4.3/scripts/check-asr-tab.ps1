[Console]::OutputEncoding = [Text.Encoding]::UTF8
$adb = 'D:\DevData\Android\Sdk\platform-tools\adb.exe'
$tools = 'd:\DevEnv\Projects\lyric-captioner-android\tools'
& $adb shell uiautomator dump /sdcard/ui-a.xml | Out-Null
& $adb pull /sdcard/ui-a.xml "$tools\ui-a.xml" | Out-Null
$xml = Get-Content -Raw -Encoding UTF8 "$tools\ui-a.xml"
$m = [regex]::Match($xml, '<node[^>]*?content-desc="workbench_asr"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
if (-not $m.Success) {
  $m = [regex]::Match($xml, '<node[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*?content-desc="workbench_asr"')
}
if ($m.Success) {
  $x = [int](([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2)
  $y = [int](([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2)
  & $adb shell input tap $x $y
  Start-Sleep -Seconds 2
}
& $adb shell uiautomator dump /sdcard/ui-b.xml | Out-Null
& $adb pull /sdcard/ui-b.xml "$tools\ui-b.xml" | Out-Null
& $adb shell screencap -p /sdcard/screen-asr.png | Out-Null
& $adb pull /sdcard/screen-asr.png "$tools\device-screen-asrtab.png" | Out-Null
$xml2 = Get-Content -Raw -Encoding UTF8 "$tools\ui-b.xml"
Write-Output '=== generate_captions node raw ==='
$gm = [regex]::Matches($xml2, '<node[^>]*generate_captions[^>]*>')
foreach ($g in $gm) { Write-Output $g.Value }
Write-Output '=== all text ==='
$ts = [regex]::Matches($xml2, 'text="([^"]{2,})"')
$seen = @{}
foreach ($t in $ts) {
  $v = $t.Groups[1].Value
  if (-not $seen.ContainsKey($v)) { $seen[$v] = 1; Write-Output ('text: ' + $v) }
}
Write-Output '=== caption_state ==='
$cs = [regex]::Match($xml2, 'content-desc="(caption_state:[^"]*)"')
if ($cs.Success) { Write-Output $cs.Groups[1].Value.Substring(0, [Math]::Min(400, $cs.Groups[1].Value.Length)) }
