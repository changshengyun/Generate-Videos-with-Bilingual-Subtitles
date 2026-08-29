[Console]::OutputEncoding = [Text.Encoding]::UTF8
$adb = 'D:\DevData\Android\Sdk\platform-tools\adb.exe'
& $adb -s fcf4b0cb logcat -c
& $adb -s fcf4b0cb logcat -v time > d:\DevEnv\Projects\lyric-captioner-android\test-artifacts\device-capture\live-run-logcat.txt
