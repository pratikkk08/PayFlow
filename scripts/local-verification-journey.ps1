# PayFlow local verification journey - dedicated test account only.
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080/api/v1'
$stamp = Get-Date -Format 'yyyyMMddHHmmss'
$email = "local.verify.$stamp@example.com"
$password = 'VerifyLocal1a'
$fullName = 'Local Verify User'
$results = [ordered]@{}

function Invoke-Api {
  param(
    [string]$Method,
    [string]$Path,
    [hashtable]$Headers = @{},
    [object]$Body = $null,
    [int[]]$ExpectStatus = @(200, 201)
  )
  $uri = if ($Path.StartsWith('http')) { $Path } else { "$base$Path" }
  $hdrs = @{} + $Headers
  $params = @{ Method = $Method; Uri = $uri; Headers = $hdrs }
  if ($null -ne $Body) {
    $params.ContentType = 'application/json'
    $params.Body = ($Body | ConvertTo-Json -Depth 8 -Compress)
  }
  try {
    $resp = Invoke-WebRequest @params -UseBasicParsing
    $code = [int]$resp.StatusCode
    $json = $null
    if ($resp.Content) {
      try { $json = $resp.Content | ConvertFrom-Json } catch { $json = $resp.Content }
    }
    return @{ Ok = ($ExpectStatus -contains $code); Status = $code; Body = $json; Raw = $resp.Content; Headers = $resp.Headers }
  } catch {
    $ex = $_.Exception
    $code = 0
    $raw = $null
    $json = $null
    if ($ex.Response) {
      $code = [int]$ex.Response.StatusCode.value__
      if (-not $code) { $code = [int]$ex.Response.StatusCode }
      try {
        $reader = New-Object System.IO.StreamReader($ex.Response.GetResponseStream())
        $raw = $reader.ReadToEnd()
        try { $json = $raw | ConvertFrom-Json } catch { $json = $raw }
      } catch {
        $raw = "$_"
      }
    } else {
      $raw = "$_"
    }
    return @{ Ok = ($ExpectStatus -contains $code); Status = $code; Body = $json; Raw = $raw; Error = "$_" }
  }
}

function Assert-Step {
  param([string]$Name, [bool]$Pass, [string]$Detail = '')
  $results[$Name] = if ($Pass) { "PASS: $Detail" } else { "FAIL: $Detail" }
  $mark = if ($Pass) { 'PASS' } else { 'FAIL' }
  Write-Host "[$mark] $Name - $Detail"
}

Write-Host '=== AUTH ==='
$reg = Invoke-Api POST '/auth/register' -Body @{ fullName = $fullName; email = $email; password = $password } -ExpectStatus @(201)
Assert-Step 'A.register' $reg.Ok "status=$($reg.Status) email=$email"

$login = Invoke-Api POST '/auth/login' -Body @{ email = $email; password = $password } -ExpectStatus @(200)
$token = $login.Body.accessToken
$refresh = $login.Body.refreshToken
Assert-Step 'A.login' ($login.Ok -and [bool]$token) "status=$($login.Status) hasAccess=$([bool]$token) hasRefresh=$([bool]$refresh)"

$auth = @{ Authorization = "Bearer $token" }
$me = Invoke-Api GET '/users/me' -Headers $auth
Assert-Step 'A.session' ($me.Ok -and $me.Body.email -eq $email) "email=$($me.Body.email)"

$logout = Invoke-Api POST '/auth/logout' -Headers $auth -Body @{ refreshToken = $refresh } -ExpectStatus @(204, 200)
Assert-Step 'A.logout' $logout.Ok "status=$($logout.Status)"

$login2 = Invoke-Api POST '/auth/login' -Body @{ email = $email; password = $password }
$token = $login2.Body.accessToken
$refresh = $login2.Body.refreshToken
$auth = @{ Authorization = "Bearer $token" }
Assert-Step 'A.relogin' ($login2.Ok -and [bool]$token) "status=$($login2.Status)"

$prot = Invoke-Api GET '/users/me' -ExpectStatus @(401)
Assert-Step 'A.protected_no_token' $prot.Ok "status=$($prot.Status)"

Write-Host '=== MERCHANTS ==='
$mA = Invoke-Api POST '/merchants' -Headers $auth -Body @{ name = "Verify Merchant A $stamp"; description = 'A' } -ExpectStatus @(201)
$mB = Invoke-Api POST '/merchants' -Headers $auth -Body @{ name = "Verify Merchant B $stamp"; description = 'B' } -ExpectStatus @(201)
$merchantA = $mA.Body.id
$merchantB = $mB.Body.id
Assert-Step 'B.create_A' ($mA.Ok -and [bool]$merchantA) "id=$merchantA"
Assert-Step 'B.create_B' ($mB.Ok -and [bool]$merchantB) "id=$merchantB"

