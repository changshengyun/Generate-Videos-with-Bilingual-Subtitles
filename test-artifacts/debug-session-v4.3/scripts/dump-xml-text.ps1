[Console]::OutputEncoding = [Text.Encoding]::UTF8
$xml = Get-Content -Raw -Encoding UTF8 d:\DevEnv\Projects\lyric-captioner-android\tools\device-ui-after-scroll.xml
$ms = [regex]::Matches($xml, 'content-desc="([^"]+)"')
foreach ($m in $ms) { if ($m.Groups[1].Value -ne '') { Write-Output ('desc: ' + $m.Groups[1].Value) } }
$ts = [regex]::Matches($xml, 'text="([^"]{2,})"')
$seen = @{}
foreach ($m in $ts) {
  $v = $m.Groups[1].Value
  if (-not $seen.ContainsKey($v)) { $seen[$v] = 1; Write-Output ('text: ' + $v) }
}
