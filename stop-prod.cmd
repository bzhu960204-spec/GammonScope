@echo off
powershell -ExecutionPolicy Bypass -File "%~dp0stop-prod.ps1" %*
