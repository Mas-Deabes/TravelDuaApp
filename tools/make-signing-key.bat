@echo off
setlocal
echo.
echo   Travel Duaa - make a new signing key
echo   ====================================
echo.

rem keytool comes with Android Studio
set "KEYTOOL=C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"
if not exist "%KEYTOOL%" (
  where keytool >nul 2>nul
  if errorlevel 1 (
    echo Could not find keytool. Install Android Studio first, then run this again.
    pause
    exit /b 1
  )
  set "KEYTOOL=keytool"
)

rem The key is saved OUTSIDE the project, so it can never be committed to GitHub.
set "OUT=%USERPROFILE%\Documents\TravelDuaa-signing-key"
if not exist "%OUT%" mkdir "%OUT%"
if exist "%OUT%\travelduaa.keystore" (
  echo A key already exists in:
  echo   %OUT%
  echo Move or delete it first if you really want a new one.
  pause
  exit /b 1
)

echo The new key will be saved in:
echo   %OUT%
echo.
echo Choose a strong password (at least 6 characters) and type it twice.
echo WRITE IT DOWN: you need it for the GitHub secrets, and for every future update.
echo.
"%KEYTOOL%" -genkeypair -keystore "%OUT%\travelduaa.keystore" -storetype PKCS12 -alias duaa -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Travel Duaa"
if errorlevel 1 (
  echo.
  echo Something went wrong, no key was made.
  pause
  exit /b 1
)

rem Turn the key into text for the KEYSTORE_BASE64 secret, and copy it to the clipboard.
powershell -NoProfile -Command "$b=[Convert]::ToBase64String([IO.File]::ReadAllBytes('%OUT%\travelduaa.keystore')); Set-Content -NoNewline -Path '%OUT%\KEYSTORE_BASE64.txt' -Value $b; Set-Clipboard -Value $b"

echo.
echo   Done!
echo.
echo   Key file:      %OUT%\travelduaa.keystore
echo                  Back it up (e.g. USB stick). Without it you can't release updates.
echo   Key as text:   %OUT%\KEYSTORE_BASE64.txt
echo                  Already copied to your clipboard.
echo.
echo   Next, on GitHub: Settings - Secrets and variables - Actions - New repository secret
echo     KEYSTORE_BASE64    paste (Ctrl+V)
echo     KEYSTORE_PASSWORD  the password you just chose
echo.
start "" "%OUT%"
pause
