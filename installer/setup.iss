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

[Dirs]
Name: "{app}"; Permissions: users-readexec authusers-readexec

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

function IsWebView2Installed(): Boolean;
var
  Version: String;
begin
  Result := RegQueryStringValue(HKLM, 'SOFTWARE\WOW6432Node\Microsoft\EdgeUpdate\Clients\{F3017226-FE2A-4295-8BDF-6F3A4A7E00C7}', 'pv', Version) or
            RegQueryStringValue(HKCU, 'Software\Microsoft\EdgeUpdate\Clients\{F3017226-FE2A-4295-8BDF-6F3A4A7E00C7}', 'pv', Version) or
            RegQueryStringValue(HKLM, 'SOFTWARE\Microsoft\EdgeUpdate\Clients\{F3017226-FE2A-4295-8BDF-6F3A4A7E00C7}', 'pv', Version);
  if Result and (Version = '') then
    Result := False;
end;

function IsVCRedistInstalled(): Boolean;
var
  Installed: Cardinal;
begin
  Result := RegQueryDWordValue(HKLM, 'SOFTWARE\Microsoft\VisualStudio\14.0\VC\Runtimes\x64', 'Installed', Installed) and (Installed = 1);
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  ResultCode: Integer;
begin
  Result := '';

  // Ensure Microsoft Edge WebView2 Runtime is installed for player controls and streaming UI
  if not IsWebView2Installed() then
  begin
    WizardForm.StatusLabel.Caption := 'Downloading Microsoft Edge WebView2 Runtime...';
    try
      DownloadTemporaryFile('https://go.microsoft.com/fwlink/p/?LinkId=2124703', 'MicrosoftEdgeWebview2Setup.exe', '', nil);
      WizardForm.StatusLabel.Caption := 'Installing Microsoft Edge WebView2 Runtime...';
      Exec(ExpandConstant('{tmp}\MicrosoftEdgeWebview2Setup.exe'), '/silent /install', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
      if not IsWebView2Installed() then
      begin
        Result := 'Microsoft Edge WebView2 Runtime is required to run CloudStream Desktop. Setup was aborted.';
        Exit;
      end;
    except
      Result := 'Failed to download Microsoft Edge WebView2 Runtime. An active internet connection is required to complete setup.';
      Exit;
    end;
  end;

  // Ensure Visual C++ 2015-2022 (x64) is installed for native binaries
  if not IsVCRedistInstalled() then
  begin
    WizardForm.StatusLabel.Caption := 'Downloading Visual C++ Redistributable (x64)...';
    try
      DownloadTemporaryFile('https://aka.ms/vs/17/release/vc_redist.x64.exe', 'vc_redist.x64.exe', '', nil);
      WizardForm.StatusLabel.Caption := 'Installing Visual C++ Redistributable...';
      Exec(ExpandConstant('{tmp}\vc_redist.x64.exe'), '/quiet /norestart', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
    except
      // Non-fatal if VC++ download fails
    end;
  end;
end;

function InitializeSetup(): Boolean;
begin
  Result := True;
end;
