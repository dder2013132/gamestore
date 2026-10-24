[CmdletBinding()]
param(
    [string]$PostgresBin = (Join-Path $env:ProgramFiles 'PostgreSQL\18\bin'),
    [string]$TomcatHome = (Join-Path $env:USERPROFILE 'scoop\apps\tomcat10\current')
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$backupWorkspace = [System.IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$backupFolder = Join-Path $backupWorkspace ('backups\gamestore-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 6))
$backupUtf8 = New-Object System.Text.UTF8Encoding($false)

function Write-BackupText([string]$RelativePath, [string]$Text) {
    [System.IO.File]::WriteAllText((Join-Path $backupFolder $RelativePath), $Text, $backupUtf8)
}

function New-ProjectArchive {
    $backupZipPath = Join-Path $backupFolder 'project.zip'
    $backupArchive = [System.IO.Compression.ZipFile]::Open($backupZipPath, [System.IO.Compression.ZipArchiveMode]::Create)
    $backupSourceManifest = New-Object 'System.Collections.Generic.List[object]'
    $backupQueue = New-Object 'System.Collections.Generic.Queue[string]'
    $backupQueue.Enqueue($backupWorkspace)
    try {
        while ($backupQueue.Count -gt 0) {
            $backupDirectory = $backupQueue.Dequeue()
            foreach ($backupItem in (Get-ChildItem -LiteralPath $backupDirectory -Force)) {
                $backupRelative = $backupItem.FullName.Substring($backupWorkspace.Length + 1).Replace('\', '/')
                if ($backupItem.PSIsContainer) {
                    if ($backupRelative -in @('backups', 'gamestore/target', 'gamestore/.artifacts')) { continue }
                    if (($backupItem.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
                        throw "A linked directory needs explicit backup handling: $backupRelative"
                    }
                    $backupQueue.Enqueue($backupItem.FullName)
                    continue
                }
                $backupBytes = [System.IO.File]::ReadAllBytes($backupItem.FullName)
                $backupEntry = $backupArchive.CreateEntry($backupRelative, [System.IO.Compression.CompressionLevel]::Optimal)
                $backupEntry.LastWriteTime = [DateTimeOffset]$backupItem.LastWriteTime
                $backupStream = $backupEntry.Open()
                try { $backupStream.Write($backupBytes, 0, $backupBytes.Length) } finally { $backupStream.Dispose() }
                $backupDigest = [System.Security.Cryptography.SHA256]::Create()
                try { $backupHash = [BitConverter]::ToString($backupDigest.ComputeHash($backupBytes)).Replace('-', '').ToLowerInvariant() }
                finally { $backupDigest.Dispose() }
                $backupSourceManifest.Add([PSCustomObject]@{ path = $backupRelative; bytes = $backupBytes.Length; sha256 = $backupHash })
            }
        }
    } finally { $backupArchive.Dispose() }
    Write-BackupText 'project-files.json' ($backupSourceManifest | ConvertTo-Json -Depth 4)

    $backupArchive = [System.IO.Compression.ZipFile]::OpenRead($backupZipPath)
    try {
        if ($backupArchive.Entries.Count -ne $backupSourceManifest.Count) { throw 'Project ZIP entry count differs.' }
        foreach ($backupRecord in $backupSourceManifest) {
            $backupEntry = $backupArchive.GetEntry($backupRecord.path)
            if ($null -eq $backupEntry -or $backupEntry.Length -ne $backupRecord.bytes) { throw "Project ZIP entry differs: $($backupRecord.path)" }
            $backupStream = $backupEntry.Open()
            $backupDigest = [System.Security.Cryptography.SHA256]::Create()
            try { $backupHash = [BitConverter]::ToString($backupDigest.ComputeHash($backupStream)).Replace('-', '').ToLowerInvariant() }
            finally { $backupDigest.Dispose(); $backupStream.Dispose() }
            if ($backupHash -ne $backupRecord.sha256) { throw "Project ZIP checksum differs: $($backupRecord.path)" }
        }
    } finally { $backupArchive.Dispose() }
    Write-BackupText 'project-verification.txt' ("Verified {0} source and Git files inside project.zip by length and SHA-256.`r`nExcluded only backups/, gamestore/target/, gamestore/.artifacts/.`r`n" -f $backupSourceManifest.Count)
    Write-Host "Project ZIP verified: $($backupSourceManifest.Count) files."
}

try {
    foreach ($backupTool in @('pg_dump.exe', 'pg_restore.exe')) {
        if (-not (Test-Path -LiteralPath (Join-Path $PostgresBin $backupTool) -PathType Leaf)) { throw "PostgreSQL tool missing: $backupTool" }
    }
    $backupDeployedWar = Join-Path $TomcatHome 'webapps\gamestore.war'
    if (-not (Test-Path -LiteralPath $backupDeployedWar -PathType Leaf)) { throw 'The deployed gamestore.war could not be found.' }
    if (Test-Path -LiteralPath $backupFolder) { throw 'Backup destination already exists.' }
    New-Item -ItemType Directory -Path $backupFolder | Out-Null

    # Config files and account data remain local, readable only by this user and system administrators.
    $backupAcl = Get-Acl -LiteralPath $backupFolder
    $backupAcl.SetAccessRuleProtection($true, $false)
    $backupSids = @([System.Security.Principal.WindowsIdentity]::GetCurrent().User,
        (New-Object System.Security.Principal.SecurityIdentifier('S-1-5-18')),
        (New-Object System.Security.Principal.SecurityIdentifier('S-1-5-32-544')))
    foreach ($backupSid in $backupSids) {
        $backupRule = New-Object System.Security.AccessControl.FileSystemAccessRule($backupSid, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
        $backupAcl.AddAccessRule($backupRule)
    }
    Set-Acl -LiteralPath $backupFolder -AclObject $backupAcl
    New-Item -ItemType Directory -Path (Join-Path $backupFolder 'runtime') | Out-Null

    Write-Host '[1/4] Saving the deployed WAR and Tomcat configuration...'
    $backupWarCopy = Join-Path $backupFolder 'runtime\gamestore.war'
    Copy-Item -LiteralPath $backupDeployedWar -Destination $backupWarCopy
    if ((Get-FileHash -LiteralPath $backupDeployedWar -Algorithm SHA256).Hash -ne (Get-FileHash -LiteralPath $backupWarCopy -Algorithm SHA256).Hash) { throw 'Deployed WAR copy differs.' }
    $backupWarArchive = [System.IO.Compression.ZipFile]::OpenRead($backupWarCopy)
    try {
        $backupDriver = @($backupWarArchive.Entries | Where-Object { $_.FullName -match '^WEB-INF/lib/postgresql-[^/]+\.jar$' })
        if ($backupDriver.Count -ne 1) { throw 'Expected one PostgreSQL JDBC driver in the deployed WAR.' }
        $backupDriverPath = Join-Path $backupFolder 'runtime\postgresql-driver.jar'
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($backupDriver[0], $backupDriverPath)
    } finally { $backupWarArchive.Dispose() }
    $backupTomcatConf = Join-Path $TomcatHome 'conf'
    foreach ($backupConfig in (Get-ChildItem -LiteralPath $backupTomcatConf -File -Force -Recurse)) {
        $backupConfigRelative = $backupConfig.FullName.Substring($backupTomcatConf.Length + 1)
        $backupConfigTarget = Join-Path $backupFolder ('runtime\tomcat-conf\' + $backupConfigRelative)
        New-Item -ItemType Directory -Path (Split-Path -Parent $backupConfigTarget) -Force | Out-Null
        Copy-Item -LiteralPath $backupConfig.FullName -Destination $backupConfigTarget
    }
    foreach ($backupSetenvName in @('setenv.bat', 'setenv.ps1')) {
        $backupSetenvSource = Join-Path $TomcatHome ('bin\' + $backupSetenvName)
        if (Test-Path -LiteralPath $backupSetenvSource) { Copy-Item -LiteralPath $backupSetenvSource -Destination (Join-Path $backupFolder ('runtime\' + $backupSetenvName)) }
    }
    $backupServerInfo = & java -cp (Join-Path $TomcatHome 'lib\catalina.jar') org.apache.catalina.util.ServerInfo
    if ($LASTEXITCODE -ne 0) { throw 'Unable to record Tomcat version.' }
    Write-BackupText 'runtime\versions.txt' (($backupServerInfo -join "`r`n") + "`r`n" + ((& (Join-Path $PostgresBin 'pg_dump.exe') --version) -join "`r`n"))

    Write-Host '[2/4] Backing up PostgreSQL and verifying an isolated restore...'
    Push-Location -LiteralPath $PSScriptRoot
    try {
        & java -cp $backupDriverPath (Join-Path $PSScriptRoot 'scripts\DatabaseBackup.java') $PostgresBin $backupFolder
        if ($LASTEXITCODE -ne 0) { throw 'Database backup or restore verification failed.' }
    } finally { Pop-Location }
    if (-not (Test-Path -LiteralPath (Join-Path $backupFolder 'database-verified.ok'))) { throw 'Database verification marker is missing.' }

    Write-Host '[3/4] Archiving current source files, images and Git history...'
    $backupHead = & git -C $backupWorkspace rev-parse HEAD
    if ($LASTEXITCODE -ne 0) { throw 'Unable to record Git revision.' }
    $backupGitStatus = & git -C $backupWorkspace status --short --untracked-files=all
    if ($LASTEXITCODE -ne 0) { throw 'Unable to record Git status.' }
    Write-BackupText 'git-state.txt' ("HEAD: $backupHead`r`n`r`nWorking files (included in project.zip):`r`n" + ($backupGitStatus -join "`r`n"))
    $backupGitCheck = & git -C $backupWorkspace fsck --full
    if ($LASTEXITCODE -ne 0) { throw 'Git object integrity check failed.' }
    Write-BackupText 'git-verification.txt' ("git fsck --full completed successfully.`r`n" + ($backupGitCheck -join "`r`n"))
    New-ProjectArchive

    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'docs\backup-restore.md') -Destination (Join-Path $backupFolder 'RESTORE.md')
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'scripts\Verify-Backup.ps1') -Destination (Join-Path $backupFolder 'Verify-Backup.ps1')
    Write-Host '[4/4] Recording and checking backup file hashes...'
    $backupChecksums = @(Get-ChildItem -LiteralPath $backupFolder -File -Recurse | Sort-Object FullName | ForEach-Object {
        [PSCustomObject]@{ path = $_.FullName.Substring($backupFolder.Length + 1).Replace('\', '/'); bytes = $_.Length; sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() }
    })
    Write-BackupText 'checksums.json' ($backupChecksums | ConvertTo-Json -Depth 4)
    & (Join-Path $backupFolder 'Verify-Backup.ps1') -BackupDirectory $backupFolder
    Write-BackupText 'BACKUP-VERIFIED.txt' ("Backup completed and verified at " + (Get-Date -Format o) + "`r`nProject ZIP, Git objects, deployed WAR, and actual isolated PostgreSQL restore verified.`r`n")
    Write-Host "BACKUP COMPLETE: $backupFolder"
} catch {
    Write-Error -Message $_.Exception.Message -ErrorAction Continue
    Write-Host "Incomplete backup preserved for diagnosis: $backupFolder"
    exit 1
}
