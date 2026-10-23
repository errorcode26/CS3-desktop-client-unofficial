[Setup]
#include "version.iss"
AppId={{C626E83F-8C3A-4D78-B5B3-FA19FE223E0C}}
AppName=CloudStream
AppVersion={#AppVersion}
AppPublisher=CloudStream
AppPublisherURL=https://github.com/errorcode26/CS3-desktop-client-unofficial
AppSupportURL=https://github.com/errorcode26/CS3-desktop-client-unofficial
AppUpdatesURL=https://github.com/errorcode26/CS3-desktop-client-unofficial
VersionInfoVersion={#AppVersion}
VersionInfoCompany=CloudStream
VersionInfoDescription=CloudStream Desktop Installer
VersionInfoProductName=CloudStream Desktop
VersionInfoProductVersion={#AppVersion}
VersionInfoCopyright=Copyright (C) CloudStream
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
TimeStampsInUTC=yes
WizardStyle=modern
DisableProgramGroupPage=yes
DisableReadyPage=yes
CloseApplications=yes
RestartApplications=no

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

[Code]
procedure InitializeWizard;
begin
  // Hide individual file extraction subtext to keep UI clean and modern
  WizardForm.FilenameLabel.Visible := False;
  WizardForm.StatusLabel.Caption := 'Installing CloudStream Desktop, please wait...';
end;

function IsWebView2Installed: Boolean;
var
  Version: String;
begin
  Result := RegQueryStringValue(HKLM, 'SOFTWARE\WOW6432Node\Microsoft\EdgeUpdate\Clients\{F3017226-FE2A-4295-8BDF-6F3A4A7E00C7}', 'pv', Version) or
            RegQueryStringValue(HKCU, 'Software\Microsoft\EdgeUpdate\Clients\{F3017226-FE2A-4295-8BDF-6F3A4A7E00C7}', 'pv', Version) or
            RegQueryStringValue(HKLM, 'SOFTWARE\Microsoft\EdgeUpdate\Clients\{F3017226-FE2A-4295-8BDF-6F3A4A7E00C7}', 'pv', Version);
  if Result and (Version = '') then
    Result := False;
end;

function InitializeSetup(): Boolean;
var
  ErrorCode: Integer;
begin
  Result := True;
  if not IsWebView2Installed() then
  begin
    if MsgBox('Microsoft Edge WebView2 Runtime was not detected on your PC.' + #13#10 + #13#10 +
              'CloudStream requires WebView2 to display player controls and streaming interfaces. ' +
              'Without it, video playback will not have any UI.' + #13#10 + #13#10 +
              'Click YES to open the official Microsoft download page in your browser so you can install it, or NO to continue setup anyway.',
              mbConfirmation, MB_YESNO) = IDYES then
    begin
      ShellExec('open', 'https://go.microsoft.com/fwlink/p/?LinkId=2124703', '', '', SW_SHOWNORMAL, ewNoWait, ErrorCode);
    end;
  end;
end;
