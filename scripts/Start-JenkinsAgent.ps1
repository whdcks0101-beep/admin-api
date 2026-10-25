$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$previousSecret = $env:JENKINS_AGENT_SECRET
$secureSecret = Read-Host 'Paste the Jenkins admin-api-agent secret (input is hidden)' -AsSecureString
$secretPointer = [IntPtr]::Zero

try {
    $secretPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureSecret)
    $env:JENKINS_AGENT_SECRET = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($secretPointer)
    if ([string]::IsNullOrWhiteSpace($env:JENKINS_AGENT_SECRET)) {
        throw 'The agent secret must not be empty.'
    }

    Push-Location $projectDirectory
    try {
        docker compose -f compose.jenkins.yaml -f compose.jenkins.agent.yaml config --quiet
        if ($LASTEXITCODE -ne 0) { throw 'Compose validation failed.' }

        docker compose -f compose.jenkins.yaml -f compose.jenkins.agent.yaml up -d --build --no-deps agent
        if ($LASTEXITCODE -ne 0) { throw 'Starting the Jenkins agent failed.' }
    } finally {
        Pop-Location
    }
} finally {
    $env:JENKINS_AGENT_SECRET = $previousSecret
    if ($secretPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secretPointer)
    }
    $secureSecret.Dispose()
}
