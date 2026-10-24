[CmdletBinding()]
param([string]$BackupDirectory = $PSScriptRoot)
$ErrorActionPreference = 'Stop'
$verifyRoot = [System.IO.Path]::GetFullPath($BackupDirectory).TrimEnd('\')
$verifyRecords = Get-Content -LiteralPath (Join-Path $verifyRoot 'checksums.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$verifyCount = 0
foreach ($verifyRecord in $verifyRecords) {
    $verifyPath = [System.IO.Path]::GetFullPath((Join-Path $verifyRoot $verifyRecord.path))
    if (-not $verifyPath.StartsWith($verifyRoot + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Checksum entry is outside the backup directory.' }
    if (-not (Test-Path -LiteralPath $verifyPath -PathType Leaf)) { throw "Missing backup file: $($verifyRecord.path)" }
    if ((Get-Item -LiteralPath $verifyPath).Length -ne $verifyRecord.bytes) { throw "Size mismatch: $($verifyRecord.path)" }
    if ((Get-FileHash -LiteralPath $verifyPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $verifyRecord.sha256) { throw "Checksum mismatch: $($verifyRecord.path)" }
    $verifyCount++
}
if ($verifyCount -eq 0) { throw 'The backup manifest has no files.' }
Write-Host "Backup hashes verified: $verifyCount files."