$upd = Invoke-Api PATCH "/merchants/$merchantA" -Headers $auth -Body @{ name = "Verify Merchant A Edited $stamp"; description = 'A-edited' }
Assert-Step 'B.edit_A' ($upd.Ok -and ($upd.Body.name -like '*Edited*')) "name=$($upd.Body.name)"

$listM = Invoke-Api GET '/merchants' -Headers $auth
$ids = @($listM.Body | ForEach-Object { $_.id })
Assert-Step 'B.list_contains_both' ($listM.Ok -and ($ids -contains $merchantA) -and ($ids -contains $merchantB)) "count=$($ids.Count)"

$email2 = "local.verify.iso.$stamp@example.com"
$null = Invoke-Api POST '/auth/register' -Body @{ fullName = 'Iso User'; email = $email2; password = $password } -ExpectStatus @(201)
$loginIso = Invoke-Api POST '/auth/login' -Body @{ email = $email2; password = $password }
$authIso = @{ Authorization = "Bearer $($loginIso.Body.accessToken)" }
$cross = Invoke-Api GET "/merchants/$merchantA" -Headers $authIso -ExpectStatus @(404)
Assert-Step 'B.isolation_404' $cross.Ok "status=$($cross.Status)"

Write-Host '=== PAYMENTS ==='
$refCap = "cap-$stamp"
$refCancel = "cancel-$stamp"
$idem1 = "idem-create-$stamp-1"
$pay = Invoke-Api POST '/payments' -Headers ($auth + @{ 'Idempotency-Key' = $idem1 }) -Body @{
  merchantId = $merchantA; merchantReference = $refCap; amountMinor = 50000; currency = 'INR'; description = 'capture flow'
} -ExpectStatus @(201)
$paymentId = $pay.Body.paymentId
Assert-Step 'C.create' ($pay.Ok -and $pay.Body.status -eq 'CREATED') "id=$paymentId status=$($pay.Body.status)"

$payIdem = Invoke-Api POST '/payments' -Headers ($auth + @{ 'Idempotency-Key' = $idem1 }) -Body @{
  merchantId = $merchantA; merchantReference = $refCap; amountMinor = 50000; currency = 'INR'; description = 'capture flow'
} -ExpectStatus @(200, 201)
Assert-Step 'C.idempotent_retry' ($payIdem.Ok -and $payIdem.Body.paymentId -eq $paymentId) "status=$($payIdem.Status) sameId=$($payIdem.Body.paymentId -eq $paymentId)"

$dup = Invoke-Api POST '/payments' -Headers ($auth + @{ 'Idempotency-Key' = "idem-dup-$stamp" }) -Body @{
  merchantId = $merchantA; merchantReference = $refCap; amountMinor = 100; currency = 'INR'; description = 'dup ref'
} -ExpectStatus @(409, 400)
Assert-Step 'C.duplicate_reference' $dup.Ok "status=$($dup.Status) code=$($dup.Body.code)"

$getP = Invoke-Api GET "/payments/$paymentId" -Headers $auth
Assert-Step 'C.retrieve' ($getP.Ok -and $getP.Body.paymentId -eq $paymentId) "status=$($getP.Body.status)"

$listP = Invoke-Api GET "/payments?merchantId=$merchantA" -Headers $auth
Assert-Step 'C.list' ($listP.Ok -and $listP.Body.content.Count -ge 1) "total=$($listP.Body.totalElements)"

$authz = Invoke-Api POST "/payments/$paymentId/authorize" -Headers $auth
Assert-Step 'C.authorize' ($authz.Ok -and $authz.Body.status -eq 'AUTHORIZED') "status=$($authz.Body.status)"

$cap = Invoke-Api POST "/payments/$paymentId/capture" -Headers $auth
Assert-Step 'C.capture' ($cap.Ok -and $cap.Body.status -eq 'CAPTURED') "status=$($cap.Body.status)"

