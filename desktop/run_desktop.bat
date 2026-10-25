@echo off
title KovanLLM Station
cd /d "%~dp0"

echo ===================================================
echo             KovanLLM Station v1.1
echo       Created by Aselens Software(SM)
echo ===================================================
echo.

where python >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [HATA / ERROR] Python bulunamadi! Lutfen Python 3.10+ yukleyin ve PATH'e ekleyin.
    echo Python was not found! Please install Python 3.10+ and add it to PATH.
    pause
    exit /b 1
)

if not exist ".venv" (
    echo [INFO] Sanal ortam olusturuluyor / Creating virtual environment...
    python -m venv .venv
)

call .venv\Scripts\activate.bat

echo [INFO] Bagimliliklar kontrol ediliyor / Checking requirements...
pip install -r requirements.txt --quiet

echo.
echo [INFO] KovanLLM Station baslatiliyor / Launching KovanLLM Station...
python desktop_gui.py

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [HATA / ERROR] Uygulama beklenmeyen bir sekilde kapandi.
    pause
)
