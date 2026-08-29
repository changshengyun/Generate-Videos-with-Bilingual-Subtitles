# Second probe: does q= search the lyric body at all?
$ErrorActionPreference = "Continue"
$headers = @{ "User-Agent" = "LyricCaptioner/0.1.0 probe" }

function Probe($label, $url) {
    Write-Output "=== $label ==="
    try {
        $resp = Invoke-WebRequest -Uri $url -Headers $headers -TimeoutSec 20 -UseBasicParsing
        $text = $resp.Content
        if ($text.Length -gt 400) { $text = $text.Substring(0, 400) + "...[truncated]" }
        Write-Output "HTTP $($resp.StatusCode) -> $text"
    } catch {
        Write-Output "ERROR: $($_.Exception.Message)"
    }
    Write-Output ""
}

# Line that DEFINITELY exists inside the Eyes On Me lyric body we just fetched
Probe "q= exact line from known lyric body" "https://lrclib.net/api/search?q=Whenever%20sang%20my%20songs"
Probe "q= shorter unique phrase from lyric body" "https://lrclib.net/api/search?q=shyly%20placed%20your%20eyes%20on%20me"
# Title-ish word from the same song, as a control
Probe "q= song title words (control)" "https://lrclib.net/api/search?q=Eyes%20On%20Me%20Faye%20Wong"
