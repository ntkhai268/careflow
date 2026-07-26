@echo off
if "%1"=="dev" (
    flutter run --dart-define-from-file=.env.dev
) else if "%1"=="prod" (
    flutter run --dart-define-from-file=.env.prod
) else if "%1"=="mock" (
    flutter run --dart-define-from-file=.env.dev --dart-define=USE_MOCK_AUTH=true
) else (
    echo Usage: run dev ^| prod ^| mock
)
