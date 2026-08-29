$apk = 'd:\DevEnv\Projects\lyric-captioner-android\app\build\outputs\apk\debug\app-debug.apk'
$item = Get-Item $apk
Write-Output ("APK: " + $item.LastWriteTime + " size=" + [Math]::Round($item.Length / 1MB, 1) + " MB")
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($apk)
$so = $zip.Entries | Where-Object { $_.FullName -like 'lib/*whisper*' }
foreach ($entry in $so) { Write-Output ("  " + $entry.FullName + " (" + [Math]::Round($entry.Length / 1MB, 1) + " MB)") }
$zip.Dispose()
$adb = 'D:\DevData\Android\Sdk\platform-tools\adb.exe'
& $adb devices
& $adb install -r $apk
