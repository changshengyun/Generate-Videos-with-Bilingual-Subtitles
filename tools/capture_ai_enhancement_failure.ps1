<#
.SYNOPSIS
Captures a bounded, privacy-scrubbed Android evidence window for AI subtitle enhancement.

.DESCRIPTION
Observes an already-running LyricCaptioner session. The script never starts, clicks,
installs, kills, or clears the app and never clears logcat. Each adb log line is
redacted in memory before a bounded file write. Ctrl+C enters the same finalization
path as normal completion and stops only logcat processes owned by this script.

.PARAMETER Serial
Optional adb serial. When omitted, exactly one online device must exist.

.PARAMETER Package
Target package. Defaults to com.example.lyriccaptioner.

.PARAMETER DurationSeconds
Capture duration, from 1 through 3600 seconds. Defaults to 300.

.PARAMETER OutputRoot
Parent of unique run directories. Defaults to the system temporary directory under
LyricCaptioner\ai-enhancement-logs, outside the repository.

.PARAMETER AnalyzeOnly
Rebuild summary.txt for the existing sanitized run directory supplied as OutputRoot.

.PARAMETER SelfTest
Runs offline synthetic classification, redaction, truncation, finalization, and
owned-process cleanup tests. It never invokes adb.

.EXAMPLE
.\tools\capture_ai_enhancement_failure.ps1

Captures for five minutes from the only online device.

.EXAMPLE
.\tools\capture_ai_enhancement_failure.ps1 -Serial 'DEVICE_SERIAL' -DurationSeconds 420

