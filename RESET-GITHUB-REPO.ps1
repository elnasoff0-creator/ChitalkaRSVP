# Clean replacement of tracked GitHub repository contents with this Edge-only source tree.
# Usage: powershell -ExecutionPolicy Bypass -File .\RESET-GITHUB-REPO.ps1 -RepoPath "C:\path\to\ChitalkaRSVP" -Push
# The target must be a clean local Git clone. Existing .git history is preserved.
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$RepoPath,
    [switch]$Push
)
$ErrorActionPreference = 'Stop'
function Run-Git {
    param([string[]]$GitArgs)
    & git @GitArgs
    if ($LASTEXITCODE -ne 0) { throw "Git command failed: git $($GitArgs -join ' ')" }
}
if (-not (Get-Command git -ErrorAction SilentlyContinue)) { throw 'Git not found. Install Git for Windows or GitHub Desktop with Git.' }
$source = (Resolve-Path -LiteralPath $PSScriptRoot).Path
$repo = (Resolve-Path -LiteralPath $RepoPath).Path
if ($source.TrimEnd('\') -eq $repo.TrimEnd('\')) { throw 'Source must be separate from the target repository.' }
if (-not (Test-Path -LiteralPath (Join-Path $repo '.git'))) { throw 'RepoPath must be an existing local Git clone with .git folder.' }
$root = (& git -C $repo rev-parse --show-toplevel)
if ($LASTEXITCODE -ne 0) { throw 'Unable to read Git repo.' }
if (([IO.Path]::GetFullPath($repo).TrimEnd('\')) -ne ([IO.Path]::GetFullPath($root.Trim()).TrimEnd('\'))) { throw 'RepoPath must point to repository root.' }
$dirty = @(& git -C $repo status --porcelain)
if ($LASTEXITCODE -ne 0 -or $dirty.Count -gt 0) { throw 'Repository has uncommitted/untracked changes. Commit or back them up first.' }
if (-not (Test-Path (Join-Path $source 'app/src/main/AndroidManifest.xml'))) { throw 'Invalid source folder: AndroidManifest.xml not found.' }
if (-not (Test-Path (Join-Path $source '.github/workflows/release.yml'))) { throw 'Invalid source: release workflow missing.' }
$legacy = @(Get-ChildItem -LiteralPath (Join-Path $source 'app/src/main/java') -Recurse -File | Where-Object { $_.Name -match '^Mrt.*\.(java|kt)$' })
if ($legacy.Count -gt 0) { throw 'Edge-only source contains retired MRT classes.' }
if (Test-Path (Join-Path $source '.git')) { throw 'Source must not contain a .git directory.' }
$branch = (& git -C $repo branch --show-current).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrEmpty($branch)) { throw 'Check out a branch (e.g. main), not a detached tag.' }
Write-Host "Replacing tracked files in $repo, branch $branch" -ForegroundColor Yellow
Run-Git -GitArgs @('-C', $repo, 'rm', '-r', '-q', '--', '.')
Get-ChildItem -LiteralPath $source -Force | ForEach-Object {
    if ($_.Name -eq '.git') { throw 'Refusing to copy .git' }
    Copy-Item -LiteralPath $_.FullName -Destination $repo -Recurse -Force
}
Run-Git -GitArgs @('-C', $repo, 'add', '-A')
$remaining = @(Get-ChildItem -LiteralPath (Join-Path $repo 'app/src/main/java') -Recurse -File | Where-Object { $_.Name -match '^Mrt.*\.(java|kt)$' })
if ($remaining.Count -gt 0) { throw 'MRT sources remain in target repository.' }
Write-Host 'Changes staged:'
Run-Git -GitArgs @('-C', $repo, 'diff', '--cached', '--stat')
& git -C $repo diff --cached --quiet
$hasChanges = ($LASTEXITCODE -eq 1)
if ($LASTEXITCODE -gt 1) { throw 'Failed to inspect staged Git changes.' }
if ($hasChanges) {
    Run-Git -GitArgs @('-C', $repo, 'commit', '-m', 'Replace project with clean Edge-only sources; remove MRT')
} else { Write-Host 'No differences to commit.' }
if ($Push) {
    Run-Git -GitArgs @('-C', $repo, 'push', 'origin', "HEAD:$branch")
    Write-Host 'Changes pushed. Run Actions -> Publish signed APK release -> Run workflow on current branch.' -ForegroundColor Green
} else {
    Write-Host 'Changes committed locally. Run: git push origin HEAD' -ForegroundColor Green
}
