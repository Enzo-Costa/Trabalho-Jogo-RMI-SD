@echo off
echo Compilando todos os arquivos Java...
javac *.java
if %ERRORLEVEL% EQU 0 (
    echo [SUCESSO] Compilacao concluida sem erros!
) else (
    echo [ERRO] Falha ao compilar. Verifique se o JDK esta instalado e configurado no PATH.
)
pause
