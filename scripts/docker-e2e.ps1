# ===========================
# Docker unified run/verify script (Windows PowerShell)
# ===========================
param(
  [string]$APP = "api-payment-gateway",
  [string]$DB  = "mariadb",
  [string]$WM  = "wiremock"
)

$ErrorActionPreference = 'Stop'

$DB_NAME = "appdb"
$DB_USER = "appuser"
$DB_PASS = "app-pass"
$SCHEMA_LOCAL_PATH = ".\sql\scheme.sql"
$SCHEMA_IN_CONTAINER = "/tmp/scheme.sql"

Write-Host "== 1) Start containers ==" -ForegroundColor Cyan
# Start required services (create if not exists, reuse if already running)
docker compose up -d --build $DB $WM $APP

Write-Host "`n== 2) DB health check and apply schema/seeds ($DB) ==" -ForegroundColor Cyan
# Wait for DB to be ready (max ~60s)
$ready = $false
for ($i=0; $i -lt 30; $i++) {
  try {
    $null = docker exec -i $DB mariadb "-u$DB_USER" "-p$DB_PASS" -e "SELECT 1;" 2>$null
    if ($LASTEXITCODE -eq 0) { $ready=$true; break }
  } catch {}
  Start-Sleep -Seconds 2
}
if (-not $ready) { throw "MariaDB is not ready." }

# Copy schema into the container and apply
if (-not (Test-Path $SCHEMA_LOCAL_PATH)) { throw "Schema file not found: $SCHEMA_LOCAL_PATH" }
docker cp $SCHEMA_LOCAL_PATH "$($DB):$SCHEMA_IN_CONTAINER"
# Use shell redirection inside the container to avoid PowerShell parsing '<'
docker exec -i $DB sh -c "mariadb -u$DB_USER -p$DB_PASS $DB_NAME < $SCHEMA_IN_CONTAINER"

# Seed partner (idempotent)
docker exec -i $DB mariadb "-u$DB_USER" "-p$DB_PASS" $DB_NAME -e "INSERT IGNORE INTO partner(id,code,name,active) VALUES (1,'TEST','Test Partner',TRUE);"
# Ensure a current fee policy exists (idempotent)
docker exec -i $DB mariadb "-u$DB_USER" "-p$DB_PASS" $DB_NAME -e "SET @pid := (SELECT id FROM partner WHERE code='TEST' LIMIT 1); INSERT INTO partner_fee_policy (partner_id,effective_from,percentage,fixed_fee) SELECT @pid, NOW(6) - INTERVAL 1 DAY, 0.025000, 0 WHERE NOT EXISTS (SELECT 1 FROM partner_fee_policy WHERE partner_id=@pid AND effective_from<=NOW(6));"

# Verify tables and seed
docker exec -i $DB mariadb "-u$DB_USER" "-p$DB_PASS" $DB_NAME -e "SHOW TABLES;"
docker exec -i $DB mariadb "-u$DB_USER" "-p$DB_PASS" $DB_NAME -e "SELECT id,code,name,active FROM partner;"

Write-Host "`n== 3) WireMock health check ($WM) ==" -ForegroundColor Cyan
try {
  Invoke-RestMethod "http://localhost:18080/__admin/mappings" | Out-Null
  Write-Host "WireMock OK (localhost:18080)"
} catch {
  throw "Cannot reach WireMock (localhost:18080). Check port mapping/mappings."
}

# Reset WireMock request journal for a clean run
$wmResetOk = $false
try {
  Invoke-RestMethod -Method Delete -Uri "http://localhost:18080/__admin/requests" | Out-Null
  $wmResetOk = $true
} catch {
  # Fallback for older WireMock variants
  try {
    Invoke-RestMethod -Method Post -Uri "http://localhost:18080/__admin/requests/reset" | Out-Null
    $wmResetOk = $true
  } catch {
    Write-Host "Failed to reset WireMock requests (DELETE and POST variants): $($_.Exception.Message)" -ForegroundColor Yellow
  }
}
if ($wmResetOk) { Write-Host "WireMock request journal cleared." }

