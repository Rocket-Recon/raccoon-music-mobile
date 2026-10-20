"""Desenha o icone do app (fundo laranja e uma nota musical branca) nos tamanhos que o Android pede.

Roda sozinho no GitHub antes de compilar: python3 gerar_icones.py
"""
from __future__ import annotations

import struct
import zlib
from pathlib import Path

COR_FUNDO = "#e9532e"
PASTAS = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def _dentro(x: float, y: float) -> bool:
    cx, cy, rx, ry = 0.40, 0.66, 0.115, 0.085
    if ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1:
        return True
    if 0.495 <= x <= 0.545 and 0.26 <= y <= 0.67:
        return True
    if 0.545 < x <= 0.68:
        topo = 0.26 + (x - 0.545) * 1.5
        return topo <= y <= topo + 0.11
    return False


def _bloco(tipo: bytes, dados: bytes) -> bytes:
    return struct.pack(">I", len(dados)) + tipo + dados + struct.pack(">I", zlib.crc32(tipo + dados) & 0xFFFFFFFF)


def icone_png(lado: int, cor: str = COR_FUNDO) -> bytes:
    fundo = tuple(int(cor.lstrip("#")[i:i + 2], 16) for i in (0, 2, 4))
    linhas = []
    for j in range(lado):
        linha = bytearray()
        for i in range(lado):
            cobertura = sum(_dentro((i + dx) / lado, (j + dy) / lado) for dx in (0.25, 0.75) for dy in (0.25, 0.75)) / 4
            linha += bytes(round(f + (255 - f) * cobertura) for f in fundo)
        linhas.append(bytes(linha))
    bruto = b"".join(b"\x00" + l for l in linhas)
    return (b"\x89PNG\r\n\x1a\n" + _bloco(b"IHDR", struct.pack(">IIBBBBB", lado, lado, 8, 2, 0, 0, 0))
            + _bloco(b"IDAT", zlib.compress(bruto, 9)) + _bloco(b"IEND", b""))


def gerar(raiz: Path) -> None:
    """Cria so o que falta: se o publicar_mobile.py ja colocou o icone do app do PC, ele e mantido."""
    res = raiz / "app" / "src" / "main" / "res"
    destinos = {res / f"mipmap-{nome}" / "ic_launcher.png": lado for nome, lado in PASTAS.items()}
    destinos[res / "drawable-nodpi" / "logo.png"] = 256
    for arquivo, lado in destinos.items():
        if not arquivo.exists():
            arquivo.parent.mkdir(parents=True, exist_ok=True)
            arquivo.write_bytes(icone_png(lado))


if __name__ == "__main__":
    gerar(Path(__file__).resolve().parent)
    print("Icones gerados.")