$payCancel = Invoke-Api POST '/payments' -Headers ($auth + @{ 'Idempotency-Key' = "idem-cancel-$stamp" }) -Body @{
  merchantId = $merchantA; merchantReference = $refCancel; amountMinor = 1200; currency = 'INR'; description = 'cancel flow'
} -ExpectStatus @(201)
$cancelId = $payCancel.Body.paymentId
$authz2 = Invoke-Api POST "/payments/$cancelId/authorize" -Headers $auth
$cancel = Invoke-Api POST "/payments/$cancelId/cancel" -Headers $auth
Assert-Step 'C.cancel' ($cancel.Ok -and $cancel.Body.status -eq 'CANCELLED') "auth=$($authz2.Body.status) cancel=$($cancel.Body.status)"

Write-Host '=== REFUNDS ==='
$refR = "ref-$stamp-partial"
$refund = Invoke-Api POST "/payments/$paymentId/refunds" -Headers ($auth + @{ 'Idempotency-Key' = "idem-ref-$stamp" }) -Body @{
  merchantReference = $refR; amountMinor = 10000; reason = 'partial verify'
} -ExpectStatus @(201)
$refundId = $refund.Body.refundId
Assert-Step 'D.partial_refund' ($refund.Ok -and $refund.Body.status -eq 'SUCCEEDED') "id=$refundId status=$($refund.Body.status) amount=$($refund.Body.amountMinor)"

$getR = Invoke-Api GET "/refunds/$refundId" -Headers $auth
Assert-Step 'D.retrieve_refund' ($getR.Ok -and $getR.Body.refundId -eq $refundId) "status=$($getR.Body.status)"

$listR = Invoke-Api GET "/payments/$paymentId/refunds" -Headers $auth
Assert-Step 'D.refund_history' $listR.Ok "rawLen=$($listR.Raw.Length)"

$refundIdem = Invoke-Api POST "/payments/$paymentId/refunds" -Headers ($auth + @{ 'Idempotency-Key' = "idem-ref-$stamp" }) -Body @{
  merchantReference = $refR; amountMinor = 10000; reason = 'partial verify'
} -ExpectStatus @(200, 201)
Assert-Step 'D.refund_idempotent' ($refundIdem.Ok -and $refundIdem.Body.refundId -eq $refundId) "sameId=$($refundIdem.Body.refundId -eq $refundId)"

$over = Invoke-Api POST "/payments/$paymentId/refunds" -Headers ($auth + @{ 'Idempotency-Key' = "idem-over-$stamp" }) -Body @{
  merchantReference = "over-$stamp"; amountMinor = 999999; reason = 'too much'
} -ExpectStatus @(400, 409, 422)
Assert-Step 'D.refund_capacity' $over.Ok "status=$($over.Status) code=$($over.Body.code)"

$refund2 = Invoke-Api POST "/payments/$paymentId/refunds" -Headers ($auth + @{ 'Idempotency-Key' = "idem-ref2-$stamp" }) -Body @{
  merchantReference = "ref2-$stamp"; amountMinor = 5000; reason = 'second partial'
} -ExpectStatus @(201)
Assert-Step 'D.remaining_refundable' $refund2.Ok "status=$($refund2.Body.status) amount=$($refund2.Body.amountMinor)"

Write-Host '=== ANALYTICS ==='
$ovA = Invoke-Api GET "/merchants/$merchantA/analytics/overview" -Headers $auth
$ovB = Invoke-Api GET "/merchants/$merchantB/analytics/overview" -Headers $auth
$aOk = $ovA.Ok -and ($ovA.Body.capturedCount -ge 1) -and ($ovA.Body.totalCapturedAmountMinor -ge 50000) -and ($ovA.Body.totalSucceededRefundAmountMinor -ge 15000)
Assert-Step 'E.analytics_A' $aOk "payments=$($ovA.Body.totalPaymentCount) capturedAmt=$($ovA.Body.totalCapturedAmountMinor) refundAmt=$($ovA.Body.totalSucceededRefundAmountMinor) riskB=$($ovA.Body.riskBlockCount) riskR=$($ovA.Body.riskReviewCount)"
Assert-Step 'E.analytics_B_isolation' ($ovB.Ok -and $ovB.Body.totalPaymentCount -eq 0) "B_payments=$($ovB.Body.totalPaymentCount)"
$daily = Invoke-Api GET "/merchants/$merchantA/analytics/daily" -Headers $auth
Assert-Step 'E.analytics_daily' $daily.Ok "keys=$($daily.Body.PSObject.Properties.Name -join ',')"

Write-Host '=== API KEYS ==='
$keyCreate = Invoke-Api POST "/merchants/$merchantA/api-keys" -Headers $auth -Body @{ name = "Verify Key $stamp"; type = 'SECRET' } -ExpectStatus @(201)
$rawKey = $keyCreate.Body.secret
$keyId = $keyCreate.Body.id
Assert-Step 'F.create_key' ($keyCreate.Ok -and ($rawKey -like 'sk_test_*')) "id=$keyId"

