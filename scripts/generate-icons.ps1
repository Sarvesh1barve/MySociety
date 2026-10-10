Add-Type -AssemblyName System.Drawing
$target = Join-Path $PSScriptRoot '../frontend/public/icons'
New-Item -ItemType Directory -Force $target | Out-Null
foreach ($size in @(192,512)) {
 $bitmap = New-Object Drawing.Bitmap($size,$size)
 $graphics = [Drawing.Graphics]::FromImage($bitmap)
 $graphics.SmoothingMode = 'AntiAlias'
 $graphics.Clear([Drawing.ColorTranslator]::FromHtml('#28654e'))
 $pen = New-Object Drawing.Pen([Drawing.ColorTranslator]::FromHtml('#f0f4e8'), ($size * 0.022))
 $graphics.DrawRectangle($pen, [single]($size*0.25), [single]($size*0.35), [single]($size*0.24), [single]($size*0.39))
 $graphics.DrawRectangle($pen, [single]($size*0.49), [single]($size*0.24), [single]($size*0.25), [single]($size*0.50))
 foreach ($row in @(0.43,0.53,0.63)) { $graphics.DrawLine($pen,[single]($size*0.33),[single]($size*$row),[single]($size*0.40),[single]($size*$row)) }
 foreach ($row in @(0.34,0.44,0.54,0.64)) { $graphics.DrawLine($pen,[single]($size*0.57),[single]($size*$row),[single]($size*0.65),[single]($size*$row)) }
 $graphics.DrawLine($pen,[single]($size*0.20),[single]($size*0.75),[single]($size*0.80),[single]($size*0.75))
 $bitmap.Save((Join-Path $target "icon-$size.png"),[Drawing.Imaging.ImageFormat]::Png)
 $pen.Dispose(); $graphics.Dispose(); $bitmap.Dispose()
}
