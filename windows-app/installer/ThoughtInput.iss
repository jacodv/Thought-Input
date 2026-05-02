; Inno Setup script for Thought Input (Windows).
; Per-user, no UAC, registers HKCU Run for auto-launch on login.
; Build:  ISCC.exe /DAppVersion=0.1.0 /DPublishDir=..\src\ThoughtInput\bin\Release\net10.0-windows\publish ThoughtInput.iss

#define AppName "Thought Input"
#define AppPublisher "Jaco De Villiers"
#define AppExe "ThoughtInput.exe"
#define AppId "{{D6B7E2D2-3B5A-4C97-8E0E-A1A1A1A1A1A1}}"

#ifndef AppVersion
  #define AppVersion "0.1.0"
#endif
#ifndef PublishDir
  #define PublishDir "..\src\ThoughtInput\bin\Release\net10.0-windows\publish"
#endif
#ifndef OutputDir
  #define OutputDir "Output"
#endif

[Setup]
AppId={#AppId}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher={#AppPublisher}
AppPublisherURL=https://github.com/jacodv/Thought-Input
AppSupportURL=https://github.com/jacodv/Thought-Input/issues
AppUpdatesURL=https://github.com/jacodv/Thought-Input/releases
DefaultDirName={localappdata}\ThoughtInput
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
DisableDirPage=yes
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
OutputDir={#OutputDir}
OutputBaseFilename=ThoughtInput-Windows-Setup
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
UninstallDisplayName={#AppName}
UninstallDisplayIcon={app}\{#AppExe}
SetupIconFile=assets\AppIcon.ico
WizardImageFile=assets\WizardLarge.bmp
WizardSmallImageFile=assets\WizardSmall.bmp
WizardImageStretch=no

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "launchOnLogin"; Description: "Launch {#AppName} when I log in"; GroupDescription: "Startup"

[Files]
Source: "{#PublishDir}\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

[Icons]
Name: "{group}\{#AppName}"; Filename: "{app}\{#AppExe}"
Name: "{group}\Uninstall {#AppName}"; Filename: "{uninstallexe}"

[Registry]
; --hidden makes the app start to the system tray without showing a window.
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Run"; \
    ValueType: string; ValueName: "ThoughtInput"; \
    ValueData: """{app}\{#AppExe}"" --hidden"; \
    Flags: uninsdeletevalue; Tasks: launchOnLogin

[Run]
Filename: "{app}\{#AppExe}"; \
    Description: "Launch {#AppName} now"; \
    Flags: nowait postinstall skipifsilent

[UninstallDelete]
; Leave secrets in Credential Manager and settings in %AppData% by default —
; users can remove those by hand if they want to fully purge the app.
Type: filesandordirs; Name: "{localappdata}\ThoughtInput\pending"
