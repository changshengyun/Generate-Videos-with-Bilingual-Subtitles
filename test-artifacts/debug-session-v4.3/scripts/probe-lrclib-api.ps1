# Read-only probe of the LRCLIB public API to enumerate supported query parameters.
$ErrorActionPreference = "Continue"
$headers = @{ "User-Agent" = "LyricCaptioner/0.1.0 probe" }

function Probe($label, $url) {
    Write-Output "=== $label ==="
    Write-Output "URL: $url"
    try {
        $resp = Invoke-WebRequest -Uri $url -Headers $headers -TimeoutSec 20 -UseBasicParsing
        Write-Output "HTTP $($resp.StatusCode)"
        $text = $resp.Content
        if ($text.Length -gt 1200) { $text = $text.Substring(0, 1200) + "...[truncated]" }
        Write-Output $text
    } catch {
        Write-Output "ERROR: $($_.Exception.Message)"
        if ($_.Exception.Response) {
            Write-Output ("HTTP " + [int]$_.Exception.Response.StatusCode)
        }
    }
    Write-Output ""
}

# 1. Free-text lyric search (the q parameter we already use)
Probe "q= single lyric line (Eyes On Me)" "https://lrclib.net/api/search?q=Take%20your%20eyes%20off%20of%20me%20so%20I%20can%20leave"

# 2. Metadata search by track name + artist
Probe "track_name + artist_name" "https://lrclib.net/api/search?track_name=Eyes%20On%20Me&artist_name=Faye%20Wong"

# 3. Exact-match get endpoint (metadata + duration based)
Probe "api/get with track/artist/album/duration" "https://lrclib.net/api/get?track_name=Eyes%20On%20Me&artist_name=Faye%20Wong&album_name=&duration=330"

# 4. Probe for hidden/undocumented scoring params: do they change the response?
Probe "search with q plus unknown param match_lyrics=true" "https://lrclib.net/api/search?q=Take%20your%20eyes%20off%20of%20me&match_lyrics=true&fuzzy=true&limit=3"
