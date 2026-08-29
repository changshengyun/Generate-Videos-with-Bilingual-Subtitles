[Console]::OutputEncoding = [Text.Encoding]::UTF8
$xml = Get-Content -Raw -Encoding UTF8 d:\DevEnv\Projects\lyric-captioner-android\tools\device-ui-rerun.xml
$ms = [regex]::Matches($xml, 'content-desc="([^"]+)"')
$values = @()
foreach ($m in $ms) { if ($m.Groups[1].Value -ne '') { $values += $m.Groups[1].Value } }
$values | Select-Object -Unique | ForEach-Object { Write-Output $_ }
Write-Output '=== text 值（英文/中文）==='
$ts = [regex]::Matches($xml, 'text="([^"]{3,})"')
$seen = @{}
foreach ($m in $ts) {
  $v = $m.Groups[1].Value
  if (-not $seen.ContainsKey($v)) { $seen[$v] = 1; Write-Output $v }
}