# Ensure mapping for /api/v1/pay/credit-card exists (idempotent upsert)
try {
  $mappings = Invoke-RestMethod -Method Get -Uri "http://localhost:18080/__admin/mappings"
  $exists = $false
  foreach ($m in $mappings.mappings) {
    if ($m.request -and ($m.request.urlPath -eq "/api/v1/pay/credit-card" -or $m.request.url -eq "/api/v1/pay/credit-card")) { $exists = $true; break }
  }
  if (-not $exists) {
    Write-Host "Register WireMock stub: POST /api/v1/pay/credit-card" -ForegroundColor Yellow
    $stub = @{ 
      request = @{ 
        method = "POST"; 
        urlPath = "/api/v1/pay/credit-card"; 
        headers = @{ "API-KEY" = @{ matches = "test-api-key" } }; 
        bodyPatterns = @(@{ matchesJsonPath = "$.enc" }) 
      }; 
      response = @{ 
        status = 200; 
        headers = @{ "Content-Type" = "application/json" }; 
        jsonBody = @{ approvalCode = "10183497"; approvedAt = "2025-10-18T14:10:19Z"; status = "APPROVED" } 
      } 
    } | ConvertTo-Json -Depth 8
    Invoke-RestMethod -Method Post -Uri "http://localhost:18080/__admin/mappings" -ContentType "application/json" -Body $stub | Out-Null
  }

  # Register cancel OK stub (API-KEY=test-api-key) idempotently
  $cancelOkExists = $false
  foreach ($m in $mappings.mappings) {
    if ($m.request -and ($m.request.urlPath -eq "/api/v1/pay/cancel" -or $m.request.url -eq "/api/v1/pay/cancel") -and $m.request.method -eq "POST") { $cancelOkExists = $true; break }
  }
  if (-not $cancelOkExists) {
    Write-Host "Register WireMock stub: POST /api/v1/pay/cancel (OK)" -ForegroundColor Yellow
    $ok = @{
      request=@{ method="POST"; url="/api/v1/pay/cancel"; headers=@{ "API-KEY"=@{ matches="test-api-key" } }; bodyPatterns=@(@{ matchesJsonPath="$.enc" }) };
      response=@{ status=200; headers=@{ "Content-Type"="application/json" }; jsonBody=@{ status="CANCELLED"; canceledAt="2025-10-18T15:00:00Z" } }
    } | ConvertTo-Json -Depth 8
    Invoke-RestMethod -Method Post -Uri http://localhost:18080/__admin/mappings -ContentType application/json -Body $ok | Out-Null
  }

  # Register cancel NG stub (API-KEY=fail-api-key) idempotently
  Write-Host "Register WireMock stub: POST /api/v1/pay/cancel (NG)" -ForegroundColor Yellow
  $ng = @{
    request=@{ method="POST"; url="/api/v1/pay/cancel"; headers=@{ "API-KEY"=@{ matches="fail-api-key" } } };
    response=@{ status=422; headers=@{ "Content-Type"="application/json" }; jsonBody=@{ code=422; errorCode="OVER_CANCEL"; message="Cancel amount exceeds approved"; referenceId="ref-cx" } }
  } | ConvertTo-Json -Depth 8
  Invoke-RestMethod -Method Post -Uri http://localhost:18080/__admin/mappings -ContentType application/json -Body $ng | Out-Null

} catch {
  Write-Host "WireMock stub registration failed: $($_.Exception.Message)" -ForegroundColor Yellow
}

Write-Host "`n== 4) App port discovery and health check ($APP) ==" -ForegroundColor Cyan
# Discover host port for container 8080
$portLine = docker compose port $APP 8080 2>$null
if ([string]::IsNullOrWhiteSpace($portLine)) {
  throw "No 8080 port mapping found for the app. Check compose ports settings."
}
$APP_PORT = $portLine.Split(":")[-1].Trim()
Write-Host "App Host Port: $APP_PORT"

