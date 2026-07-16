[Setup]
AppName=CloudStream
AppVersion=0.1.2-pre-alpha
AppPublisher=CloudStream
AppPublisherURL=https://github.com/errorcode26/cloudstream-desktop-unofficial
AppSupportURL=https://github.com/errorcode26/cloudstream-desktop-unofficial
AppUpdatesURL=https://github.com/errorcode26/cloudstream-desktop-unofficial
DefaultDirName={autopf}\CloudStream
DefaultGroupName=CloudStream
AllowNoIcons=yes
; Output directory for the compiled installer
OutputDir=..\desktop-app\build\outputs
OutputBaseFilename=CloudStream-Setup
Compression=lzma2/ultra64
SolidCompression=yes
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
; Copy all files and folders from the AppImage output
Source: "..\desktop-app\build\compose\binaries\main-release\app\CloudStream-Desktop\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\CloudStream"; Filename: "{app}\CloudStream-Desktop.exe"
Name: "{group}\{cm:UninstallProgram,CloudStream}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\CloudStream"; Filename: "{app}\CloudStream-Desktop.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\CloudStream-Desktop.exe"; Description: "{cm:LaunchProgram,CloudStream}"; Flags: nowait postinstall skipifsilent
