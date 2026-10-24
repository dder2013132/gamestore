[CmdletBinding()]
param(
    [string]$TomcatHome = (Join-Path $env:USERPROFILE 'scoop\apps\tomcat10\current'),
    [switch]$SkipTests
)

$ErrorActionPreference = 'Stop'
$deployProjectRoot = $PSScriptRoot
$deployArchiveDirectory = Join-Path $deployProjectRoot '.artifacts'
$deployArchive = Join-Path $deployArchiveDirectory 'gamestore.war'
$deployWebapps = Join-Path ([System.IO.Path]::GetFullPath($TomcatHome)) 'webapps'
$deployDestination = Join-Path $deployWebapps 'gamestore.war'
$deployStagedFile = $null

try {
    if (-not (Test-Path -LiteralPath $deployWebapps -PathType Container)) {
        throw "Tomcat webapps directory does not exist: $deployWebapps"
    }
    $deployMaven = (Get-Command mvn.cmd -CommandType Application -ErrorAction Stop).Source
    New-Item -ItemType Directory -Path $deployArchiveDirectory -Force | Out-Null

    # Produce the WAR outside target, which may be cleared by other build tools.
    $deployMavenArguments = @('-B', '--no-transfer-progress',
        "-Dgamestore.war.outputDirectory=$deployArchiveDirectory", 'package')
    if ($SkipTests) { $deployMavenArguments += '-DskipTests' }

    Write-Host '[1/3] Building a fresh WAR...'
    Push-Location -LiteralPath $deployProjectRoot
    try {
        & $deployMaven @deployMavenArguments
        if ($LASTEXITCODE -ne 0) {
            throw "Maven failed (exit $LASTEXITCODE). The deployed WAR was not replaced."
        }
    } finally {
        Pop-Location
    }
    if (-not (Test-Path -LiteralPath $deployArchive -PathType Leaf)) {
        throw "Maven did not produce the expected WAR: $deployArchive"
    }

    Write-Host '[2/3] Copying and checking the WAR...'
    $deployExpectedHash = (Get-FileHash -LiteralPath $deployArchive -Algorithm SHA256).Hash
    # Tomcat ignores this temporary extension while the copy is in progress.
    $deployStagedFile = Join-Path $deployWebapps ('.gamestore-' + [Guid]::NewGuid().ToString('N') + '.tmp')
    Copy-Item -LiteralPath $deployArchive -Destination $deployStagedFile -Force
    if ((Get-FileHash -LiteralPath $deployStagedFile -Algorithm SHA256).Hash -ne $deployExpectedHash) {
        throw 'The copy checksum does not match. The deployed WAR was not replaced.'
    }
    Move-Item -LiteralPath $deployStagedFile -Destination $deployDestination -Force
    $deployStagedFile = $null
    if ((Get-FileHash -LiteralPath $deployDestination -Algorithm SHA256).Hash -ne $deployExpectedHash) {
        throw 'The destination checksum does not match.'
    }

    Write-Host '[3/3] WAR copied to Tomcat successfully.'
    Write-Host "Local WAR: $deployArchive"
    Write-Host "Tomcat WAR: $deployDestination"
    Write-Host 'If Tomcat is running with autoDeploy enabled, wait for redeployment and refresh the browser.'
} catch {
    Write-Error -Message $_.Exception.Message -ErrorAction Continue
    exit 1
} finally {
    if ($deployStagedFile -and (Test-Path -LiteralPath $deployStagedFile -PathType Leaf)) {
        Remove-Item -LiteralPath $deployStagedFile -Force
    }
}
