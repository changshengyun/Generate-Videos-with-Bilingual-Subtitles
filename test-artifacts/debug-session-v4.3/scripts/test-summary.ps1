[Console]::OutputEncoding = [Text.Encoding]::UTF8
$dir = 'd:\DevEnv\Projects\lyric-captioner-android\app\build\test-results\testDebugUnitTest'
$total = 0; $fail = 0; $skip = 0; $suites = 0; $latest = [datetime]::MinValue
Get-ChildItem $dir -Filter *.xml | ForEach-Object {
    if ($_.LastWriteTime -gt $latest) { $latest = $_.LastWriteTime }
    $x = [xml](Get-Content $_.FullName -Raw)
    $suites++
    $total += [int]$x.testsuite.tests
    $fail += [int]$x.testsuite.failures + [int]$x.testsuite.errors
    $skip += [int]$x.testsuite.skipped
    if ([int]$x.testsuite.failures -gt 0 -or [int]$x.testsuite.errors -gt 0) {
        Write-Output "FAIL SUITE: $($x.testsuite.name)"
    }
}
Write-Output "suites=$suites total=$total failed=$fail skipped=$skip resultTime=$latest"
Write-Output "apk time: $((Get-Item 'd:\DevEnv\Projects\lyric-captioner-android\app\build\outputs\apk\debug\app-debug.apk').LastWriteTime)"
