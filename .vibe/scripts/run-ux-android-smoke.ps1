param(
    [string]$AvdSerial = "emulator-5554",
    [string]$PackageName = "com.sedsoftware.sproutpace",
    [string]$ApkPath = "androidApp/build/outputs/apk/debug/androidApp-debug.apk",
    [ValidateSet("ru", "en")][string]$Locale = "ru",
    [switch]$ResetAppState
)

# Run only against a disposable emulator. ResetAppState explicitly authorizes fresh-install data clearing.
# UI Automator can verify focus and exposed bounds, but cannot prove spoken TalkBack output or visual insets.
$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "../..")).Path
$report = Join-Path $repo ("build/reports/ux-android-smoke/" + (Get-Date -Format "yyyyMMdd-HHmmss"))
New-Item -ItemType Directory -Force -Path $report | Out-Null
$checks = [ordered]@{}
@("fresh-blank-names", "browse-resume-setup", "keyboard-focus", "ime-entry", "three-step-setup",
    "recommendation-editor", "dirty-back-preserves-draft", "batch-save", "history-filter", "touch-targets",
    "talkback-spoken-order", "gesture-navigation", "visual-ime-and-system-insets", "font-scale-200",
    "process-death-draft", "contextual-tray-return") | ForEach-Object {
    $checks[$_] = @{ status = "UNVERIFIED"; detail = "Not reached by this bounded run" }
}
$phase = "environment"
$exitCode = 0
$evidenceIndex = 0

