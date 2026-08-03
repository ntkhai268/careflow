param(
    [Parameter(Mandatory = $true)][string]$AppointmentId,
    [Parameter(Mandatory = $true)][string]$RoomId,
    [Parameter(Mandatory = $true)][string]$PatientToken,
    [Parameter(Mandatory = $true)][string]$StaffToken,
    [Parameter(Mandatory = $true)][string]$DoctorToken,
    [string]$GatewayUrl = "http://localhost:8080/api"
)

$ErrorActionPreference = "Stop"

function Invoke-CareFlowJson {
    param(
        [string]$Method,
        [string]$Uri,
        [string]$Token,
        [object]$Body,
        [hashtable]$ExtraHeaders = @{}
    )
    $headers = @{ Authorization = "Bearer $Token" }
    foreach ($key in $ExtraHeaders.Keys) { $headers[$key] = $ExtraHeaders[$key] }
    $parameters = @{
        Method = $Method
        Uri = $Uri
        Headers = $headers
        ContentType = "application/json"
    }
    if ($null -ne $Body) { $parameters.Body = $Body | ConvertTo-Json -Depth 8 }
    Invoke-RestMethod @parameters
}

Write-Host "1/4 Lấy Visit Ticket từ Queue Service"
$ticketEnvelope = Invoke-CareFlowJson -Method Get `
    -Uri "$GatewayUrl/queues/tickets/appointment/$AppointmentId" `
    -Token $PatientToken
$ticket = $ticketEnvelope.data
$ticket | Format-List ticketCode, queueNumber, roomId, appointmentDate, status

Write-Host "2/4 Staff quét QR và check-in"
$checkInEnvelope = Invoke-CareFlowJson -Method Post `
    -Uri "$GatewayUrl/queues/check-in" `
    -Token $StaffToken `
    -Body @{ qrToken = $ticket.qrToken; roomId = $RoomId; queueClass = "NORMAL" }
$checkInEnvelope.data | Format-List queueNumber, queueStatus, effectivePosition

Write-Host "3/4 Đọc active queue của phòng"
$dashboardEnvelope = Invoke-CareFlowJson -Method Get `
    -Uri "$GatewayUrl/queues/rooms/$RoomId/active" `
    -Token $DoctorToken
$dashboardEnvelope.data.entries | Format-Table queueNumber, queueStatus, effectivePosition

Write-Host "4/4 Bác sĩ gọi lượt tiếp theo"
$calledEnvelope = Invoke-CareFlowJson -Method Post `
    -Uri "$GatewayUrl/queues/rooms/$RoomId/call-next" `
    -Token $DoctorToken `
    -Body $null `
    -ExtraHeaders @{ "Idempotency-Key" = [guid]::NewGuid().ToString() }
$calledEnvelope.data | Format-List queueNumber, queueStatus, calledAt