# Wait for app health (max ~90s)
$healthy = $false
for ($i=0; $i -lt 45; $i++) {
  try {
    $h = Invoke-RestMethod "http://localhost:$APP_PORT/actuator/health" -TimeoutSec 2
    if ($h.status -eq "UP") { $healthy=$true; break }
  } catch {}
  Start-Sleep -Seconds 2
}
if (-not $healthy) {
  Write-Host "actuator/health not available yet, trying root ping /" -ForegroundColor Yellow
  try {
    Invoke-RestMethod "http://localhost:$APP_PORT/" -TimeoutSec 2 | Out-Null
    $healthy=$true
  } catch {}
}
if (-not $healthy) {
  docker logs $APP --tail=200
  throw "Application is not ready. See logs above."
}

Write-Host "`n== 5) Run payment create/list scenario ==" -ForegroundColor Cyan
# Resolve partnerId
$partnerId = docker exec -i $DB mariadb "-u$DB_USER" "-p$DB_PASS" $DB_NAME -N -e "SELECT id FROM partner WHERE code='TEST' LIMIT 1;"
$partnerId = $partnerId.Trim()
if ([string]::IsNullOrWhiteSpace($partnerId)) { throw "Could not fetch partnerId." }
Write-Host "partnerId: $partnerId"

# Create payment
$createBody = @{
  partnerId   = [int]$partnerId
  amount      = 20000
  cardBin     = "111122"
  cardLast4   = "3344"
  productName = "schema-fix-test"
} | ConvertTo-Json -Depth 5 -Compress

Write-Host "`n[POST] /api/v1/payments" -ForegroundColor Green
$createResp = Invoke-RestMethod -Method Post `
  -Uri ("http://localhost:{0}/api/v1/payments" -f $APP_PORT) `
  -ContentType "application/json" `
  -Body $createBody
$createResp | Format-List

# Cancel full
$paymentId = $createResp.id
Write-Host "`n[POST] /api/v1/payments/$paymentId/cancel (full)" -ForegroundColor Green
$cancelFullResp = Invoke-RestMethod -Method Post -Uri ("http://localhost:{0}/api/v1/payments/{1}/cancel" -f $APP_PORT, $paymentId) -ContentType "application/json" -Body (@{ reason="user_request" } | ConvertTo-Json)
$cancelFullResp | Format-List

# Cancel partial (should 422 if exceeds, else 200)
Write-Host "`n[POST] /api/v1/payments/$paymentId/cancel (partial=5000)" -ForegroundColor Green
try {
  $cancelPartResp = Invoke-RestMethod -Method Post -Uri ("http://localhost:{0}/api/v1/payments/{1}/cancel" -f $APP_PORT, $paymentId) -ContentType "application/json" -Body (@{ reason="partial"; cancelAmount=5000 } | ConvertTo-Json)
  $cancelPartResp | Format-List
} catch { $_ | Out-String | Write-Host }

# Query approved (limit=5)
Write-Host "`n[GET] /api/v1/payments?partnerId=...&status=APPROVED&limit=5" -ForegroundColor Green
$listResp = Invoke-RestMethod -Method Get `
  -Uri ("http://localhost:{0}/api/v1/payments?partnerId={1}&status=APPROVED&limit=5" -f $APP_PORT, $partnerId)
$listResp | Format-List

# 5b) Seed fee policy visible difference (TEST vs NEW)
Write-Host "`n== 5b) Seed fee policy difference and verify ==" -ForegroundColor Cyan

docker exec -i $DB mariadb "-u$DB_USER" "-p$DB_PASS" $DB_NAME -e @"
INSERT INTO partner (code,name,active)
SELECT 'NEW','New Partner',1 WHERE NOT EXISTS (SELECT 1 FROM partner WHERE code='NEW');

DELETE FROM partner_fee_policy WHERE partner_id IN (SELECT id FROM partner WHERE code IN ('TEST','NEW'));

