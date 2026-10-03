@echo off
setlocal
set "RAG=%~dp0..\aegis-kb-rag-tools"
cd /d "%RAG%"
set "PYTHONPATH=."
"%RAG%\.venv\Scripts\python.exe" -m uvicorn rag_tools.main:app --host 0.0.0.0 --port 18080
endlocal
