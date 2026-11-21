<#
.SYNOPSIS
    Sets up a Windows machine as Living Horizon's regression bench (see SKILL.md).

.DESCRIPTION
    Installs what is missing with winget (Git, Python 3.12, Temurin JDK 21 and 25), makes the
    bench's folder with its own Python environment and a clone of the repository for the bench
    itself, and writes bench.cmd there. Run it again at any time: it only does what is missing.

        powershell -ExecutionPolicy Bypass -File setup.ps1 [-BenchHome D:\lh-bench] [-Schedule] [-KeepAwake]

    -Schedule   registers a task that runs every hour while you are logged on and plays the
                newest develop pre-release if it has not been benched yet (bench.cmd watch).
    -KeepAwake  stops the screen and the machine from going to sleep on mains power: the game
                draws nothing while the screen is off or the session locked.
#>
param(
    [string]$BenchHome = (Join-Path $HOME "living-horizon-bench"),
    [string]$Repository = "MC-Skin-Creator/living-horizon",
    [switch]$Schedule,
    [switch]$KeepAwake,
    [switch]$Perf
)
$ErrorActionPreference = "Stop"

function Say($line) { Write-Host "setup: $line" }

function Refresh-Path {
    $env:Path = [Environment]::GetEnvironmentVariable("Path", "Machine") + ";" + [Environment]::GetEnvironmentVariable("Path", "User")
}

function Ensure($id, $test) {
    if (& $test) { Say "$id already there"; return }
    if (-not (Get-Command winget -ErrorAction SilentlyContinue)) { throw "winget is missing: install App Installer from the Microsoft Store, then run this again" }
    Say "installing $id"
    winget install --id $id --exact --silent --accept-package-agreements --accept-source-agreements
    Refresh-Path
}

function Jdk($major) {
    $roots = @("$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Java", "$env:ProgramFiles\Microsoft")
    foreach ($root in $roots) {
        if (Test-Path $root) {
            $found = Get-ChildItem $root -Directory | Where-Object { $_.Name -match "^jdk-?$major[.\-+]" } | Select-Object -First 1
            if ($found) { return $found.FullName }
        }
    }
    return $null
}

# --- Tools -----------------------------------------------------------------------------------
Ensure "Git.Git" { Get-Command git -ErrorAction SilentlyContinue }
Ensure "Python.Python.3.12" { (Get-Command py -ErrorAction SilentlyContinue) -or (Get-Command python -ErrorAction SilentlyContinue | Where-Object { $_.Source -notmatch "WindowsApps" }) }
Ensure "EclipseAdoptium.Temurin.21.JDK" { Jdk 21 }
Ensure "EclipseAdoptium.Temurin.25.JDK" { Jdk 25 }

# Gradle runs on Java 21 (Loom does not run on 25 for the older versions); it finds 25 itself
# for the 26.x nodes.
$jdk21 = Jdk 21
if (-not $jdk21) { throw "Temurin 21 was installed but not found under Program Files" }
if ([Environment]::GetEnvironmentVariable("JAVA_HOME", "User") -ne $jdk21) {
    [Environment]::SetEnvironmentVariable("JAVA_HOME", $jdk21, "User")
    Say "JAVA_HOME set to $jdk21"
}
$env:JAVA_HOME = $jdk21

# --- The bench's folder ----------------------------------------------------------------------
New-Item -ItemType Directory -Force $BenchHome | Out-Null
[Environment]::SetEnvironmentVariable("LH_BENCH_HOME", $BenchHome, "User")
$env:LH_BENCH_HOME = $BenchHome

# The bench's own copy of the scripts, kept on develop. The builds tested are checked out
# apart, in src\, by the bench itself.
$tool = Join-Path $BenchHome "tool"
if (-not (Test-Path (Join-Path $tool ".git"))) {
    Say "cloning the bench's scripts into $tool"
    git clone --branch develop "https://github.com/$Repository.git" $tool
} else {
    git -C $tool pull --ff-only --quiet
}

$venv = Join-Path $BenchHome "venv"
if (-not (Test-Path (Join-Path $venv "Scripts\python.exe"))) {
    Say "making the Python environment"
    if (Get-Command py -ErrorAction SilentlyContinue) { py -3.12 -m venv $venv } else { python -m venv $venv }
}
& (Join-Path $venv "Scripts\python.exe") -m pip install --quiet --upgrade pip
& (Join-Path $venv "Scripts\python.exe") -m pip install --quiet -r (Join-Path $tool ".claude\skills\regression-bench\requirements.txt")

# bench.cmd: brings the scripts up to date with develop, then runs them.
$launcher = Join-Path $BenchHome "bench.cmd"
@"
@echo off
set LH_BENCH_HOME=%~dp0
set LH_BENCH_HOME=%LH_BENCH_HOME:~0,-1%
git -C "%~dp0tool" pull --ff-only --quiet
"%~dp0venv\Scripts\python.exe" "%~dp0tool\.claude\skills\regression-bench\bench.py" %*
"@ | Set-Content -Encoding ascii $launcher
Say "wrote $launcher"

# --- Optional: power and schedule ------------------------------------------------------------
if ($KeepAwake) {
    powercfg /change monitor-timeout-ac 0
    powercfg /change standby-timeout-ac 0
    Say "the screen and the machine no longer sleep on mains power"
}

if ($Schedule) {
    $name = "Living Horizon regression bench"
    $watch = if ($Perf) { "watch --perf" } else { "watch" }
    $action = New-ScheduledTaskAction -Execute "cmd.exe" -Argument "/c `"`"$launcher`" $watch >> `"$BenchHome\watch.log`" 2>&1`""
    $trigger = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(1) -RepetitionInterval (New-TimeSpan -Hours 1)
    # Interactive: the game needs the desktop of a logged-on session to draw.
    $principal = New-ScheduledTaskPrincipal -UserId "$env:USERDOMAIN\$env:USERNAME" -LogonType Interactive
    $settings = New-ScheduledTaskSettingsSet -MultipleInstances IgnoreNew -ExecutionTimeLimit (New-TimeSpan -Hours 16) -StartWhenAvailable
    Register-ScheduledTask -TaskName $name -Action $action -Trigger $trigger -Principal $principal -Settings $settings -Force | Out-Null
    Say "scheduled '$name' every hour ($watch), log in $BenchHome\watch.log"
}

Say "done. Next: $launcher run   (then $launcher approve last once the pictures are checked)"
