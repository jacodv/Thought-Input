# Installer assets

CI generates `AppIcon.ico` from `windows-app/src/ThoughtInput/Resources/AppIcon.png`
using ImageMagick before invoking ISCC, then passes the path via `/DIconFile=…`.

```sh
magick AppIcon.png -define icon:auto-resize=256,128,64,48,32,16 AppIcon.ico
```

Wizard imagery uses Inno Setup's defaults — fine for v1.
