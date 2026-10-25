#!/bin/bash
echo "Compilando arquivos Java para Linux..."
javac *.java
if [ $? -eq 0 ]; then
    echo "[SUCESSO] Compilação concluída com sucesso!"
else
    echo "[ERRO] Ocorreu uma falha na compilação."
fi
