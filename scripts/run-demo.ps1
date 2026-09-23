# End-to-end demonstration. Requires both services running:
#   python-service : uvicorn app.main:app --port 8000   (run from python-service/)
#   spring-service : mvn spring-boot:run                (run from spring-service/)
# Usage: powershell -File scripts/run-demo.ps1

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$examples = Join-Path $root "examples"
$requests = Join-Path $root "data\requests"

Write-Host "== POST /answer =="
$answerRequest = Join-Path $examples "answer-request.json"
curl.exe -s -X POST http://localhost:8080/answer `
    -H "X-Caller-Id: atlas-employee-01" `
    -H "Content-Type: application/json" `
    --data "@$answerRequest" | Tee-Object -FilePath (Join-Path $examples "answer-response.json")

Write-Host "`n== POST /batches (mixed batch, includes a text-based PDF) =="
$batchRequest = Join-Path $examples "batch-request.json"
curl.exe -s -X POST http://localhost:8080/batches `
    -H "X-Caller-Id: atlas-employee-01" `
    -F "metadata=@$batchRequest;type=application/json" `
    -F "files=@$requests\request-01.txt" `
    -F "files=@$requests\request-02.pdf" `
    -F "files=@$requests\request-03.txt" `
    -F "files=@$requests\request-04.txt" `
    -F "files=@$requests\request-05.txt" `
    -F "files=@$requests\request-06.txt" `
    -F "files=@$requests\request-07.txt" `
    -F "files=@$requests\request-08.txt" | Tee-Object -FilePath (Join-Path $examples "batch-response.json")

Write-Host "`nSaved examples/answer-response.json and examples/batch-response.json"

# Pretty-print the saved responses for readability.
foreach ($name in @("answer-response.json", "batch-response.json")) {
    $path = Join-Path $examples $name
    $pretty = (Get-Content -Raw $path | python -m json.tool)
    [System.IO.File]::WriteAllText($path, ($pretty -join "`n") + "`n", (New-Object System.Text.UTF8Encoding $false))
}

