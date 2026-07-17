[Setup]
#include "version.iss"
AppId={{C626E83F-8C3A-4D78-B5B3-FA19FE223E0C}}
AppName=CloudStream
AppVersion={#AppVersion}-pre-alpha
AppPublisher=Ayu
AppPublisherURL=https://github.com/errorcode26/CS3-desktop-client-unofficial
AppSupportURL=https://github.com/errorcode26/CS3-desktop-client-unofficial
AppUpdatesURL=https://github.com/errorcode26/CS3-desktop-client-unofficial
DefaultDirName={autopf}\CloudStream
DefaultGroupName=CloudStream
AllowNoIcons=yes
SetupIconFile=..\desktop-app\src\main\resources\app_icon.ico
UninstallDisplayIcon={app}\CloudStream-Desktop.exe
; Output directory for the compiled installer
OutputDir=..\desktop-app\build\outputs
OutputBaseFilename=CloudStream-Setup
Compression=lzma2/ultra64
SolidCompression=yes
LZMAUseSeparateProcess=yes
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
; Copy all files and folders from the AppImage output
Source: "..\desktop-app\build\compose\binaries\main\app\CloudStream-Desktop\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\CloudStream"; Filename: "{app}\CloudStream-Desktop.exe"
Name: "{group}\{cm:UninstallProgram,CloudStream}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\CloudStream"; Filename: "{app}\CloudStream-Desktop.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\CloudStream-Desktop.exe"; Description: "{cm:LaunchProgram,CloudStream}"; Flags: nowait postinstall skipifsilent runasoriginaluser