Captures from one selected device without persisting its serial.
#>
[CmdletBinding(DefaultParameterSetName = 'Capture')]
param(
    [Parameter(ParameterSetName = 'Capture')]
    [string]$Serial,

    [Parameter(ParameterSetName = 'Capture')]
    [Parameter(ParameterSetName = 'Analyze')]
    [string]$Package = 'com.example.lyriccaptioner',

    [Parameter(ParameterSetName = 'Capture')]
    [ValidateRange(1, 3600)]
    [int]$DurationSeconds = 300,

    [Parameter(ParameterSetName = 'Capture')]
    [Parameter(ParameterSetName = 'Analyze', Mandatory = $true)]
    [string]$OutputRoot,

    [Parameter(ParameterSetName = 'Analyze', Mandatory = $true)]
    [switch]$AnalyzeOnly,

    [Parameter(ParameterSetName = 'SelfTest', Mandatory = $true)]
    [switch]$SelfTest
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'
$script:DeviceSerialToRedact = ''
$script:MaximumLogBytes = 4MB

if (-not $OutputRoot -and -not $AnalyzeOnly -and -not $SelfTest) {
    $temporaryRoot = [IO.Path]::GetTempPath()
    $OutputRoot = Join-Path $temporaryRoot 'LyricCaptioner\ai-enhancement-logs'
}

if (-not ('LyricCaptioner.BoundedProcessCapture' -as [type])) {
    Add-Type -Language CSharp -TypeDefinition @'
using System;
using System.Diagnostics;
using System.IO;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;

namespace LyricCaptioner {
    public sealed class BoundedProcessCapture : IDisposable {
        private static readonly Regex Sensitive = new Regex(
            @"(?i)(\b(?:request|response|body|messages?|prompt|lyrics?|captions?|content|cookie|tokens?|authorization)\b|\bapi[-_ ]?key\b|\bbearer\s+\S+|\bsk-[A-Za-z0-9_-]{8,})",
            RegexOptions.Compiled);
        private static readonly Regex Secret = new Regex(
            @"(?i)(Bearer\s+)[A-Za-z0-9._~+/=-]+|\bsk-[A-Za-z0-9_-]{8,}|((?:api[-_ ]?key|token|secret|cookie|authorization)\s*[:=]\s*)[^\s,;\}\]]+",
            RegexOptions.Compiled);
        private static readonly Regex Uri = new Regex(@"(?i)\b(?:content|file)://[^\s""'\]\)]+", RegexOptions.Compiled);
        private static readonly Regex WinQuoted = new Regex("(?i)(\"[A-Z]:\\\\[^\"]+\"|'[A-Z]:\\\\[^']+')", RegexOptions.Compiled);
        private static readonly Regex WinPath = new Regex(@"(?i)\b[A-Z]:\\[^\r\n,;]+", RegexOptions.Compiled);
        private static readonly Regex DevicePath = new Regex(@"(?i)/(?:storage/emulated/\d+|sdcard)/[^\s""'\]\)]+", RegexOptions.Compiled);

        private readonly object gate = new object();
        private readonly long maximumBytes;
        private readonly string serial;
        private StreamWriter writer;
        private Process process;
        private Task stdoutTask;
        private Task stderrTask;
        private long writtenBytes;
        private bool truncated;
        private bool sensitiveBlock;
        private int sensitiveDepth;

        public BoundedProcessCapture(string executable, string arguments, string outputPath, string deviceSerial, long maxBytes) {
            maximumBytes = maxBytes;
            serial = deviceSerial ?? String.Empty;
            writer = new StreamWriter(outputPath, false, new UTF8Encoding(false));
            writer.AutoFlush = true;
            var info = new ProcessStartInfo(executable, arguments) {
                UseShellExecute = false,
                CreateNoWindow = true,
                RedirectStandardOutput = true,
                RedirectStandardError = true
            };
            process = new Process { StartInfo = info };
            if (!process.Start()) throw new InvalidOperationException("Unable to start owned capture process.");
            stdoutTask = Task.Factory.StartNew(() => ReadLoop(process.StandardOutput, false), TaskCreationOptions.LongRunning);
            stderrTask = Task.Factory.StartNew(() => ReadLoop(process.StandardError, true), TaskCreationOptions.LongRunning);
        }

        public int ProcessId { get { return process == null ? -1 : process.Id; } }
        public bool HasExited { get { try { return process == null || process.HasExited; } catch { return true; } } }
        public bool Truncated { get { return truncated; } }

        private void ReadLoop(StreamReader reader, bool isError) {
            string line;
            while ((line = reader.ReadLine()) != null) {
                WriteSanitized((isError ? "# adb stderr: " : String.Empty) + line);
            }
        }

        private static int DelimiterDelta(string value) {
            int result = 0;
            foreach (char c in value) {
                if (c == '{' || c == '[') result++;
                else if (c == '}' || c == ']') result--;
            }
            return result;
        }

        private string Sanitize(string line) {
            if (sensitiveBlock) {
                sensitiveDepth += DelimiterDelta(line);
                if (sensitiveDepth <= 0) { sensitiveBlock = false; sensitiveDepth = 0; }
                return "<REDACTED_SENSITIVE_LINE>";
            }
            if (Sensitive.IsMatch(line)) {
                int delta = DelimiterDelta(line);
                if (delta > 0) { sensitiveBlock = true; sensitiveDepth = delta; }
                return "<REDACTED_SENSITIVE_LINE>";
            }
            string value = line;
            if (!String.IsNullOrEmpty(serial)) value = Regex.Replace(value, Regex.Escape(serial), "[REDACTED_DEVICE_SERIAL]", RegexOptions.IgnoreCase);
            value = Secret.Replace(value, delegate(Match m) {
                if (m.Groups[1].Success) return m.Groups[1].Value + "[REDACTED_SECRET]";
                if (m.Groups[2].Success) return m.Groups[2].Value + "[REDACTED_SECRET]";
                return "[REDACTED_SECRET]";
            });
            value = Uri.Replace(value, "[REDACTED_URI]");
            value = WinQuoted.Replace(value, "[REDACTED_WINDOWS_PATH]");
            value = WinPath.Replace(value, "[REDACTED_WINDOWS_PATH]");
            value = DevicePath.Replace(value, "[REDACTED_DEVICE_PATH]");
            return value;
        }

        private void WriteSanitized(string rawLine) {
            string safe = Sanitize(rawLine);
            int bytes = Encoding.UTF8.GetByteCount(safe) + 2;
            lock (gate) {
                if (writer == null) return;
                if (writtenBytes + bytes > maximumBytes) {
                    if (!truncated) {
                        writer.WriteLine("<TRUNCATED_MAX_BYTES>");
                        truncated = true;
                    }
                    return;
                }
                writer.WriteLine(safe);
                writtenBytes += bytes;
            }
        }

        public void Stop() {
            Process owned = process;
            if (owned == null) return;
            try { if (!owned.HasExited) owned.Kill(); } catch { }
            try { owned.WaitForExit(5000); } catch { }
            try { Task.WaitAll(new [] { stdoutTask, stderrTask }, 5000); } catch { }
            lock (gate) {
                if (writer != null) { writer.Flush(); writer.Dispose(); writer = null; }
            }
        }

        public void Dispose() {
            Stop();
            if (process != null) { process.Dispose(); process = null; }
        }
    }

    public static class CaptureCancellation {
        private static volatile bool requested;
        private static ConsoleCancelEventHandler handler;
        public static bool Requested { get { return requested; } }
        public static void Install() {
            requested = false;
            handler = delegate(object sender, ConsoleCancelEventArgs args) { requested = true; args.Cancel = true; };
            Console.CancelKeyPress += handler;
        }
        public static void Uninstall() {
            if (handler != null) Console.CancelKeyPress -= handler;
            handler = null;
        }
    }
}
'@
}

function Protect-Text {
    param([AllowNull()][string]$Text)
    if ($null -eq $Text) { return '' }
    $safeLines = New-Object 'System.Collections.Generic.List[string]'
    $insideSensitive = $false
    $depth = 0
    foreach ($line in ($Text -split "`r?`n")) {
        $delta = ([regex]::Matches($line, '[\{\[]').Count - [regex]::Matches($line, '[\}\]]').Count)
        if ($insideSensitive) {
            $safeLines.Add('<REDACTED_SENSITIVE_LINE>')
            $depth += $delta
            if ($depth -le 0) { $insideSensitive = $false; $depth = 0 }
            continue
        }
        if ($line -match '(?i)(\b(?:request|response|body|messages?|prompt|lyrics?|captions?|content|cookie|tokens?|authorization)\b|\bapi[-_ ]?key\b|\bBearer\s+\S+|\bsk-[A-Za-z0-9_-]{8,})') {
            $safeLines.Add('<REDACTED_SENSITIVE_LINE>')
            if ($delta -gt 0) { $insideSensitive = $true; $depth = $delta }
            continue
        }
        $value = $line
        if ($script:DeviceSerialToRedact) {
            $value = [regex]::Replace($value, [regex]::Escape($script:DeviceSerialToRedact), '[REDACTED_DEVICE_SERIAL]', [Text.RegularExpressions.RegexOptions]::IgnoreCase)
        }
        $value = [regex]::Replace($value, '(?i)\b(?:content|file)://[^\s"''\]\)]+', '[REDACTED_URI]')
        $value = [regex]::Replace($value, '(?i)("[A-Z]:\\[^"]+"|''[A-Z]:\\[^'']+'')', '[REDACTED_WINDOWS_PATH]')
        $value = [regex]::Replace($value, '(?i)\b[A-Z]:\\[^\r\n,;]+', '[REDACTED_WINDOWS_PATH]')
        $value = [regex]::Replace($value, '(?i)/(?:storage/emulated/\d+|sdcard)/[^\s"''\]\)]+', '[REDACTED_DEVICE_PATH]')
        $safeLines.Add($value)
    }
    return $safeLines -join "`r`n"
}

function Write-SafeText {
    param([string]$Path, [AllowNull()][string]$Text)
    [IO.File]::WriteAllText($Path, (Protect-Text $Text), (New-Object Text.UTF8Encoding($false)))
}

function Write-CaptureStatus {
    param(
        [string]$Directory,
        [string]$Outcome,
        [hashtable]$Values
    )
    $status = [ordered]@{ outcome = $Outcome }
    foreach ($key in $Values.Keys) { $status[$key] = $Values[$key] }
    try {
        $json = $status | ConvertTo-Json -Depth 5
        # Status keys and values are controlled metadata. Error messages are never included.
        [IO.File]::WriteAllText((Join-Path $Directory 'capture-status.json'), $json, (New-Object Text.UTF8Encoding($false)))
    } catch {
        try { [IO.File]::WriteAllText((Join-Path $Directory 'capture-status-minimal.txt'), "outcome=$Outcome", (New-Object Text.UTF8Encoding($false))) } catch { }
    }
}

function Convert-DeviceTimestamp {
    param([string]$Line, [int]$Year, [string]$Offset)
    $match = [regex]::Match($Line, '(?<!\d)(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})(?!\d)')
    if (-not $match.Success) { return $null }
    $normalizedOffset = if ($Offset -match '^[+-]\d{4}$') { $Offset.Insert(3, ':') } else { '+00:00' }
    $parsed = [DateTimeOffset]::MinValue
    $value = "$Year-$($match.Groups[1].Value) $normalizedOffset"
    if ([DateTimeOffset]::TryParseExact($value, 'yyyy-MM-dd HH:mm:ss.fff zzz', [Globalization.CultureInfo]::InvariantCulture, [Globalization.DateTimeStyles]::None, [ref]$parsed)) {
        return $parsed
    }
    return $null
}

function Get-ExitInfoBlocks {
    param([string]$Text)
    return @([regex]::Split($Text, '(?m)(?=^\s*ApplicationExitInfo\s+#\d+:)') | Where-Object { $_ -match '(?m)^\s*ApplicationExitInfo\s+#\d+:' } | ForEach-Object { $_.Trim() })
}

function Get-NewExitInfo {
    param([string]$Baseline, [string]$Final)
    $known = @{}
    foreach ($block in (Get-ExitInfoBlocks $Baseline)) {
        $normalized = $block -replace '(?m)^\s*ApplicationExitInfo\s+#\d+:\s*', ''
        $known[$normalized] = $true
    }
    $newBlocks = New-Object 'System.Collections.Generic.List[string]'
    foreach ($block in (Get-ExitInfoBlocks $Final)) {
        $normalized = $block -replace '(?m)^\s*ApplicationExitInfo\s+#\d+:\s*', ''
        if (-not $known.ContainsKey($normalized)) { $newBlocks.Add($block) }
    }
    if ($newBlocks.Count -eq 0) { return 'No new exit-info record was added during this capture.' }
    return $newBlocks -join "`r`n`r`n"
}

function Get-LinePid {
    param([string]$Line)
    $thread = [regex]::Match($Line, '^\s*\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3}\s+(\d+)\s+(\d+)\s')
    if ($thread.Success) { return $thread.Groups[1].Value }
    $event = [regex]::Match($Line, '(?i)\bam_(?:crash|anr|kill)\b[^\r\n]*?[\[, ](\d{2,})[\], ]')
    if ($event.Success) { return $event.Groups[1].Value }
    return $null
}

function Get-RelatedEvidence {
    param([string]$Directory, [string]$AppPackage, [string[]]$ObservedPids, [int]$Year, [string]$Offset, [DateTimeOffset]$Start, [DateTimeOffset]$End)
    $result = New-Object 'System.Collections.Generic.List[object]'
    $escapedPackage = [regex]::Escape($AppPackage)
    $logFiles = @(Get-ChildItem -LiteralPath $Directory -File | Where-Object { $_.Name -like 'logcat-*.log' })
    foreach ($file in $logFiles) {
        foreach ($line in [IO.File]::ReadAllLines($file.FullName)) {
            $time = Convert-DeviceTimestamp $line $Year $Offset
            if ($null -eq $time -or $time -lt $Start -or $time -gt $End) { continue }
            $lineProcessId = Get-LinePid $line
            $related = $line -match $escapedPackage -or ($lineProcessId -and $ObservedPids -contains $lineProcessId)
            if ($related) { $result.Add([pscustomobject]@{ Time = $time; Line = $line; File = $file.Name; Pid = $lineProcessId }) }
        }
    }
    $exitPath = Join-Path $Directory 'exit-info-new.txt'
    if (Test-Path -LiteralPath $exitPath) {
        foreach ($line in [IO.File]::ReadAllLines($exitPath)) {
            if ($line -match '(?i)No new exit-info') { continue }
            $time = [DateTimeOffset]::MinValue
            $timeMatch = [regex]::Match($line, '(?i)timestamp\s*=\s*(.+)$')
            if ($timeMatch.Success) { [DateTimeOffset]::TryParse($timeMatch.Groups[1].Value.Trim(), [ref]$time) | Out-Null }
            if ($time -eq [DateTimeOffset]::MinValue) { $time = $End }
            $result.Add([pscustomobject]@{ Time = $time; Line = $line; File = 'exit-info-new.txt'; Pid = $null })
        }
    }
    return @($result | Sort-Object Time)
}

function New-EvidenceSummary {
    param([string]$Directory, [string]$AppPackage)
    if (-not (Test-Path -LiteralPath $Directory -PathType Container)) { throw 'AnalyzeOnly directory does not exist.' }
    $statusPath = Join-Path $Directory 'capture-status.json'
    $status = $null
    if (Test-Path -LiteralPath $statusPath) { try { $status = Get-Content -Raw -LiteralPath $statusPath | ConvertFrom-Json } catch { } }
    $props = if ($status) { @($status.PSObject.Properties.Name) } else { @() }
    $pids = if ($status -and $props -contains 'observedPids') { @($status.observedPids | ForEach-Object { "$_" }) } else { @() }
    $start = if ($status -and $props -contains 'deviceStartIso') { [DateTimeOffset]::Parse($status.deviceStartIso) } else { [DateTimeOffset]::MinValue }
    $end = if ($status -and $props -contains 'deviceEndIso') { [DateTimeOffset]::Parse($status.deviceEndIso) } else { [DateTimeOffset]::MaxValue }
    $year = if ($status -and $props -contains 'deviceYear') { [int]$status.deviceYear } else { [DateTime]::UtcNow.Year }
    $offset = if ($status -and $props -contains 'deviceUtcOffset') { "$($status.deviceUtcOffset)" } else { '+0000' }
    $related = @(Get-RelatedEvidence $Directory $AppPackage $pids $year $offset $start $end)

    $signals = New-Object 'System.Collections.Generic.List[object]'
    foreach ($item in $related) {
        $line = $item.Line
        $category = $null
        if ($line -match '(?i)OutOfMemoryError|Failed to allocate|lowmemorykiller|\blmkd\b|am_low_memory|REASON_LOW_MEMORY|low memory kill') { $category = 'OOM_OR_LMK' }
        elseif ($line -match '(?i)Fatal signal\s+\d+|signal\s+\d+\s+\(SIG|backtrace:|tombstone|REASON_CRASH_NATIVE') { $category = 'NATIVE_CRASH' }
        elseif ($line -match '(?i)\bANR in\b|am_anr|Input dispatching timed out|executing service.*timed out|REASON_ANR') { $category = 'ANR' }
        elseif ($line -match '(?i)\bHTTP\s+(?:4\d\d|5\d\d)\b|UnknownHostException|SocketTimeoutException|ConnectException|SSLHandshakeException|network.*(?:error|failed)|api.*(?:error|failed)') { $category = 'NETWORK_OR_API_ERROR' }
        elseif ($line -match '(?i)FATAL EXCEPTION|AndroidRuntime.*(?:Exception|Error)|REASON_CRASH') { $category = 'JAVA_KOTLIN_CRASH' }
        if ($category) { $signals.Add([pscustomobject]@{ Category = $category; Time = $item.Time; Line = $line; File = $item.File }) }
    }
    $groups = @($signals | Group-Object Category)
    $classification = 'UNKNOWN'
    if ($groups.Count -eq 1) { $classification = $groups[0].Name }
    elseif ($groups.Count -gt 1) {
        $exitText = ''
        $exitPath = Join-Path $Directory 'exit-info-new.txt'
        if (Test-Path -LiteralPath $exitPath) { $exitText = Get-Content -Raw -LiteralPath $exitPath }
        $exitCategories = @()
        if ($exitText -match '(?i)REASON_LOW_MEMORY|low memory') { $exitCategories += 'OOM_OR_LMK' }
        if ($exitText -match '(?i)REASON_CRASH_NATIVE|SIG(?:SEGV|ABRT|BUS|ILL)') { $exitCategories += 'NATIVE_CRASH' }
        if ($exitText -match '(?i)REASON_ANR') { $exitCategories += 'ANR' }
        if ($exitText -match '(?i)REASON_CRASH(?!_NATIVE)') { $exitCategories += 'JAVA_KOTLIN_CRASH' }
        $exitCategories = @($exitCategories | Select-Object -Unique | Where-Object { @($groups.Name) -contains $_ })
        if ($exitCategories.Count -eq 1) { $classification = $exitCategories[0] }
        else {
            $firstByCategory = @($groups | ForEach-Object { $_.Group | Sort-Object Time | Select-Object -First 1 } | Sort-Object Time)
            if ($firstByCategory.Count -gt 1 -and ($firstByCategory[1].Time - $firstByCategory[0].Time).TotalSeconds -gt 2) {
                $classification = $firstByCategory[0].Category
            } else { $classification = 'MULTIPLE_SIGNALS' }
        }
    }

    $processState = 'Unknown (minimal or missing status)'
    if ($status) {
        if ($props -contains 'pidChangeObserved' -and $status.pidChangeObserved) { $processState = "Process restarted; observed PID aliases: $(@($pids | ForEach-Object { 'pid-' + $_ }) -join ', ')" }
        elseif ($props -contains 'pidDisappearObserved' -and $status.pidDisappearObserved) { $processState = 'Process disappeared during the capture window' }
        elseif ($status.initialPid -and $status.finalPid -and "$($status.initialPid)" -eq "$($status.finalPid)") {
            $processState = "Alive with unchanged PID alias (pid-$($status.initialPid))"
            if ($classification -eq 'UNKNOWN') { $classification = 'PROCESS_ALIVE_NO_FAILURE_EVIDENCE' }
        } elseif ($status.initialPid -and -not $status.finalPid) { $processState = 'Process exited and was absent at capture end' }
    }
    $earliest = if ($signals.Count) { ($signals | Sort-Object Time | Select-Object -First 1).Time.ToString('o') } else { 'Not observed' }
    $firstSignal = if ($signals.Count) { Protect-Text (($signals | Sort-Object Time | Select-Object -First 1).Line) } else { 'Not observed' }
    $firstFrame = 'Not observed'
    foreach ($item in $related) { if ($item.Line -match ('at\s+' + [regex]::Escape($AppPackage) + '\.[^\r\n]+')) { $firstFrame = Protect-Text $Matches[0]; break } }
    $beforeKb = $null; $afterKb = $null
    foreach ($pair in @(@('meminfo-before.txt', 'beforeKb'), @('meminfo-after.txt', 'afterKb'))) {
        $path = Join-Path $Directory $pair[0]
        if (Test-Path -LiteralPath $path) {
            $m = [regex]::Match((Get-Content -Raw -LiteralPath $path), '(?m)^\s*TOTAL\s+(\d+)')
            if ($m.Success) { if ($pair[1] -eq 'beforeKb') { $beforeKb = [long]$m.Groups[1].Value } else { $afterKb = [long]$m.Groups[1].Value } }
        }
    }
    $memory = if ($null -ne $beforeKb -and $null -ne $afterKb) { "TOTAL PSS: $beforeKb KB -> $afterKb KB (delta $($afterKb-$beforeKb) KB)" } else { 'Unavailable' }
    $samplePath = Join-Path $Directory 'process-memory-samples.log'
    if (Test-Path -LiteralPath $samplePath) {
        $rss = @([regex]::Matches((Get-Content -Raw -LiteralPath $samplePath), '(?im)\bVmRSS:\s*(\d+)\s*kB') | ForEach-Object { [long]$_.Groups[1].Value })
        if ($rss.Count) { $memory += "; sampled peak VmRSS: $(($rss | Measure-Object -Maximum).Maximum) KB" }
    }
    $files = @(Get-ChildItem -LiteralPath $Directory -File | Where-Object Name -ne 'summary.txt' | Sort-Object Name | ForEach-Object Name)
    $categoryNames = if ($groups.Count) { @($groups.Name | Sort-Object) -join ', ' } else { 'No associated failure signals' }
    $outcome = if ($status -and $props -contains 'outcome') { $status.outcome } else { 'unknown' }
    $summary = @(
        'AI enhancement evidence summary', "Outcome: $outcome", "Classification: $classification",
        "Associated signal categories: $categoryNames", "Earliest associated signal time: $earliest",
        "First associated exception/signal: $firstSignal", "First app stack frame: $firstFrame",
        "Process state: $processState", "Memory change: $memory", "Evidence files: $($files -join ', ')", '',
        'Evidence boundary: only target-package or observed-PID/TID signals inside the exact device-time window can classify the target.',
        'Unrelated system/app lines are context only. UNKNOWN and MULTIPLE_SIGNALS are explicit evidence limits.'
    ) -join "`r`n"
    Write-SafeText (Join-Path $Directory 'summary.txt') $summary
    return $classification
}

function Resolve-Adb {
    $command = Get-Command adb -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    $candidate = Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'Android\Sdk\platform-tools\adb.exe'
    if (Test-Path -LiteralPath $candidate) { return $candidate }
    throw 'adb was not found.'
}

function Invoke-AdbText {
    param([string]$Adb, [string[]]$Prefix, [string[]]$Arguments, [switch]$AllowFailure)
    $output = & $Adb @Prefix @Arguments 2>&1 | Out-String
    $code = $LASTEXITCODE
    if (-not $AllowFailure -and $code -ne 0) { throw "adb command failed with exit code $code." }
    return $output.TrimEnd()
}

function ConvertTo-Arguments {
    param([string[]]$Values)
    return (($Values | ForEach-Object { if ($_ -match '[\s"]') { '"' + ($_ -replace '"', '\"') + '"' } else { $_ } }) -join ' ')
}

function Start-LogCapture {
    param([string]$Adb, [string[]]$Prefix, [string]$Buffer, [string]$Since, [string]$Path, [string]$DeviceSerial, [string]$ProcessId)
    $args = @($Prefix) + @('logcat', '-b', $Buffer, '-v', 'threadtime', '-T', $Since)
    if ($ProcessId) { $args += "--pid=$ProcessId" }
    $args += @('AndroidRuntime:V','ActivityManager:I','am_crash:I','am_anr:I','am_kill:I','lmkd:I','lowmemorykiller:I','libc:I','DEBUG:I','Choreographer:I','MainViewModel:V','WhisperSession:V','LyricCaptioner:V','*:S')
    return New-Object LyricCaptioner.BoundedProcessCapture($Adb, (ConvertTo-Arguments $args), $Path, $DeviceSerial, $script:MaximumLogBytes)
}

function Stop-OwnedCaptures {
    param([object[]]$Captures)
    foreach ($capture in @($Captures)) { if ($capture) { try { $capture.Dispose() } catch { } } }
}

function Invoke-SelfTest {
    $temp = Join-Path ([IO.Path]::GetTempPath()) ('lyriccaptioner-log-selftest-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $temp | Out-Null
    try {
        $script:DeviceSerialToRedact = 'SERIAL-PRIVATE-123'
        $sensitive = @(
            '08-14 12:00:00.000 100 100 D OkHttp: { "messages": [',
            '08-14 12:00:00.001 100 100 D OkHttp: { "role": "user",',
            '08-14 12:00:00.002 100 100 D OkHttp: "text": "private multiline" } ] }',
            '08-14 12:00:00.003 100 100 D Api: request body: private',
            '08-14 12:00:00.004 100 100 D Api: Cookie: a=one; b=two; token=x token=y',
            '08-14 12:00:00.005 100 100 D Api: Authorization: Bearer abc sk-123456789 content://private',
            'D:\Users\Alice Example\private file.txt SERIAL-PRIVATE-123'
        ) -join "`n"
        $redacted = Protect-Text $sensitive
        foreach ($leak in @('private multiline','request body','a=one','b=two','token=x','token=y','abc','sk-123456789','content://private','Alice Example','SERIAL-PRIVATE-123')) {
            if ($redacted.Contains($leak)) { throw "Redaction self-test leaked: $leak" }
        }
        if (([regex]::Matches($redacted, '<REDACTED_SENSITIVE_LINE>')).Count -lt 6) { throw 'Multiline sensitive block was not fail-closed.' }

        function New-Case([string]$Name, [string[]]$Lines, [hashtable]$State) {
            $dir = Join-Path $temp $Name; New-Item -ItemType Directory -Path $dir | Out-Null
            Write-SafeText (Join-Path $dir 'logcat-main.log') ($Lines -join "`n")
            $base = @{ outcome='completed'; deviceStartIso='2026-08-14T12:00:00+00:00'; deviceEndIso='2026-08-14T12:01:00+00:00'; deviceYear=2026; deviceUtcOffset='+0000'; initialPid='100'; finalPid='100'; observedPids=@('100'); pidChangeObserved=$false; pidDisappearObserved=$false }
            foreach ($key in $State.Keys) { $base[$key] = $State[$key] }
            Write-CaptureStatus $dir $base.outcome $base
            return $dir
        }
        $javaDir = New-Case 'java' @('08-14 12:00:01.100 100 100 E AndroidRuntime: FATAL EXCEPTION: main','08-14 12:00:01.200 100 100 E AndroidRuntime: at com.example.lyriccaptioner.Main.run(Main.kt:1)') @{}
        if ((New-EvidenceSummary $javaDir $Package) -ne 'JAVA_KOTLIN_CRASH') { throw 'Java classification failed.' }
        $anrDir = New-Case 'anr' @('08-14 12:00:02.000 100 100 I ActivityManager: ANR in com.example.lyriccaptioner') @{}
        if ((New-EvidenceSummary $anrDir $Package) -ne 'ANR') { throw 'ANR classification failed.' }
        $oomDir = New-Case 'oom' @('08-14 12:00:03.000 100 100 I lmkd: low memory kill com.example.lyriccaptioner') @{}
        if ((New-EvidenceSummary $oomDir $Package) -ne 'OOM_OR_LMK') { throw 'OOM classification failed.' }
        $nativeDir = New-Case 'native' @('08-14 12:00:04.000 100 100 F libc: Fatal signal 11 (SIGSEGV) com.example.lyriccaptioner') @{}
        if ((New-EvidenceSummary $nativeDir $Package) -ne 'NATIVE_CRASH') { throw 'Native classification failed.' }
        $networkDir = New-Case 'network' @('08-14 12:00:05.000 100 100 E MainViewModel: HTTP 503 failed') @{}
        if ((New-EvidenceSummary $networkDir $Package) -ne 'NETWORK_OR_API_ERROR') { throw 'Network classification failed.' }
        $unrelatedDir = New-Case 'unrelated' @('08-14 12:00:01.000 999 999 E AndroidRuntime: FATAL EXCEPTION: main other.app') @{}
        if ((New-EvidenceSummary $unrelatedDir $Package) -ne 'PROCESS_ALIVE_NO_FAILURE_EVIDENCE') { throw 'Unrelated app crash contaminated classification.' }
        $unknownDir = New-Case 'unknown' @('08-14 12:00:01.000 999 999 I Other: harmless') @{ initialPid=$null; finalPid=$null; observedPids=@() }
        if ((New-EvidenceSummary $unknownDir $Package) -ne 'UNKNOWN') { throw 'Unknown classification failed.' }
        $restartDir = New-Case 'restart' @('08-14 12:00:01.000 100 100 I MainViewModel: started','08-14 12:00:05.000 200 200 I MainViewModel: restarted') @{ finalPid='200'; observedPids=@('100','200'); pidChangeObserved=$true; pidDisappearObserved=$true }
        New-EvidenceSummary $restartDir $Package | Out-Null
        if ((Get-Content -Raw (Join-Path $restartDir 'summary.txt')) -notmatch 'restarted') { throw 'PID restart summary failed.' }
        $mixedDir = New-Case 'mixed' @('08-14 12:00:09.000 100 100 E AndroidRuntime: FATAL EXCEPTION: main','08-14 12:00:09.500 100 100 F libc: Fatal signal 11 (SIGSEGV)') @{}
        if ((New-EvidenceSummary $mixedDir $Package) -ne 'MULTIPLE_SIGNALS') { throw 'Mixed classification failed.' }
        if ((Get-Content -Raw (Join-Path $mixedDir 'summary.txt')) -notmatch '12:00:09.000') { throw 'True earliest signal failed.' }

        $baseline = "ApplicationExitInfo #0:`ntimestamp=2026-08-14T11:00:00+00:00`nreason=OLD"
        $final = "ApplicationExitInfo #0:`ntimestamp=2026-08-14T12:00:10+00:00`nreason=NEW`nApplicationExitInfo #1:`ntimestamp=2026-08-14T11:00:00+00:00`nreason=OLD"
        $delta = Get-NewExitInfo $baseline $final
        if ($delta -notmatch 'reason=NEW' -or $delta -match 'reason=OLD') { throw 'Exit-info baseline diff failed.' }

        foreach ($outcome in @('cancelled','error')) {
            $dir = Join-Path $temp $outcome; New-Item -ItemType Directory -Path $dir | Out-Null
            Write-CaptureStatus $dir $outcome @{ errorType = if ($outcome -eq 'error') { 'SyntheticError' } else { $null } }
            $parsed = Get-Content -Raw (Join-Path $dir 'capture-status.json') | ConvertFrom-Json
            if ($parsed.outcome -ne $outcome) { throw "$outcome minimal status failed." }
        }

        $fakeOutput = Join-Path $temp 'fake-process.log'
        $fakeArgs = '-NoProfile -Command "1..200 | ForEach-Object { Write-Output (''token=secret '' + (''x'' * 80)) }; Start-Sleep -Seconds 30"'
        $owned = New-Object LyricCaptioner.BoundedProcessCapture('powershell.exe', $fakeArgs, $fakeOutput, '', 256)
        Start-Sleep -Milliseconds 700
        $owned.Stop()
        if (-not $owned.HasExited) { throw 'Owned process cleanup failed.' }
        $owned.Dispose()
        $fakeText = Get-Content -Raw $fakeOutput
        if ($fakeText -notmatch '<TRUNCATED_MAX_BYTES>' -or $fakeText -match 'secret') { throw 'Bounded truncation or streaming redaction failed.' }

        $analyze = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $PSCommandPath -AnalyzeOnly -OutputRoot $unrelatedDir 2>&1 | Out-String
        if ($LASTEXITCODE -ne 0 -or $analyze -notmatch 'PROCESS_ALIVE_NO_FAILURE_EVIDENCE') { throw 'AnalyzeOnly counterexample failed.' }
        Write-Host 'SELFTEST PASS: privacy, association, time, classification, exit delta, truncation, finalize, and owned cleanup.'
    } finally {
        if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp -Recurse -Force }
    }
}

if ($SelfTest) { Invoke-SelfTest; exit 0 }
if ($AnalyzeOnly) { $result = New-EvidenceSummary ([IO.Path]::GetFullPath($OutputRoot)) $Package; Write-Host "Analysis complete. Classification: $result"; exit 0 }

$runDirectory = $null
$captures = @()
$outcome = 'error'
$errorType = $null
$runStartHost = [DateTimeOffset]::UtcNow
$deviceStart = $null
$deviceEnd = $null
$initialPid = ''
$finalPid = ''
$lastPid = ''
$pidChange = $false
$pidDisappear = $false
$observedPids = New-Object 'System.Collections.Generic.List[string]'
$samples = New-Object 'System.Collections.Generic.List[string]'
$exitBaseline = ''
$truncatedBuffers = @()

try {
    $adb = Resolve-Adb
    $deviceLines = & $adb devices 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'adb devices failed.' }
    $online = @($deviceLines | Select-String '^([^\s]+)\s+device$' | ForEach-Object { $_.Matches[0].Groups[1].Value })
    if ($Serial) {
        if ($online -notcontains $Serial) { throw 'Requested adb serial is not online.' }
        $prefix = @('-s', $Serial)
    } else {
        if ($online.Count -ne 1) { throw "Exactly one online adb device is required; found $($online.Count)." }
        $prefix = @('-s', $online[0])
    }
    $script:DeviceSerialToRedact = $prefix[1]
    $root = [IO.Path]::GetFullPath($OutputRoot)
    New-Item -ItemType Directory -Path $root -Force | Out-Null
    $runName = 'ai-enhancement-' + $runStartHost.ToString('yyyyMMddTHHmmssfffZ') + '-' + [guid]::NewGuid().ToString('N').Substring(0,8)
    $runDirectory = Join-Path $root $runName
    New-Item -ItemType Directory -Path $runDirectory | Out-Null
    Write-CaptureStatus $runDirectory 'running' @{ hostStartUtc=$runStartHost.ToString('o') }

    $deviceStartText = Invoke-AdbText $adb $prefix @('shell','date','+%Y-%m-%dT%H:%M:%S%z')
    $deviceStart = [DateTimeOffset]::ParseExact($deviceStartText, 'yyyy-MM-ddTHH:mm:sszzz', [Globalization.CultureInfo]::InvariantCulture)
    $deviceSince = Invoke-AdbText $adb $prefix @('shell','date','+%m-%d %H:%M:%S.000')
    $initialPid = (Invoke-AdbText $adb $prefix @('shell','pidof','-s',$Package) -AllowFailure).Trim()
    if ($initialPid -match '^\d+$') { $lastPid=$initialPid; $observedPids.Add($initialPid) }
    $exitBaseline = Invoke-AdbText $adb $prefix @('shell','dumpsys','activity','exit-info',$Package) -AllowFailure
    $metadata = @(
        "deviceStartIso=$($deviceStart.ToString('o'))", 'deviceAlias=[REDACTED_DEVICE_SERIAL]',
        "manufacturer=$(Invoke-AdbText $adb $prefix @('shell','getprop','ro.product.manufacturer') -AllowFailure)",
        "model=$(Invoke-AdbText $adb $prefix @('shell','getprop','ro.product.model') -AllowFailure)",
        "osRelease=$(Invoke-AdbText $adb $prefix @('shell','getprop','ro.build.version.release') -AllowFailure)",
        "sdk=$(Invoke-AdbText $adb $prefix @('shell','getprop','ro.build.version.sdk') -AllowFailure)",
        "abi=$(Invoke-AdbText $adb $prefix @('shell','getprop','ro.product.cpu.abi') -AllowFailure)",
        "package=$Package", (Invoke-AdbText $adb $prefix @('shell','dumpsys','package',$Package) -AllowFailure | Select-String 'versionCode=|versionName=' | ForEach-Object Line)
    ) -join "`r`n"
    Write-SafeText (Join-Path $runDirectory 'device-app-info.txt') $metadata
    Write-SafeText (Join-Path $runDirectory 'meminfo-before.txt') (Invoke-AdbText $adb $prefix @('shell','dumpsys','meminfo',$Package) -AllowFailure)
    foreach ($buffer in @('main','system','crash','events')) {
        $captures += Start-LogCapture $adb $prefix $buffer $deviceSince (Join-Path $runDirectory "logcat-$buffer.log") $prefix[1] ''
    }
    if ($initialPid -match '^\d+$') { $captures += Start-LogCapture $adb $prefix 'main' $deviceSince (Join-Path $runDirectory 'logcat-app-process.log') $prefix[1] $initialPid }

    [LyricCaptioner.CaptureCancellation]::Install()
    Write-Host '现在点击 AI 增强字幕'
    Write-Host "Capture window: $DurationSeconds seconds. Press Ctrl+C to finalize early."
    $deadline = [DateTime]::UtcNow.AddSeconds($DurationSeconds)
    $nextSample = [DateTime]::UtcNow
    while ([DateTime]::UtcNow -lt $deadline -and -not [LyricCaptioner.CaptureCancellation]::Requested) {
        if ([DateTime]::UtcNow -ge $nextSample) {
            $samplePid = (Invoke-AdbText $adb $prefix @('shell','pidof','-s',$Package)).Trim()
            $sampleTime = (Invoke-AdbText $adb $prefix @('shell','date','+%Y-%m-%dT%H:%M:%S%z'))
            if ($samplePid -match '^\d+$') {
                if ($lastPid -and $lastPid -ne $samplePid) { $pidChange = $true }
                if (-not $observedPids.Contains($samplePid)) { $observedPids.Add($samplePid) }
                $lastPid = $samplePid
                $proc = Invoke-AdbText $adb $prefix @('shell','cat',"/proc/$samplePid/status")
                $metrics = @($proc -split "`r?`n" | Where-Object { $_ -match '^(?:VmSize|VmPeak|VmRSS|VmHWM|Threads):' })
                $samples.Add("deviceTime=$sampleTime pid=$samplePid state=running $($metrics -join ' ')")
            } else {
                if ($lastPid) { $pidDisappear = $true }
                $samples.Add("deviceTime=$sampleTime state=not_running")
            }
            $nextSample = [DateTime]::UtcNow.AddSeconds(3)
        }
        Start-Sleep -Milliseconds 200
    }
    $outcome = if ([LyricCaptioner.CaptureCancellation]::Requested) { 'cancelled' } else { 'completed' }
} catch {
    $outcome = 'error'
    $errorType = $_.Exception.GetType().FullName
} finally {
    try { [LyricCaptioner.CaptureCancellation]::Uninstall() } catch { }
    Stop-OwnedCaptures $captures
    foreach ($capture in @($captures)) { if ($capture -and $capture.Truncated) { $truncatedBuffers += 'one-buffer' } }
    if ($runDirectory) {
        try {
            if ($deviceStart) {
                $deviceEndText = Invoke-AdbText $adb $prefix @('shell','date','+%Y-%m-%dT%H:%M:%S%z') -AllowFailure
                try { $deviceEnd = [DateTimeOffset]::ParseExact($deviceEndText, 'yyyy-MM-ddTHH:mm:sszzz', [Globalization.CultureInfo]::InvariantCulture) } catch { $deviceEnd = $deviceStart }
            }
            $finalPid = (Invoke-AdbText $adb $prefix @('shell','pidof','-s',$Package) -AllowFailure).Trim()
            if ($finalPid -match '^\d+$' -and -not $observedPids.Contains($finalPid)) { if ($lastPid -and $lastPid -ne $finalPid) { $pidChange=$true }; $observedPids.Add($finalPid) }
            Write-SafeText (Join-Path $runDirectory 'process-memory-samples.log') ($samples -join "`r`n")
            Write-SafeText (Join-Path $runDirectory 'meminfo-after.txt') (Invoke-AdbText $adb $prefix @('shell','dumpsys','meminfo',$Package) -AllowFailure)
            $exitFinal = Invoke-AdbText $adb $prefix @('shell','dumpsys','activity','exit-info',$Package) -AllowFailure
            Write-SafeText (Join-Path $runDirectory 'exit-info-new.txt') (Get-NewExitInfo $exitBaseline $exitFinal)
        } catch { if (-not $errorType) { $errorType = $_.Exception.GetType().FullName }; if ($outcome -eq 'completed') { $outcome='error' } }
        $statusValues = @{
            hostStartUtc=$runStartHost.ToString('o'); deviceStartIso=if($deviceStart){$deviceStart.ToString('o')}else{$null};
            deviceEndIso=if($deviceEnd){$deviceEnd.ToString('o')}elseif($deviceStart){$deviceStart.ToString('o')}else{$null};
            deviceYear=if($deviceStart){$deviceStart.Year}else{$null}; deviceUtcOffset=if($deviceStart){$deviceStart.ToString('zzz').Replace(':','')}else{$null};
            initialPid=if($initialPid -match '^\d+$'){$initialPid}else{$null}; finalPid=if($finalPid -match '^\d+$'){$finalPid}else{$null};
            observedPids=@($observedPids); pidChangeObserved=$pidChange; pidDisappearObserved=$pidDisappear;
            truncatedBufferCount=$truncatedBuffers.Count; errorType=$errorType
        }
        Write-CaptureStatus $runDirectory $outcome $statusValues
        try { $classification = New-EvidenceSummary $runDirectory $Package; Write-Host "Capture finalized. Outcome: $outcome. Classification: $classification" } catch { Write-Host "Capture finalized. Outcome: $outcome. Summary unavailable." }
        Write-Host "Evidence directory name: $(Split-Path -Leaf $runDirectory)"
    }
}

if ($outcome -eq 'error') { exit 1 }