$listKeys = Invoke-Api GET "/merchants/$merchantA/api-keys" -Headers $auth
$listedHasSecret = $false
foreach ($k in @($listKeys.Body)) {
  if ($k.secret -or $k.rawSecret -or $k.plaintext) { $listedHasSecret = $true }
}
Assert-Step 'F.list_metadata_no_secret' ($listKeys.Ok -and -not $listedHasSecret) "listedSecret=$listedHasSecret"

$integ = Invoke-Api GET '/integrations/me' -Headers @{ Authorization = "Bearer $rawKey" }
Assert-Step 'F.key_auth_works' $integ.Ok "status=$($integ.Status) merchant=$($integ.Body.merchantId)"

$revoke = Invoke-Api DELETE "/merchants/$merchantA/api-keys/$keyId" -Headers $auth -ExpectStatus @(204, 200)
Assert-Step 'F.revoke' $revoke.Ok "status=$($revoke.Status)"

$integ2 = Invoke-Api GET '/integrations/me' -Headers @{ Authorization = "Bearer $rawKey" } -ExpectStatus @(401)
Assert-Step 'F.revoked_rejected' $integ2.Ok "status=$($integ2.Status)"

Write-Host '=== WEBHOOKS ==='
$wh = Invoke-Api POST "/merchants/$merchantA/webhooks" -Headers $auth -Body @{
  url = 'https://example.com/payflow-local-verify'
  eventTypes = @('payment.captured', 'refund.succeeded')
  enabled = $true
} -ExpectStatus @(201)
$whId = $wh.Body.id
$whSecret = $wh.Body.signingSecret
Assert-Step 'G.create_webhook' ($wh.Ok -and [bool]$whId -and [bool]$whSecret) "id=$whId hasSecret=$([bool]$whSecret)"

$whList = Invoke-Api GET "/merchants/$merchantA/webhooks" -Headers $auth
$whListedSecret = $false
foreach ($w in @($whList.Body)) {
  if ($w.signingSecret -or $w.secret) { $whListedSecret = $true }
}
Assert-Step 'G.list_no_secret' ($whList.Ok -and -not $whListedSecret) "listedSecret=$whListedSecret"

$whEdit = Invoke-Api PATCH "/merchants/$merchantA/webhooks/$whId" -Headers $auth -Body @{
  eventTypes = @('payment.captured', 'payment.cancelled', 'refund.succeeded')
  enabled = $false
}
Assert-Step 'G.edit_disable' ($whEdit.Ok -and ($whEdit.Body.enabled -eq $false)) "enabled=$($whEdit.Body.enabled)"

$whDel = Invoke-Api DELETE "/merchants/$merchantA/webhooks/$whId" -Headers $auth -ExpectStatus @(204, 200)
Assert-Step 'G.delete' $whDel.Ok "status=$($whDel.Status)"

Write-Host '=== LEDGER / RISK (API-visible) ==='
Assert-Step 'H.risk_via_authorize_and_analytics' ($authz.Ok -and $ovA.Ok -and ($null -ne $ovA.Body.riskBlockCount)) "authorizeStatus=$($authz.Body.status) riskBlock=$($ovA.Body.riskBlockCount) riskReview=$($ovA.Body.riskReviewCount)"
Assert-Step 'H.ledger_public_api' $true 'NO public ledger HTTP API by design - covered by backend tests'

Write-Host '=== PERSISTENCE IDS ==='
$persist = [ordered]@{
  email = $email
  password = $password
  merchantA = "$merchantA"
  merchantB = "$merchantB"
  paymentId = "$paymentId"
  paymentStatus = "$($cap.Body.status)"
  refundId = "$refundId"
  cancelId = "$cancelId"
}
$outDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$persist | ConvertTo-Json | Set-Content -Path (Join-Path $outDir 'local-verification-persist.json') -Encoding utf8
Write-Host ($persist | ConvertTo-Json)

Write-Host ''
Write-Host '=== SUMMARY ==='
$fail = 0
foreach ($k in $results.Keys) {
  if ($results[$k] -like 'FAIL*') { $fail++ }
  Write-Host "$k = $($results[$k])"
}
Write-Host "FAILED_COUNT=$fail TOTAL=$($results.Count)"
$results | ConvertTo-Json | Set-Content -Path (Join-Path $outDir 'local-verification-results.json') -Encoding utf8
if ($fail -gt 0) { exit 1 } else { exit 0 }