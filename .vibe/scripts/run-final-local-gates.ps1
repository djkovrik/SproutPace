$ErrorActionPreference = "Stop"

$gradleTasks = @(
    "--no-parallel",
    "--max-workers=1",
    "detekt",
    ":shared:domain:testAndroidHostTest",
    ":shared:data:testAndroidHostTest",
    ":shared:component:testAndroidHostTest",
    ":shared:root:testAndroidHostTest",
    ":shared:platform:testAndroidHostTest",
    ":shared:monetization:testAndroidHostTest",
    ":shared:compose:testAndroidHostTest",
    ":shared:compose:compileAndroidMain",
    ":shared:data:verifySqlDelightMigration",
    ":shared:domain:compileKotlinIosSimulatorArm64",
    ":shared:data:compileKotlinIosSimulatorArm64",
    ":shared:component:compileKotlinIosSimulatorArm64",
    ":shared:root:compileKotlinIosSimulatorArm64",
    ":shared:platform:compileKotlinIosSimulatorArm64",
    ":shared:monetization:compileKotlinIosSimulatorArm64",
    ":shared:compose:compileKotlinIosSimulatorArm64",
    ":shared:compose:verifyPaparazzi",
    ":androidApp:assembleDebug",
    ":androidApp:assembleRelease",
    ":androidApp:bundleRelease",
    "koverXmlReport",
    "koverVerify"
)

& ".\gradlew.bat" @gradleTasks
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$bundledPython = "C:\Users\Sergey\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe"
$assetValidator = "C:\Users\Sergey\.codex\skills\vibe-assets-creator\scripts\validate-assets.py"
& $bundledPython $assetValidator --app-spec-root ".\spec" --repository "."
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

& powershell -NoProfile -ExecutionPolicy Bypass -File ".vibe\scripts\verify-final-design-review.ps1"
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
