[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

Add-Type -AssemblyName System.Drawing

$desktopRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$iconDir = Join-Path $desktopRoot 'src-tauri\icons'
$iconPath = Join-Path $iconDir 'icon.ico'

New-Item -ItemType Directory -Path $iconDir -Force | Out-Null

$bitmap = New-Object System.Drawing.Bitmap 256, 256
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.Clear([System.Drawing.Color]::FromArgb(7, 16, 24))

$rect = New-Object System.Drawing.Rectangle 24, 24, 208, 208
$gradient = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    $rect,
    [System.Drawing.Color]::FromArgb(130, 245, 224),
    [System.Drawing.Color]::FromArgb(92, 178, 255),
    45
)
$graphics.FillEllipse($gradient, $rect)

$font = New-Object System.Drawing.Font('Segoe UI', 112, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
$textBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(7, 16, 24))
$format = New-Object System.Drawing.StringFormat
$format.Alignment = [System.Drawing.StringAlignment]::Center
$format.LineAlignment = [System.Drawing.StringAlignment]::Center
$graphics.DrawString('S', $font, $textBrush, (New-Object System.Drawing.RectangleF 0, 0, 256, 244), $format)

$pngStream = New-Object System.IO.MemoryStream
try {
    $bitmap.Save($pngStream, [System.Drawing.Imaging.ImageFormat]::Png)
    [byte[]]$pngBytes = $pngStream.ToArray()

    # Write a standards-compliant single-image ICO container whose image payload
    # is a 256x256 PNG. Width/height bytes are zero by ICO convention for 256 px.
    $fileStream = [System.IO.File]::Open($iconPath, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write)
    $writer = New-Object System.IO.BinaryWriter($fileStream)
    try {
        $writer.Write([UInt16]0)                 # ICONDIR.reserved
        $writer.Write([UInt16]1)                 # ICONDIR.type = icon
        $writer.Write([UInt16]1)                 # ICONDIR.count
        $writer.Write([Byte]0)                   # width 256
        $writer.Write([Byte]0)                   # height 256
        $writer.Write([Byte]0)                   # color count
        $writer.Write([Byte]0)                   # ICONDIRENTRY.reserved
        $writer.Write([UInt16]1)                 # planes
        $writer.Write([UInt16]32)                # bits per pixel
        $writer.Write([UInt32]$pngBytes.Length)  # payload size
        $writer.Write([UInt32]22)                # 6-byte header + 16-byte entry
        $writer.Write([Byte[]]$pngBytes)
        $writer.Flush()
    } finally {
        $writer.Dispose()
    }
} finally {
    $pngStream.Dispose()
    $format.Dispose()
    $textBrush.Dispose()
    $font.Dispose()
    $gradient.Dispose()
    $graphics.Dispose()
    $bitmap.Dispose()
}

if (-not (Test-Path $iconPath -PathType Leaf)) { throw 'Development icon was not produced.' }

$bytes = [System.IO.File]::ReadAllBytes($iconPath)
if ($bytes.Length -lt 22) { throw 'Generated ICO is too small.' }
if ($bytes[0] -ne 0 -or $bytes[1] -ne 0 -or $bytes[2] -ne 1 -or $bytes[3] -ne 0 -or $bytes[4] -ne 1 -or $bytes[5] -ne 0) {
    throw 'Generated ICO header is invalid.'
}
if ($bytes[9] -ne 0) { throw 'Generated ICONDIRENTRY reserved byte must be zero.' }

Write-Host "Generated standards-compliant unsigned-development Syntra icon at $iconPath"
