param(
    [string]$AvdSerial = "emulator-5554",
    [string]$PackageName = "com.sedsoftware.sproutpace",
    [string]$ActivityName = ".AppActivity",
    [string]$ApkPath = "androidApp/build/outputs/apk/debug/androidApp-debug.apk",
    [switch]$KeepAppState
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "../..")).Path
$localProperties = Get-Content -Raw -LiteralPath (Join-Path $repo "local.properties")
$sdkMatch = [regex]::Match($localProperties, '(?m)^sdk\.dir=(.+)$')
if (-not $sdkMatch.Success) { throw "local.properties does not define sdk.dir" }
$sdkRoot = $sdkMatch.Groups[1].Value.Trim().Replace('\:', ':').Replace('\\', '\')
$adb = Join-Path $sdkRoot "platform-tools/adb.exe"
if (-not (Test-Path -LiteralPath $adb)) { throw "ADB not found: $adb" }

$apk = (Resolve-Path -LiteralPath (Join-Path $repo $ApkPath)).Path
$reportDirectory = Join-Path $repo "build/reports/android-ui-smoke"
New-Item -ItemType Directory -Force -Path $reportDirectory | Out-Null

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $output = & $adb -s $AvdSerial @Arguments 2>&1
    $ErrorActionPreference = $previousErrorActionPreference
    if ($LASTEXITCODE -ne 0) {
        throw "ADB failed: $($Arguments -join ' ')`n$($output -join "`n")"
    }
    return ($output -join "`n")
}

function Dump-Ui {
    param([string]$Name)
    $remote = "/sdcard/sproutpace-$Name.xml"
    Invoke-Adb shell uiautomator dump $remote | Out-Null
    $local = Join-Path $reportDirectory "$Name.xml"
    Invoke-Adb pull $remote $local | Out-Null
    return [xml](Get-Content -Raw -LiteralPath $local)
}

function Nodes {
    param([xml]$Document)
    return @($Document.SelectNodes("//node"))
}

function Find-Node {
    param(
        [xml]$Document,
        [string]$Text = "",
        [string]$ContentDescription = "",
        [string]$ClassName = "",
        [int]$Index = 0
    )
    $matches = @(Nodes $Document | Where-Object {
        ($Text -eq "" -or $_.GetAttribute("text") -eq $Text) -and
        ($ContentDescription -eq "" -or $_.GetAttribute("content-desc") -eq $ContentDescription) -and
        ($ClassName -eq "" -or $_.GetAttribute("class") -eq $ClassName)
    })
    $node = if ($Index -lt $matches.Count) { $matches[$Index] } else { $null }
    if ($null -eq $node) {
        $description = if ($Text) { "text '$Text'" } elseif ($ContentDescription) {
            "content description '$ContentDescription'"
        } else { "class '$ClassName' at index $Index" }
        throw "Missing UI node with $description"
    }
    return $node
}

function Find-ClickableAncestor {
    param([System.Xml.XmlNode]$Node)
    $candidate = $Node
    while ($null -ne $candidate -and $candidate.Name -eq "node") {
        if ($candidate.GetAttribute("clickable") -eq "true") { return $candidate }
        $candidate = $candidate.ParentNode
    }
    throw "Node has no clickable ancestor"
}

function Bounds {
    param([System.Xml.XmlNode]$Node)
    $match = [regex]::Match($Node.GetAttribute("bounds"), '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$')
    if (-not $match.Success) { throw "Invalid node bounds: $($Node.GetAttribute('bounds'))" }
    return @(
        [int]$match.Groups[1].Value,
        [int]$match.Groups[2].Value,
        [int]$match.Groups[3].Value,
        [int]$match.Groups[4].Value
    )
}

function Tap-Node {
    param([System.Xml.XmlNode]$Node)
    $clickable = Find-ClickableAncestor $Node
    $bounds = Bounds $clickable
    Invoke-Adb shell input tap ([int](($bounds[0] + $bounds[2]) / 2)) ([int](($bounds[1] + $bounds[3]) / 2)) | Out-Null
}

function Assert-MinimumTarget {
    param(
        [System.Xml.XmlNode]$Node,
        [double]$MinimumPixels,
        [string]$Label
    )
    $clickable = Find-ClickableAncestor $Node
    $bounds = Bounds $clickable
    $width = $bounds[2] - $bounds[0]
    $height = $bounds[3] - $bounds[1]
    if ($width -lt $MinimumPixels -or $height -lt $MinimumPixels) {
        throw "$Label target is ${width}x${height}px; expected at least $([math]::Ceiling($MinimumPixels))px"
    }
}

function Assert-NodeOnScreen {
    param(
        [System.Xml.XmlNode]$Node,
        [int]$ScreenWidth,
        [int]$ScreenHeight,
        [string]$Label
    )
    $bounds = Bounds $Node
    if ($bounds[0] -lt 0 -or $bounds[1] -lt 0 -or $bounds[2] -gt $ScreenWidth -or
        $bounds[3] -gt $ScreenHeight -or $bounds[2] -le $bounds[0] -or $bounds[3] -le $bounds[1]) {
        throw "$Label is clipped or outside the screen: $($Node.GetAttribute('bounds'))"
    }
}

function Assert-KeyboardReachable {
    param(
        [string]$Text = "",
        [string]$ContentDescription = "",
        [string]$DumpPrefix,
        [int]$MaximumTabs = 24
    )
    for ($tabIndex = 1; $tabIndex -le $MaximumTabs; $tabIndex += 1) {
        Invoke-Adb shell input keyevent KEYCODE_TAB | Out-Null
        $document = Dump-Ui "$DumpPrefix-$tabIndex"
        $focusedNodes = @(Nodes $document | Where-Object { $_.GetAttribute("focused") -eq "true" })
        foreach ($focused in $focusedNodes) {
            $scope = @($focused) + @($focused.SelectNodes(".//node"))
            $match = $scope | Where-Object {
                ($Text -eq "" -or $_.GetAttribute("text") -eq $Text) -and
                ($ContentDescription -eq "" -or $_.GetAttribute("content-desc") -eq $ContentDescription)
            } | Select-Object -First 1
            if ($null -ne $match) { return }
        }
    }
    $description = if ($Text) { "text '$Text'" } else { "content description '$ContentDescription'" }
    throw "Keyboard Tab could not reach $description"
}

function Find-NodeAfterForwardScroll {
    param(
        [string]$Text = "",
        [string]$ContentDescription = "",
        [string]$DumpPrefix,
        [int]$MaximumScrolls = 6
    )
    for ($scrollIndex = 0; $scrollIndex -le $MaximumScrolls; $scrollIndex += 1) {
        $document = Dump-Ui "$DumpPrefix-$scrollIndex"
        $match = Nodes $document | Where-Object {
            ($Text -eq "" -or $_.GetAttribute("text") -eq $Text) -and
            ($ContentDescription -eq "" -or $_.GetAttribute("content-desc") -eq $ContentDescription)
        } | Select-Object -First 1
        if ($null -ne $match) { return $match }
        if ($scrollIndex -lt $MaximumScrolls) {
            Invoke-Adb shell input swipe 500 1800 500 650 450 | Out-Null
            Start-Sleep -Milliseconds 350
        }
    }
    $description = if ($Text) { "text '$Text'" } else { "content description '$ContentDescription'" }
    throw "Could not find $description after $MaximumScrolls forward scrolls"
}

function Start-App {
    Invoke-Adb shell am force-stop $PackageName | Out-Null
    for ($attempt = 1; $attempt -le 3; $attempt += 1) {
        Invoke-Adb -Arguments @("shell", "am", "start", "-W", "-n", "$PackageName/$ActivityName") | Out-Null
        Start-Sleep -Milliseconds 350
        $activities = Invoke-Adb shell dumpsys activity activities
        if ($activities -match "ResumedActivity.*$([regex]::Escape($PackageName))") { return }
    }
    throw "SproutPace did not become the resumed Android activity after three launch attempts"
}

$results = [ordered]@{}
$previousFontScale = $null
$externalBackupPath = "/sdcard/Download/sproutpace-backup.zip"
try {
    $device = Invoke-Adb get-state
    if ($device.Trim() -ne "device") { throw "Android device is not ready" }
    $previousFontScale = (Invoke-Adb shell settings get system font_scale).Trim()
    Invoke-Adb shell settings put system font_scale 1.0 | Out-Null
    Invoke-Adb -Arguments @("install", "-r", $apk) | Out-Null
    Invoke-Adb shell pm clear $PackageName | Out-Null
    Invoke-Adb -Arguments @(
        "shell", "pm", "clear-permission-flags", $PackageName,
        "android.permission.POST_NOTIFICATIONS", "user-set"
    ) | Out-Null
    Invoke-Adb -Arguments @(
        "shell", "pm", "clear-permission-flags", $PackageName,
        "android.permission.POST_NOTIFICATIONS", "user-fixed"
    ) | Out-Null
    Invoke-Adb shell pm revoke $PackageName android.permission.POST_NOTIFICATIONS | Out-Null
    Start-App

    $densityOutput = Invoke-Adb shell wm density
    $densityMatch = [regex]::Match($densityOutput, '(?:Override|Physical) density:\s*(\d+)')
    if (-not $densityMatch.Success) { throw "Could not determine emulator density: $densityOutput" }
    $minimumTargetPixels = 48.0 * [int]$densityMatch.Groups[1].Value / 160.0
    $sizeOutput = Invoke-Adb shell wm size
    $sizeMatch = [regex]::Match($sizeOutput, '(?:Override|Physical) size:\s*(\d+)x(\d+)')
    if (-not $sizeMatch.Success) { throw "Could not determine emulator size: $sizeOutput" }
    $screenWidth = [int]$sizeMatch.Groups[1].Value
    $screenHeight = [int]$sizeMatch.Groups[2].Value

    $pickerPhoto = Join-Path $repo "docs/assets/google-play/app-icon-512.png"
    Invoke-Adb -Arguments @("shell", "mkdir", "-p", "/sdcard/Pictures") | Out-Null
    Invoke-Adb push $pickerPhoto /sdcard/Pictures/sproutpace-smoke.png | Out-Null
    Invoke-Adb -Arguments @(
        "shell", "am", "broadcast",
        "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE",
        "-d", "file:///sdcard/Pictures/sproutpace-smoke.png"
    ) | Out-Null
    Invoke-Adb -Arguments @("shell", "rm", "-f", $externalBackupPath) | Out-Null

    $initial = Dump-Ui "00-initial-route"
    if (@(Nodes $initial | Where-Object { $_.GetAttribute("text") -eq "Get started" }).Count -gt 0) {
        $browseCatalog = Find-Node $initial -Text "Browse catalog"
        Tap-Node $browseCatalog
        Start-Sleep -Milliseconds 750
        $catalogFromFirstRun = Dump-Ui "00a-catalog-from-first-run"
        Find-Node $catalogFromFirstRun -Text "Catalog" | Out-Null
        $results["first-run-browse-catalog-navigation"] = "passed"

        Invoke-Adb shell pm clear $PackageName | Out-Null
        Start-App
        $firstRunAgain = Dump-Ui "00b-first-run-restarted"
        Find-Node $firstRunAgain -Text "Name the place where your trays grow and set its capacity." | Out-Null

        Assert-KeyboardReachable -Text "Continue" -DumpPrefix "00c-first-run-step-1-keyboard"
        Invoke-Adb shell input keyevent KEYCODE_ENTER | Out-Null
        Start-Sleep -Milliseconds 750
        $firstRunStep2 = Dump-Ui "00d-first-run-step-2"
        Find-Node $firstRunStep2 -Text "Save the tray dimensions you use most often." | Out-Null

        Assert-KeyboardReachable -Text "Continue" -DumpPrefix "00e-first-run-step-2-keyboard"
        Invoke-Adb shell input keyevent KEYCODE_ENTER | Out-Null
        Start-Sleep -Milliseconds 750
        $firstRunStep3 = Dump-Ui "00f-first-run-step-3"
        Find-Node $firstRunStep3 -Text "Finish setup" | Out-Null

        Assert-KeyboardReachable -Text "Finish setup" -DumpPrefix "00g-first-run-step-3-keyboard"
        Invoke-Adb shell input keyevent KEYCODE_ENTER | Out-Null
        Start-Sleep -Milliseconds 750
        $initialPlan = Dump-Ui "00h-plan-after-setup"
        Find-Node $initialPlan -Text "Plan" | Out-Null
        $results["first-run-three-step-setup"] = "passed"
        Start-App
    }

    $today = Dump-Ui "01-today"
    $todayPrimary = @(Nodes $today | Where-Object { $_.GetAttribute("text") -eq "New batch" } | Select-Object -First 1)
    if ($todayPrimary.Count -eq 0) { $todayPrimary = @(Find-Node $today -Text "Confirm suggestion") }
    $settings = Find-Node $today -ContentDescription "Settings"
    Assert-MinimumTarget $todayPrimary[0] $minimumTargetPixels "Today primary action"
    Assert-MinimumTarget $settings $minimumTargetPixels "Settings"
    $results["today-accessibility-tree"] = "passed"
    $results["minimum-48dp-targets"] = "passed"

    Assert-KeyboardReachable `
        -ContentDescription "Settings" -DumpPrefix "02-keyboard-focus" -MaximumTabs 32
    Invoke-Adb shell input keyevent KEYCODE_ENTER | Out-Null
    $settingsScreen = Dump-Ui "03-settings-keyboard-open"
    Find-Node $settingsScreen -Text "Settings" | Out-Null
    Find-Node $settingsScreen -Text "Growing setup" | Out-Null
    $results["keyboard-focus-and-activation"] = "passed"

    $notificationsRow = Find-Node $settingsScreen -Text "Notifications"
    Assert-MinimumTarget $notificationsRow $minimumTargetPixels "Notifications settings row"
    Tap-Node $notificationsRow
    $notifications = Dump-Ui "04-notifications-denied"
    Find-Node $notifications -Text "Permission needed" | Out-Null
    $allowNotifications = Find-Node $notifications -Text "Allow notifications"
    Assert-MinimumTarget $allowNotifications $minimumTargetPixels "Allow notifications"
    Find-Node $notifications -Text "Open system settings" | Out-Null
    $results["permission-denied-recovery"] = "passed"

    Tap-Node $allowNotifications
    $permissionDialog = Dump-Ui "05-system-permission-dialog"
    if (@(Nodes $permissionDialog | Where-Object { $_.GetAttribute("package") -eq "com.google.android.permissioncontroller" }).Count -eq 0) {
        throw "Android notification permission controller did not open"
    }
    Find-Node $permissionDialog -Text "Allow" | Out-Null
    $deny = Find-Node $permissionDialog -Text "Don’t allow"
    Tap-Node $deny
    $denied = Dump-Ui "06-permission-denied-return"
    Find-Node $denied -Text "Open system settings" | Out-Null
    $results["permission-request-and-denial"] = "passed"

    $openSystemSettings = Find-Node $denied -Text "Open system settings"
    Tap-Node $openSystemSettings
    $systemSettings = Dump-Ui "07-system-settings-recovery"
    if (@(Nodes $systemSettings | Where-Object { $_.GetAttribute("package") -eq "com.android.settings" }).Count -eq 0) {
        throw "System settings did not open"
    }
    Find-Node $systemSettings -Text "SproutPace" | Out-Null
    Find-Node $systemSettings -Text "Notifications" | Out-Null
    $results["system-settings-recovery"] = "passed"

    Invoke-Adb shell input keyevent KEYCODE_BACK | Out-Null
    Invoke-Adb shell pm grant $PackageName android.permission.POST_NOTIFICATIONS | Out-Null
    Start-App
    $restart = Dump-Ui "08-process-restart"
    Find-Node $restart -Text "Today" | Out-Null
    if (@(Nodes $restart | Where-Object { $_.GetAttribute("text") -eq "New batch" }).Count -eq 0) {
        Find-Node $restart -Text "Confirm suggestion" | Out-Null
    }
    $results["process-restart"] = "passed"

    Tap-Node (Find-Node $restart -ContentDescription "Settings")
    $settingsAfterRestart = Dump-Ui "09-settings-after-restart"
    Tap-Node (Find-Node $settingsAfterRestart -Text "Notifications")
    $notificationsGranted = Dump-Ui "10-notifications-granted"
    if (@(Nodes $notificationsGranted | Where-Object { $_.GetAttribute("text") -eq "Permission needed" }).Count -ne 0) {
        throw "Permission-needed state remained after Android permission grant"
    }

    $timeField = Find-Node $notificationsGranted -Text "09:00"
    Tap-Node $timeField
    Invoke-Adb shell input keyevent KEYCODE_MOVE_END | Out-Null
    1..5 | ForEach-Object { Invoke-Adb shell input keyevent KEYCODE_DEL | Out-Null }
    Invoke-Adb shell input text "10:30" | Out-Null
    $editedTime = Dump-Ui "11-manual-time-entry"
    Find-Node $editedTime -Text "10:30" | Out-Null
    Invoke-Adb shell input keyevent KEYCODE_ESCAPE | Out-Null
    $keyboardDismissed = Dump-Ui "11b-keyboard-dismissed"
    Tap-Node (Find-Node $keyboardDismissed -Text "Update notifications")
    $saveCompleted = $false
    1..12 | ForEach-Object {
        if ($saveCompleted) { return }
        Start-Sleep -Milliseconds 250
        $saveState = Dump-Ui "11c-save-state-$($_)"
        $discardActions = @(Nodes $saveState | Where-Object { $_.GetAttribute("text") -eq "Discard changes" })
        if ($discardActions.Count -eq 0) { $saveCompleted = $true }
    }
    if (-not $saveCompleted) { throw "Notification settings did not report a completed save" }
    Start-App
    $afterSave = Dump-Ui "12-restart-after-save"
    Tap-Node (Find-Node $afterSave -ContentDescription "Settings")
    $settingsAfterSave = Dump-Ui "13-settings-after-save"
    Tap-Node (Find-Node $settingsAfterSave -Text "Notifications")
    $persisted = Dump-Ui "14-persisted-time"
    Find-Node $persisted -Text "10:30" | Out-Null
    $results["manual-time-entry-and-restart-persistence"] = "passed"

    Invoke-Adb shell settings put system font_scale 2.0 | Out-Null
    Invoke-Adb shell pm clear $PackageName | Out-Null
    Start-App
    $firstRun = Dump-Ui "20-first-run-200-percent"
    Find-Node $firstRun -Text "Get started" | Out-Null
    $browseCatalog = Find-Node $firstRun -Text "Browse catalog"
    $continueSetup = Find-Node $firstRun -Text "Continue"
    Assert-MinimumTarget $browseCatalog $minimumTargetPixels "First Run browse catalog"
    Assert-MinimumTarget $continueSetup $minimumTargetPixels "First Run continue"
    Assert-NodeOnScreen $continueSetup $screenWidth $screenHeight "First Run continue"
    Assert-KeyboardReachable -Text "Continue" -DumpPrefix "21-first-run-keyboard"
    Invoke-Adb shell input keyevent KEYCODE_ENTER | Out-Null
    Start-Sleep -Milliseconds 750
    $firstRunStep2 = Dump-Ui "22-first-run-step-2-200-percent"
    Find-Node $firstRunStep2 -Text "Save the tray dimensions you use most often." | Out-Null
    Assert-KeyboardReachable -Text "Continue" -DumpPrefix "23-first-run-step-2-keyboard"
    Invoke-Adb shell input keyevent KEYCODE_ENTER | Out-Null
    Start-Sleep -Milliseconds 750
    $firstRunStep3 = Dump-Ui "24-first-run-step-3-200-percent"
    Find-Node $firstRunStep3 -Text "Finish setup" | Out-Null
    Assert-KeyboardReachable -Text "Finish setup" -DumpPrefix "25-first-run-step-3-keyboard"
    Invoke-Adb shell input keyevent KEYCODE_ENTER | Out-Null
    Start-Sleep -Milliseconds 750
    $plan200 = Dump-Ui "26-plan-after-first-run-200-percent"
    Find-Node $plan200 -Text "Plan" | Out-Null
    $confirmSuggestion = Find-Node $plan200 -Text "Confirm suggestion"
    Assert-MinimumTarget $confirmSuggestion $minimumTargetPixels "Plan confirmation"
    Assert-NodeOnScreen $confirmSuggestion $screenWidth $screenHeight "Plan confirmation"
    Assert-KeyboardReachable -Text "Confirm suggestion" -DumpPrefix "27-plan-keyboard"
    $results["first-run-and-plan-200-percent"] = "passed"

    Tap-Node (Find-Node $plan200 -ContentDescription "Trays")
    Start-Sleep -Milliseconds 500
    $trays200 = Dump-Ui "24-trays-200-percent"
    Find-Node $trays200 -Text "Planned" | Out-Null
    Find-Node $trays200 -Text "Active" | Out-Null
    Find-Node $trays200 -Text "History" | Out-Null
    $newBatch200 = Find-Node $trays200 -Text "New batch"
    Assert-MinimumTarget $newBatch200 $minimumTargetPixels "Trays new batch"
    Assert-NodeOnScreen $newBatch200 $screenWidth $screenHeight "Trays new batch"
    Assert-KeyboardReachable -Text "New batch" -DumpPrefix "25-trays-keyboard"
    $results["trays-200-percent"] = "passed"

    Tap-Node (Find-Node $trays200 -ContentDescription "Catalog")
    Start-Sleep -Milliseconds 500
    $catalog200 = Dump-Ui "26-catalog-200-percent"
    $catalogSearch = Find-Node $catalog200 -Text "Search catalog"
    $openRecipe = Find-Node $catalog200 -Text "Open recipe"
    Assert-MinimumTarget $catalogSearch $minimumTargetPixels "Catalog search"
    Assert-MinimumTarget $openRecipe $minimumTargetPixels "Catalog open recipe"
    Assert-NodeOnScreen $openRecipe $screenWidth $screenHeight "Catalog open recipe"
    Assert-KeyboardReachable -Text "Search catalog" -DumpPrefix "27-catalog-keyboard"
    $results["catalog-200-percent"] = "passed"

    Invoke-Adb shell input keyevent KEYCODE_BACK | Out-Null
    Start-Sleep -Milliseconds 250
    $catalogKeyboardClosed = Dump-Ui "27b-catalog-keyboard-closed"
    Tap-Node (Find-Node $catalogKeyboardClosed -ContentDescription "Trays")
    Start-Sleep -Milliseconds 500
    $traysForBatch = Dump-Ui "28-trays-before-batch"
    Tap-Node (Find-Node $traysForBatch -Text "New batch")
    Start-Sleep -Milliseconds 500
    Invoke-Adb shell input swipe 500 1800 500 800 500 | Out-Null
    Start-Sleep -Milliseconds 350
    $batchBasics = Dump-Ui "29-batch-basics-200-percent"
    Find-Node $batchBasics -Text "10 x 20 tray" | Out-Null
    Tap-Node (Find-Node $batchBasics -Text "Continue")
    Start-Sleep -Milliseconds 500
    $batchDetails = Dump-Ui "30-batch-details-200-percent"
    $substrate = Find-Node $batchDetails -ClassName "android.widget.EditText" -Index 0
    Tap-Node $substrate
    Invoke-Adb shell input text "Coco" | Out-Null
    Invoke-Adb shell input keyevent KEYCODE_BACK | Out-Null
    Start-Sleep -Milliseconds 250
    $detailsKeyboardClosed = Dump-Ui "31-batch-details-keyboard-closed"
    Tap-Node (Find-Node $detailsKeyboardClosed -Text "Review batch")
    Start-Sleep -Milliseconds 500
    $batchReview = Dump-Ui "32-batch-review-200-percent"
    Tap-Node (Find-Node $batchReview -Text "Save batch")
    Start-Sleep -Milliseconds 900
    $traysAfterSave = Dump-Ui "33-trays-after-save-200-percent"
    Tap-Node (Find-Node $traysAfterSave -ContentDescription "Today")
    Start-Sleep -Milliseconds 500
    $todayWithBatch = Dump-Ui "34-today-with-batch-200-percent"
    Find-Node $todayWithBatch -Text "Radish" | Out-Null
    $completeTodayAction = Find-Node $todayWithBatch -Text "Complete action"
    Assert-MinimumTarget $completeTodayAction $minimumTargetPixels "Today complete action"
    Assert-KeyboardReachable -Text "Complete action" -DumpPrefix "35-today-keyboard"
    $todayAfterKeyboard = Dump-Ui "35b-today-after-keyboard"
    Tap-Node (Find-Node $todayAfterKeyboard -ContentDescription "Trays")
    Start-Sleep -Milliseconds 500
    $traysWithBatch = Dump-Ui "36-trays-with-batch-200-percent"
    Tap-Node (Find-Node $traysWithBatch -Text "Active")
    Start-Sleep -Milliseconds 350
    $activeTrays = Dump-Ui "37-active-trays-200-percent"
    $openBatch = Find-Node $activeTrays -Text "Open batch"
    Assert-MinimumTarget $openBatch $minimumTargetPixels "Open batch"
    Tap-Node $openBatch
    Start-Sleep -Milliseconds 500
    $batchDetail = Dump-Ui "38-batch-detail-200-percent"
    Find-Node $batchDetail -Text "Radish" | Out-Null
    $primaryBatchAction = $null
    $primaryBatchActionLabel = $null
    $batchDetailViewport = $batchDetail
    for ($scrollIndex = 0; $scrollIndex -le 6 -and $null -eq $primaryBatchAction; $scrollIndex += 1) {
        $unexpectedActions = @(Nodes $batchDetailViewport | Where-Object {
            $_.GetAttribute("text") -in @("Confirm stage", "Undo stage", "Skip stage", "Record actual mass")
        })
        if ($unexpectedActions.Count -gt 0) {
            throw "Batch Detail exposes lifecycle actions that are invalid for a planned batch"
        }
        $candidate = Nodes $batchDetailViewport | Where-Object {
            $_.GetAttribute("text") -in @("Start soaking", "Confirm sowing")
        } | Select-Object -First 1
        if ($null -ne $candidate) {
            $primaryBatchAction = $candidate
            $primaryBatchActionLabel = $candidate.GetAttribute("text")
        } elseif ($scrollIndex -lt 6) {
            Invoke-Adb shell input swipe 500 1800 500 700 500 | Out-Null
            Start-Sleep -Milliseconds 350
            $batchDetailViewport = Dump-Ui "38b-batch-detail-scroll-$scrollIndex"
        }
    }
    if ($null -eq $primaryBatchAction) { throw "Missing state-aware primary action on Batch Detail" }
    Assert-MinimumTarget $primaryBatchAction $minimumTargetPixels "Batch detail primary action"
    Assert-NodeOnScreen $primaryBatchAction $screenWidth $screenHeight "Batch detail primary action"
    Assert-KeyboardReachable -Text $primaryBatchActionLabel -DumpPrefix "39-batch-detail-keyboard"
    $results["batch-detail-200-percent"] = "passed"

    $addObservation = Find-NodeAfterForwardScroll -Text "Add observation" -DumpPrefix "40-add-observation"
    Tap-Node $addObservation
    Start-Sleep -Milliseconds 500
    $choosePhoto = Find-NodeAfterForwardScroll -Text "Choose photo" -DumpPrefix "41-choose-photo"
    Tap-Node $choosePhoto
    Start-Sleep -Milliseconds 750
    $photoPicker = Dump-Ui "42-photo-picker-cancel"
    if (@(Nodes $photoPicker | Where-Object {
        $_.GetAttribute("package") -eq "com.google.android.photopicker"
    }).Count -eq 0) {
        throw "Android Photo Picker did not open"
    }
    Invoke-Adb shell input keyevent KEYCODE_BACK | Out-Null
    Start-Sleep -Milliseconds 500
    $afterPhotoCancel = Dump-Ui "43-photo-picker-cancelled"
    Find-Node $afterPhotoCancel -Text "Observation" | Out-Null
    Find-Node $afterPhotoCancel -Text "No photo selected" | Out-Null

    $choosePhotoAgain = Find-NodeAfterForwardScroll -Text "Choose photo" -DumpPrefix "44-choose-photo-again"
    Tap-Node $choosePhotoAgain
    Start-Sleep -Milliseconds 750
    $seededPicker = Dump-Ui "45-photo-picker-seeded"
    $dismissCloudPrompt = Nodes $seededPicker | Where-Object {
        $_.GetAttribute("text") -eq "Dismiss"
    } | Select-Object -First 1
    if ($null -ne $dismissCloudPrompt) {
        Tap-Node $dismissCloudPrompt
        Start-Sleep -Milliseconds 350
        $seededPicker = Dump-Ui "45b-photo-picker-cloud-dismissed"
    }
    $photo = Nodes $seededPicker | Where-Object {
        $_.GetAttribute("content-desc") -like "Photo taken on*"
    } | Select-Object -First 1
    if ($null -eq $photo) { throw "Seeded photo is not visible in Android Photo Picker" }
    Tap-Node $photo
    Start-Sleep -Milliseconds 350
    $selectedPhoto = Dump-Ui "46-photo-picker-selected"
    Tap-Node (Find-Node $selectedPhoto -Text "Done")
    Start-Sleep -Milliseconds 750
    $photoImported = Dump-Ui "47-photo-imported"
    Find-Node $photoImported -Text "Private photo selected" | Out-Null
    $privatePhotoFiles = (Invoke-Adb shell run-as $PackageName ls files/pending_photos).Trim()
    if (-not $privatePhotoFiles.EndsWith(".png")) {
        throw "Selected photo was not imported into app-private pending storage"
    }

    $observationText = Find-Node $photoImported -ClassName "android.widget.EditText" -Index 1
    Tap-Node $observationText
    Invoke-Adb shell input text "Runtime_photo_evidence" | Out-Null
    Invoke-Adb shell input keyevent KEYCODE_BACK | Out-Null
    Start-Sleep -Milliseconds 250
    1..4 | ForEach-Object { Invoke-Adb shell input swipe 500 650 500 1800 400 | Out-Null }
    Start-Sleep -Milliseconds 350
    $observationReady = Dump-Ui "48-observation-ready"
    Tap-Node (Find-Node $observationReady -Text "Save observation")
    Start-Sleep -Milliseconds 900
    Start-App
    $afterObservationRestart = Dump-Ui "49-observation-process-restart"
    Tap-Node (Find-Node $afterObservationRestart -ContentDescription "Trays")
    Start-Sleep -Milliseconds 500
    $traysAfterObservation = Dump-Ui "50-trays-after-observation"
    Tap-Node (Find-Node $traysAfterObservation -Text "Active")
    Start-Sleep -Milliseconds 350
    $activeAfterObservation = Dump-Ui "51-active-after-observation"
    Tap-Node (Find-Node $activeAfterObservation -Text "Open batch")
    Start-Sleep -Milliseconds 500
    $savedObservation = Find-NodeAfterForwardScroll `
        -ContentDescription "Photo attached to this observation" `
        -DumpPrefix "52-saved-observation" -MaximumScrolls 8
    $observationViewport = Dump-Ui "53-saved-observation-photo"
    Find-Node $observationViewport -ContentDescription "Photo attached to this observation" | Out-Null
    Assert-NodeOnScreen $savedObservation $screenWidth $screenHeight "Saved observation"
    $results["photo-picker-cancel-select-private-copy-and-restart"] = "passed"

    Tap-Node (Find-Node $observationViewport -ContentDescription "Back")
    Start-Sleep -Milliseconds 500
    $traysForBackup = Dump-Ui "54-trays-for-backup"
    Tap-Node (Find-Node $traysForBackup -ContentDescription "Settings")
    Start-Sleep -Milliseconds 500
    $backupSettingsRow = Find-NodeAfterForwardScroll `
        -Text "Backup and restore" -DumpPrefix "55-backup-settings-row"
    Tap-Node $backupSettingsRow
    Start-Sleep -Milliseconds 500
    $backupScreen = Dump-Ui "56-backup-screen"
    $createBackup = Find-Node $backupScreen -Text "Create backup" -Index 1
    Tap-Node $createBackup
    Start-Sleep -Milliseconds 750
    $createPicker = Dump-Ui "57-create-document-cancel"
    if (@(Nodes $createPicker | Where-Object {
        $_.GetAttribute("package") -eq "com.google.android.documentsui"
    }).Count -eq 0) {
        throw "Android create-document picker did not open"
    }
    Find-Node $createPicker -Text "sproutpace-backup.zip" | Out-Null
    Invoke-Adb shell input keyevent KEYCODE_BACK | Out-Null
    Start-Sleep -Milliseconds 500
    $afterCreateCancel = Dump-Ui "58-create-document-cancelled"
    Find-Node $afterCreateCancel -Text "Backup and restore" | Out-Null

    Tap-Node (Find-Node $afterCreateCancel -Text "Create backup" -Index 1)
    Start-Sleep -Milliseconds 750
    $createPickerAgain = Dump-Ui "59-create-document-save"
    Tap-Node (Find-Node $createPickerAgain -Text "SAVE")
    Start-Sleep -Milliseconds 1200
    $afterBackupSave = Dump-Ui "60-backup-saved"
    Find-Node $afterBackupSave -Text "Backup and restore" | Out-Null
    $backupSize = [long](Invoke-Adb -Arguments @("shell", "stat", "-c", "%s", $externalBackupPath)).Trim()
    if ($backupSize -le 0) { throw "Created backup document is empty" }

    $selectedDocument = Find-NodeAfterForwardScroll `
        -Text "File selected" -DumpPrefix "61-selected-document"
    $removeFile = Find-NodeAfterForwardScroll `
        -Text "Remove file" -DumpPrefix "63-selected-document-actions"
    Tap-Node $removeFile
    Start-Sleep -Milliseconds 350
    Find-NodeAfterForwardScroll -Text "No file selected" -DumpPrefix "64-document-cleared" | Out-Null
    $chooseBackupFile = Find-NodeAfterForwardScroll `
        -Text "Choose file" -DumpPrefix "64b-choose-document"
    Tap-Node $chooseBackupFile
    Start-Sleep -Milliseconds 750
    $openPicker = Dump-Ui "65-open-document-cancel"
    if (@(Nodes $openPicker | Where-Object {
        $_.GetAttribute("package") -eq "com.google.android.documentsui"
    }).Count -eq 0) {
        throw "Android open-document picker did not open"
    }
    Find-Node $openPicker -Text "sproutpace-backup.zip" | Out-Null
    Invoke-Adb shell input keyevent KEYCODE_BACK | Out-Null
    Start-Sleep -Milliseconds 500
    Find-NodeAfterForwardScroll `
        -Text "No file selected" -DumpPrefix "66-open-document-cancelled" | Out-Null

    $chooseBackupFileAgain = Find-NodeAfterForwardScroll `
        -Text "Choose file" -DumpPrefix "66b-choose-document-again"
    Tap-Node $chooseBackupFileAgain
    Start-Sleep -Milliseconds 750
    $openPickerAgain = Dump-Ui "67-open-document-select"
    Tap-Node (Find-Node $openPickerAgain -Text "sproutpace-backup.zip")
    Start-Sleep -Milliseconds 900
    Find-NodeAfterForwardScroll `
        -Text "File selected" -DumpPrefix "68-document-imported" | Out-Null
    $importedBackupFiles = (Invoke-Adb shell run-as $PackageName ls files/imports).Trim()
    if (-not $importedBackupFiles.EndsWith(".zip")) {
        throw "Selected backup was not imported into app-private storage"
    }

    1..7 | ForEach-Object { Invoke-Adb shell input swipe 500 650 500 1800 400 | Out-Null }
    Start-Sleep -Milliseconds 400
    $backupTop = Dump-Ui "69-backup-before-preview"
    Find-Node $backupTop -Text "Preview restore" -Index 1 | Out-Null
    Assert-KeyboardReachable `
        -Text "Preview restore" -DumpPrefix "69b-preview-restore-keyboard" -MaximumTabs 40
    $focusedPreviewDocument = Dump-Ui "69c-preview-restore-focused"
    $focusedPreviewAction = Nodes $focusedPreviewDocument | Where-Object {
        $_.GetAttribute("focused") -eq "true" -and
        $null -ne $_.SelectSingleNode(".//node[@text='Preview restore']")
    } | Select-Object -First 1
    if ($null -eq $focusedPreviewAction) { throw "Preview restore action lost keyboard focus" }
    Tap-Node $focusedPreviewAction
    $restorePreview = $null
    for ($previewAttempt = 1; $previewAttempt -le 20; $previewAttempt += 1) {
        Start-Sleep -Milliseconds 250
        $candidatePreview = Dump-Ui "70-restore-preview-$previewAttempt"
        if (@(Nodes $candidatePreview | Where-Object {
            $_.GetAttribute("text") -eq "Restore preview"
        }).Count -gt 0) {
            $restorePreview = $candidatePreview
            break
        }
    }
    if ($null -eq $restorePreview) { throw "Backup restore preview did not load" }
    Find-Node $restorePreview -Text "Restore preview" | Out-Null
    Find-Node $restorePreview `
        -Text "Restore does not merge records. Review this backup before confirming." | Out-Null
    $results["document-picker-create-open-cancel-import-and-preview"] = "passed"

    $summary = [ordered]@{
        schemaVersion = "1.0"
        emulator = $AvdSerial
        packageName = $PackageName
        densityDpi = [int]$densityMatch.Groups[1].Value
        fontScaleStress = 2.0
        minimumTargetPixels = $minimumTargetPixels
        checks = $results
        completedAt = (Get-Date).ToUniversalTime().ToString("o")
    }
    $summary | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $reportDirectory "summary.json") -Encoding utf8
    Write-Output ($summary | ConvertTo-Json -Depth 5)
} finally {
    if ($null -ne $previousFontScale -and $previousFontScale -ne "null" -and $previousFontScale -ne "") {
        try { Invoke-Adb shell settings put system font_scale $previousFontScale | Out-Null } catch { }
    }
    try { Invoke-Adb shell pm revoke $PackageName android.permission.POST_NOTIFICATIONS | Out-Null } catch { }
    try { Invoke-Adb -Arguments @("shell", "rm", "-f", $externalBackupPath) | Out-Null } catch { }
    try {
        Invoke-Adb -Arguments @("shell", "rm", "-f", "/sdcard/Pictures/sproutpace-smoke.png") | Out-Null
    } catch { }
    if (-not $KeepAppState) {
        try { Invoke-Adb shell pm clear $PackageName | Out-Null } catch { }
    }
}
