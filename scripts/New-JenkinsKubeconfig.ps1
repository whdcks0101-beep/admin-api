$ErrorActionPreference = 'Stop'

# Export only the cluster CA, never the administrator's client credentials.
$clusterJson = kubectl --context docker-desktop config view --minify --flatten --raw -o 'jsonpath={.clusters[0].cluster}'
if ($LASTEXITCODE -ne 0) { throw 'Reading the Kubernetes cluster configuration failed.' }
$cluster = $clusterJson | ConvertFrom-Json
if ([string]::IsNullOrWhiteSpace($cluster.'certificate-authority-data')) {
    throw 'The Kubernetes cluster CA certificate is missing.'
}

$deployerToken = kubectl --context docker-desktop -n admin-portfolio create token jenkins-deployer --duration=24h
if ($LASTEXITCODE -ne 0) { throw 'Creating the deployment token failed.' }

try {
    $deployerToken = ($deployerToken -join '').Trim()
    $configuration = @{
        apiVersion = 'v1'
        kind = 'Config'
        clusters = @(@{
            name = 'docker-desktop-ci'
            cluster = @{
                server = 'https://host.docker.internal:6443'
                'tls-server-name' = 'kubernetes.docker.internal'
                'certificate-authority-data' = $cluster.'certificate-authority-data'
            }
        })
        users = @(@{
            name = 'jenkins-deployer'
            user = @{ token = $deployerToken }
        })
        contexts = @(@{
            name = 'admin-portfolio-ci'
            context = @{
                cluster = 'docker-desktop-ci'
                user = 'jenkins-deployer'
                namespace = 'admin-portfolio'
            }
        })
        'current-context' = 'admin-portfolio-ci'
    }

    $credentialDirectory = Join-Path $env:USERPROFILE '.kube\jenkins'
    New-Item -ItemType Directory -Path $credentialDirectory -Force | Out-Null
    $credentialPath = Join-Path $credentialDirectory 'admin-api.json'
    $configurationJson = $configuration | ConvertTo-Json -Depth 10
    [IO.File]::WriteAllText($credentialPath, $configurationJson, [Text.UTF8Encoding]::new($false))

    # Read only the expiry claim for display; do not print the token.
    $payload = $deployerToken.Split('.')[1].Replace('-', '+').Replace('_', '/')
    $payload = $payload.PadRight($payload.Length + ((4 - $payload.Length % 4) % 4), '=')
    $claims = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($payload)) | ConvertFrom-Json
    $expiresAt = [DateTimeOffset]::FromUnixTimeSeconds([long]$claims.exp).ToLocalTime()
    Write-Output "Kubeconfig created: $credentialPath"
    Write-Output "Token expires at: $($expiresAt.ToString('yyyy-MM-dd HH:mm:ss zzz'))"
    Write-Output 'Upload this file as the Jenkins Secret file credential: k8s-admin-portfolio'
} finally {
    $deployerToken = $null
    $configuration = $null
    $configurationJson = $null
}
