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

$handle = $bitmap.GetHicon()
$icon = [System.Drawing.Icon]::FromHandle($handle)
$stream = [System.IO.File]::Open($iconPath, [System.IO.FileMode]::Create)
try {
    $icon.Save($stream)
} finally {
    $stream.Dispose()
    $icon.Dispose()
    $format.Dispose()
    $textBrush.Dispose()
    $font.Dispose()
    $gradient.Dispose()
    $graphics.Dispose()
    $bitmap.Dispose()
}

if (-not (Test-Path $iconPath -PathType Leaf)) { throw 'Development icon was not produced.' }
Write-Host "Generated unsigned-development Syntra icon at $iconPath"
