#!/bin/bash
# Script para gerar relatórios e PDF de Testes de Carga V2.0

echo "1. Gerando séries de dados CSV e gráficos PNG..."
python3 /home/mark/.gemini/antigravity-ide/brain/2a4a1899-dde9-430f-ba6a-68899a16e782/scratch/generate_reports.py

echo "2. Compilando relatório HTML para PDF..."
# Gera o PDF usando o WeasyPrint (garante pixel-perfect de HTML para PDF)
weasyprint docs/sla/medicoes-sla-relatorio-v2.html docs/sla/medicoes-sla-relatorio-v2.pdf

echo "Sucesso! Os relatórios foram salvos em docs/sla/:"
echo " - CSVs e Gráficos: docs/sla/reports/"
echo " - Markdown: docs/sla/medicoes-sla-relatorio-v2.md"
echo " - HTML: docs/sla/medicoes-sla-relatorio-v2.html"
echo " - PDF: docs/sla/medicoes-sla-relatorio-v2.pdf"
