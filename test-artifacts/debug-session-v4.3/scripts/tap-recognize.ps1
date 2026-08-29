[Console]::OutputEncoding = [Text.Encoding]::UTF8
$adb = 'D:\DevData\Android\Sdk\platform-tools\adb.exe'
$tools = 'd:\DevEnv\Projects\lyric-captioner-android\tools'

function Dump-Ui {
  & $adb shell uiautomator dump /sdcard/ui-tmp.xml | Out-Null
  & $adb pull /sdcard/ui-tmp.xml "$tools\ui-tmp.xml" | Out-Null
  Get-Content -Raw -Encoding UTF8 "$tools\ui-tmp.xml"
}

function Find-Bounds {
  param($xml, $pattern)
  $m = [regex]::Matches($xml, '<node[^>]*?(?:text="' + $pattern + '"|content-desc="' + $pattern + '")[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
  if ($m.Count -eq 0) {
    $m = [regex]::Matches($xml, '<node[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*?(?:text="' + $pattern + '"|content-desc="' + $pattern + '")')
  }
  if ($m.Count -eq 0) { return $null }
  $g = $m[0].Groups
  $x = [int](([int]$g[1].Value + [int]$g[3].Value) / 2)
  $y = [int](([int]$g[2].Value + [int]$g[4].Value) / 2)
  return "$x $y"
}

Write-Output '=== step1+2: scroll to top until workbench_asr visible ==='
$p = $null
for ($i = 0; $i -lt 6; $i++) {
  & $adb shell input swipe 610 1500 610 2600 200
  Start-Sleep -Milliseconds 800
  $xml = Dump-Ui
  $p = Find-Bounds $xml 'workbench_asr'
  Write-Output ("attempt " + ($i + 1) + " center: " + $p)
  if ($p) { break }
}
if (-not $p) {
  Copy-Item "$tools\ui-tmp.xml" "$tools\device-ui-after-scroll.xml" -Force
  Write-Output '!!! tab not found after scroll'
  exit 1
}
$xy = $p -split ' '
& $adb shell input tap $xy[0] $xy[1]
Start-Sleep -Seconds 2

Write-Output '=== step3: find + tap generate_captions ==='
$xml = Dump-Ui
$p = Find-Bounds $xml 'generate_captions'
Write-Output ("generate_captions center: " + $p)
if (-not $p) {
  Copy-Item "$tools\ui-tmp.xml" "$tools\device-ui-asrtab.xml" -Force
  Write-Output '!!! generate_captions not found'
  exit 1
}
$xy = $p -split ' '
& $adb shell input tap $xy[0] $xy[1]
Write-Output ('tapped generate_captions at ' + $p + ' -> workflow started')
