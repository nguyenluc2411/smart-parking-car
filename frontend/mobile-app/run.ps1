# Chạy mobile-app với cấu hình đúng cho từng nền tảng (mặc định: API THẬT).
#
# Cách dùng:
#   .\run.ps1 web        # Chrome  -> http://localhost:5000 (đã được backend cho phép CORS)
#   .\run.ps1 emulator   # Android emulator -> host qua 10.0.2.2
#   .\run.ps1 desktop    # Windows desktop  -> localhost, không vướng CORS
#   .\run.ps1 web -Mock  # thêm -Mock để chạy data giả (không cần backend)
#
# Đăng nhập (data thật): admin / ChangeMe123!
# Yêu cầu: docker compose đang chạy (admin/parking/billing healthy).

param(
  [Parameter(Mandatory = $true)]
  [ValidateSet('web', 'emulator', 'desktop')]
  [string]$Target,
  [switch]$Mock
)

$useMock = if ($Mock) { 'true' } else { 'false' }
$common = @("-t", "lib/main_operator.dart", "--dart-define=USE_MOCK=$useMock")

switch ($Target) {
  'web' {
    # Cổng 5000 đã nằm trong APP_CORS_ALLOWED_ORIGINS của backend.
    $args = $common + @("-d", "chrome", "--web-port=5000")
  }
  'emulator' {
    # Android emulator: localhost = chính máy ảo, nên host máy thật phải là 10.0.2.2.
    $args = $common + @(
      "--dart-define=ADMIN_API_URL=http://10.0.2.2:8083",
      "--dart-define=PARKING_API_URL=http://10.0.2.2:8081",
      "--dart-define=BILLING_API_URL=http://10.0.2.2:8082"
    )
  }
  'desktop' {
    # App native không bị CORS, localhost gọi thẳng được.
    $args = $common + @("-d", "windows")
  }
}

Write-Host "flutter run $($args -join ' ')" -ForegroundColor Cyan
flutter run @args
