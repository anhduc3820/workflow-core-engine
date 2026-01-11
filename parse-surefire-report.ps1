param(
    [Parameter(Mandatory=$true)]
    [string]$ReportDir
)

$total = 0
$fail  = 0
$err   = 0
$skip  = 0

Get-ChildItem "$ReportDir\TEST-*.xml" | ForEach-Object {
    [xml]$xml = Get-Content $_
    $suite = $xml.testsuite

    $total += [int]$suite.tests
    $fail  += [int]$suite.failures
    $err   += [int]$suite.errors
    $skip  += [int]$suite.skipped
}

$pass = $total - $fail - $err - $skip

Write-Host ("Total   : {0}" -f $total)
Write-Host ("Passed  : {0}" -f $pass)
Write-Host ("Failed  : {0}" -f $fail)
Write-Host ("Errors  : {0}" -f $err)
Write-Host ("Skipped : {0}" -f $skip)

Write-Host ""
Write-Host "========================================"
Write-Host "FAILED / ERROR TEST DETAILS"
Write-Host "========================================"

Get-ChildItem "$ReportDir\TEST-*.xml" | ForEach-Object {
    [xml]$xml = Get-Content $_
    foreach ($tc in $xml.testsuite.testcase) {
        if ($tc.failure -or $tc.error) {
            Write-Host "----------------------------------------"
            Write-Host ("Test File : {0}" -f $_.Name)
            Write-Host ("Class     : {0}" -f $tc.classname)
            Write-Host ("Test      : {0}" -f $tc.name)
            Write-Host "----------------------------------------"

            if ($tc.failure) { $tc.failure.'#text' }
            if ($tc.error)   { $tc.error.'#text' }
        }
    }
}