INSERT INTO partner_fee_policy (partner_id,effective_from,percentage,fixed_fee)
SELECT p.id,NOW(6) - INTERVAL 1 DAY,0.025000,0 FROM partner p WHERE p.code='TEST';
INSERT INTO partner_fee_policy (partner_id,effective_from,percentage,fixed_fee)
SELECT p.id,NOW(6) - INTERVAL 1 DAY,0.030000,0 FROM partner p WHERE p.code='NEW';
"@

$pidNew = docker exec -i $DB mariadb "-u$DB_USER" "-p$DB_PASS" $DB_NAME -N -e "SELECT id FROM partner WHERE code='NEW' LIMIT 1;"
$pidNew = $pidNew.Trim()
if ([string]::IsNullOrWhiteSpace($pidNew)) { throw "Failed to resolve NEW partner id. Check seed step (5b) and DB credentials." }

$respTest = Invoke-RestMethod -Method Post -Uri ("http://localhost:{0}/api/v1/payments" -f $APP_PORT) -Headers @{ "Idempotency-Key"="fee-A" } -ContentType application/json -Body (@{ partnerId=[int]$partnerId; amount=20000; cardBin="111122"; cardLast4="3344"; productName="fee-A" } | ConvertTo-Json)
$respNew  = Invoke-RestMethod -Method Post -Uri ("http://localhost:{0}/api/v1/payments" -f $APP_PORT) -Headers @{ "Idempotency-Key"="fee-B" } -ContentType application/json -Body (@{ partnerId=[int]$pidNew; amount=20000; cardBin="111122"; cardLast4="5566"; productName="fee-B" } | ConvertTo-Json)

Write-Host "TEST feeAmount=$($respTest.feeAmount), netAmount=$($respTest.netAmount) | NEW feeAmount=$($respNew.feeAmount), netAmount=$($respNew.netAmount)"

# 5c) Cursor paging round-trip
Write-Host "`n== 5c) Cursor paging round-trip ==" -ForegroundColor Cyan
$p1 = Invoke-RestMethod ("http://localhost:{0}/api/v1/payments?partnerId=1&status=APPROVED&limit=5" -f $APP_PORT)
$p2 = Invoke-RestMethod ("http://localhost:{0}/api/v1/payments?partnerId=1&status=APPROVED&limit=5&cursor={1}" -f $APP_PORT, $p1.nextCursor)
Write-Host ("p1.hasNext={0}, p2.items[0].id follows p1.items[-1].id={1}" -f $p1.hasNext, ($p2.items[0].id -lt $p1.items[-1].id))

Write-Host "`n== 6) WireMock request journal ==" -ForegroundColor Cyan
try {
  $wmReqs = Invoke-RestMethod -Method Get -Uri "http://localhost:18080/__admin/requests"
  if ($wmReqs -and $wmReqs.requests -and $wmReqs.requests.Count -gt 0) {
    $summaries = @()
    foreach ($r in $wmReqs.requests) {
      $encPresent = $false
      try {
        if ($r.request -and $r.request.body) {
          $encPresent = ($r.request.body -match '\"enc\"')
        }
      } catch {}
      $summaries += [pscustomobject]@{
        Time      = $r.request.loggedDateString
        Method    = $r.request.method
        Url       = $r.request.url
        Status    = if ($r.response) { $r.response.status } else { $r.responseDefinition.status }
        Matched   = $r.wasMatched
        'API-KEY' = if ($r.request.headers.'API-KEY') { $r.request.headers.'API-KEY' } else { '' }
        EncBody   = $encPresent
      }
    }
    $summaries | Format-Table -AutoSize
  } else {
    Write-Host "(no requests recorded)"
  }
} catch {
  Write-Host "Failed to fetch WireMock journal: $($_.Exception.Message)" -ForegroundColor Yellow
}

Write-Host "`n== Done: App responses and WireMock journal should both look good. ==" -ForegroundColor Cyan