function Result([string]$Name, [string]$Status, [string]$Detail) {
    $checks[$Name] = @{ status = $Status; detail = $Detail }
}
function Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $output = & $script:adb -s $AvdSerial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "ADB failed ($LASTEXITCODE): $($Arguments -join ' ')`n$($output -join "`n")" }
    return ($output -join "`n")
}
function Label([string]$Key) {
    $entry = $script:strings.resources.string | Where-Object { $_.name -eq $Key } | Select-Object -First 1
    if ($null -eq $entry) { throw "Unknown production resource: $Key" }
    return $entry.InnerText
}
function Capture([string]$Name) {
    $script:evidenceIndex += 1
    $stem = "{0:D3}-{1}" -f $script:evidenceIndex, $Name
    Adb shell uiautomator dump /sdcard/sproutpace-ux.xml | Out-Null
    Adb pull /sdcard/sproutpace-ux.xml (Join-Path $report "$stem.xml") | Out-Null
    Adb -Arguments @("shell", "screencap", "-p", "/sdcard/sproutpace-ux.png") | Out-Null
    Adb pull /sdcard/sproutpace-ux.png (Join-Path $report "$stem.png") | Out-Null
    return [xml](Get-Content -Raw -LiteralPath (Join-Path $report "$stem.xml"))
}
function Matching([xml]$Ui, [string]$Text) {
    return @($Ui.SelectNodes("//node") | Where-Object {
        $_.GetAttribute("text") -eq $Text -or $_.GetAttribute("content-desc") -eq $Text
    })
}
function Node([xml]$Ui, [string]$Text) {
    $found = @(Matching $Ui $Text)
    if ($found.Count -eq 0) { throw "Expected visible node '$Text'" }
    return $found[0]
}
function Rect([System.Xml.XmlNode]$Node) {
    $match = [regex]::Match($Node.GetAttribute("bounds"), '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$')
    if (-not $match.Success) { throw "Invalid UI bounds" }
    return @(1..4 | ForEach-Object { [int]$match.Groups[$_].Value })
}
function Clickable([System.Xml.XmlNode]$Node) {
    while ($null -ne $Node -and $Node.Name -eq "node") {
        if ($Node.GetAttribute("clickable") -eq "true") { return $Node }
        $Node = $Node.ParentNode
    }
    throw "No clickable ancestor"
}
function Tap([System.Xml.XmlNode]$Node) {
    $target = Clickable $Node
    if ($target.GetAttribute("enabled") -ne "true") { throw "Target is disabled" }
    $r = Rect $target
    Adb shell input tap ([int](($r[0] + $r[2]) / 2)) ([int](($r[1] + $r[3]) / 2)) | Out-Null
    Start-Sleep -Milliseconds 500
}
function Seek([string]$Key) {
    for ($scroll = 0; $scroll -le 6; $scroll += 1) {
        $ui = Capture "seek-$Key-$scroll"
        $found = @(Matching $ui (Label $Key))
        if ($found.Count -gt 0) { return $found[0] }
        if ($scroll -lt 6) {
            Adb shell input swipe ([int]($script:width * .5)) ([int]($script:height * .75)) `
                ([int]($script:width * .5)) ([int]($script:height * .3)) 400 | Out-Null
        }
    }
    throw "Missing production label $Key after bounded swipe search"
}
function BlankName([xml]$Ui) {
    $fields = @($Ui.SelectNodes("//node[@class='android.widget.EditText']"))
    if ($fields.Count -eq 0) { throw "No editable name field" }
    # Compose can expose the floating label in the editable node's text even with an empty value.
    $text = $fields[0].GetAttribute("text")
    $allowed = @("", (Label "field_zone_name"), (Label "field_light_zone"), (Label "field_tray_name"))
    if ($text -notin $allowed) { throw "Name field contains unexpected default '$text'" }
    return $fields[0]
}
function EnterName([System.Xml.XmlNode]$Field, [string]$Text) {
    Tap $Field
    Adb shell input text $Text | Out-Null
    $ui = Capture "name-ime-open"
    Node $ui $Text | Out-Null
    if (@($ui.SelectNodes("//node[@class='android.widget.EditText' and @focused='true']")).Count -eq 0) {
        throw "Edited field does not have input focus"
    }
    $ime = Adb shell dumpsys input_method
    $ime | Set-Content -LiteralPath (Join-Path $report "ime-open.txt") -Encoding utf8
    if ($ime -match 'mInputShown=true|isInputViewShown=true|mIsInputViewShown=true') {
        Result "ime-entry" "PASS" "Typed value and focused editor observed; IME reports shown"
    } else { Result "ime-entry" "UNVERIFIED" "Value/focus observed; IME visibility flag not recognized, inspect evidence" }
    Adb shell input keyevent KEYCODE_BACK | Out-Null
}

try {
    $properties = Get-Content -Raw -LiteralPath (Join-Path $repo "local.properties")
    $sdkMatch = [regex]::Match($properties, '(?m)^sdk\.dir=(.+)$')
    if (-not $sdkMatch.Success) { throw "Missing Android SDK path" }
    $sdk = $sdkMatch.Groups[1].Value.Trim().Replace('\:', ':').Replace('\\', '\')
    $script:adb = Join-Path $sdk "platform-tools/adb.exe"
    $resourceDir = if ($Locale -eq "ru") { "values-ru" } else { "values" }
    $script:strings = [xml](Get-Content -Raw -LiteralPath (Join-Path $repo "shared/compose/src/commonMain/composeResources/$resourceDir/strings.xml"))
    if ((Adb get-state).Trim() -ne "device") { throw "Device is not ready" }
    Adb shell dumpsys accessibility | Set-Content -LiteralPath (Join-Path $report "accessibility.txt") -Encoding utf8
    $services = Adb shell settings get secure enabled_accessibility_services
    $services | Set-Content -LiteralPath (Join-Path $report "enabled-accessibility-services.txt") -Encoding utf8
    Result "talkback-spoken-order" "UNVERIFIED" "Enabled services: $services. No spoken traversal or TalkBack gestures performed."
    Adb shell dumpsys window | Set-Content -LiteralPath (Join-Path $report "window-before.txt") -Encoding utf8
    Adb shell getprop persist.sys.locale | Set-Content -LiteralPath (Join-Path $report "device-locale.txt") -Encoding utf8
    $size = [regex]::Matches((Adb shell wm size), '(?:Override|Physical) size:\s*(\d+)x(\d+)') | Select-Object -Last 1
    $script:width = [int]$size.Groups[1].Value
    $script:height = [int]$size.Groups[2].Value
    $density = [regex]::Matches((Adb shell wm density), '(?:Override|Physical) density:\s*(\d+)') | Select-Object -Last 1
    $minimumTarget = 48 * [int]$density.Groups[1].Value / 160
    $apk = (Resolve-Path -LiteralPath (Join-Path $repo $ApkPath)).Path
    Get-FileHash -LiteralPath $apk | Format-List | Out-String | Set-Content -LiteralPath (Join-Path $report "apk-sha256.txt")
    Adb -Arguments @("install", "-r", $apk) | Out-Null
    if ($ResetAppState) { Adb shell pm clear $PackageName | Out-Null }
    Adb shell am force-stop $PackageName | Out-Null
    Adb -Arguments @("shell", "am", "start", "-W", "-n", "$PackageName/.AppActivity") | Out-Null
    Start-Sleep -Milliseconds 800
    $ui = Capture "first-run"
    $phase = "fresh-blank-names"
    Node $ui (Label "screen_first_run_title") | Out-Null
    BlankName $ui | Out-Null
    Result $phase "PASS" "First setup name is empty in the exposed editable node"

    $phase = "browse-resume-setup"
    Tap (Node $ui (Label "action_browse_catalog"))
    $ui = Capture "catalog-skipped-setup"
    Tap (Node $ui (Label "nav_today"))
    Tap (Seek "action_resume_setup")
    $ui = Capture "setup-resumed"
    Node $ui (Label "first_run_step_zone_body") | Out-Null
    Result $phase "PASS" "Browse catalog to Today to resume opens setup"

    $phase = "keyboard-focus"
    $reached = $false
    for ($i = 0; $i -lt 24; $i += 1) {
        Adb shell input keyevent KEYCODE_TAB | Out-Null
        $ui = Capture "tab-$i"
        if (@($ui.SelectNodes("//node[@class='android.widget.EditText' and @focused='true']")).Count -gt 0) {
            $reached = $true; break
        }
    }
    if (-not $reached) { throw "Tab could not reach an editable field" }
    Result $phase "PASS" "Hardware Tab reached exposed editable field"
    EnterName (BlankName $ui) "RuntimeShelf"
    Tap (Seek "action_continue")
    $ui = Capture "tray-name-blank"
    EnterName (BlankName $ui) "RuntimeTray"
    Tap (Seek "action_continue")
    $phase = "three-step-setup"
    Tap (Seek "action_finish_setup")
    $ui = Capture "plan-after-setup"
    Node $ui (Label "screen_plan_title") | Out-Null
    Result $phase "PASS" "Explicit names accepted; setup opened Plan"

    $phase = "recommendation-editor"
    $suggestion = Seek "action_confirm_suggestion"
    $r = Rect (Clickable $suggestion)
    if (($r[2] - $r[0]) -lt $minimumTarget -or ($r[3] - $r[1]) -lt $minimumTarget) {
        throw "Suggestion target smaller than 48dp"
    }
    Result "touch-targets" "PASS" "Measured suggestion CTA only; not an app-wide target audit"
    Tap $suggestion
    $ui = Capture "suggestion-editor"
    Node $ui (Label "screen_batch_editor_title") | Out-Null
    Result $phase "PASS" "Suggestion opened reviewable batch editor"
    Result "batch-save" "UNVERIFIED" "Stopped before Save; inspect editor evidence and continue manually"
    Result "history-filter" "UNVERIFIED" "Requires a saved/completed batch; no synthetic history seeded"
    Result "gesture-navigation" "UNVERIFIED" "Real swipe search used; edge-back/predictive-back requires manual device check"
    Result "visual-ime-and-system-insets" "UNVERIFIED" "Screenshots retained; visual overlap and gesture-safe insets need review"
} catch {
    Result $phase "FAIL" $_.Exception.Message
    $exitCode = 1
    $_ | Out-String | Set-Content -LiteralPath (Join-Path $report "failure.txt") -Encoding utf8
} finally {
    $summary = [ordered]@{ emulator = $AvdSerial; package = $PackageName; locale = $Locale;
        checks = $checks; completedAt = (Get-Date).ToUniversalTime().ToString("o"); evidence = $report }
    $summary | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $report "summary.json") -Encoding utf8
    $summary | ConvertTo-Json -Depth 6 | Write-Output
}
exit $exitCode
