[Setup]
#include "version.iss"
AppId={{C626E83F-8C3A-4D78-B5B3-FA19FE223E0C}}
AppName=CloudStream
AppVersion={#AppVersion}
AppPublisher=CloudStream
AppPublisherURL=https://github.com/errorcode26/CS3-desktop-client-unofficial
AppSupportURL=https://github.com/errorcode26/CS3-desktop-client-unofficial
AppUpdatesURL=https://github.com/errorcode26/CS3-desktop-client-unofficial
VersionInfoVersion={#NumericVersion}
VersionInfoCompany=CloudStream
VersionInfoDescription=CloudStream Desktop Installer
VersionInfoProductName=CloudStream Desktop
VersionInfoProductVersion={#NumericVersion}
VersionInfoTextVersion={#AppVersion}
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
CloseApplications=force
RestartApplications=no
LicenseFile=..\LICENSE

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

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
var
  DownloadPage: TDownloadWizardPage;

function OnDownloadProgress(const Url, FileName: String; const Progress, ProgressMax: Int64): Boolean;
begin
  Result := True;
end;

procedure InitializeWizard;
begin
  // Keep individual file extraction visible for transparent progress
  WizardForm.FilenameLabel.Visible := True;
  WizardForm.StatusLabel.Caption := 'Installing CloudStream Desktop, please wait...';
  DownloadPage := CreateDownloadPage(SetupMessage(msgWizardPreparing), 'Setup is downloading required prerequisites for CloudStream Desktop...', @OnDownloadProgress);
end;

procedure CurStepChanged(CurStep: TInstallStep);
begin
  if CurStep = ssInstall then
  begin
    WizardForm.StatusLabel.Caption := 'Extracting CloudStream Desktop application files...';
  end
  else if CurStep = ssPostInstall then
  begin
    WizardForm.StatusLabel.Caption := 'Finalizing installation and registering shortcuts...';
  end;
end;

function IsWebView2OrEdgeInstalled(): Boolean;
var
  Version: String;
begin
  // 1. Check WebView2 Evergreen Runtime registry keys (64-bit, 32-bit, and user)
  if RegQueryStringValue(HKLM64, 'SOFTWARE\WOW6432Node\Microsoft\EdgeUpdate\Clients\{F3017226-FE2A-4295-8BDF-00C3A9A7E4C5}', 'pv', Version) and (Version <> '') then
  begin
    Result := True;
    Exit;
  end;
  if RegQueryStringValue(HKLM32, 'SOFTWARE\Microsoft\EdgeUpdate\Clients\{F3017226-FE2A-4295-8BDF-00C3A9A7E4C5}', 'pv', Version) and (Version <> '') then
  begin
    Result := True;
    Exit;
  end;
  if RegQueryStringValue(HKLM, 'SOFTWARE\WOW6432Node\Microsoft\EdgeUpdate\Clients\{F3017226-FE2A-4295-8BDF-00C3A9A7E4C5}', 'pv', Version) and (Version <> '') then
  begin
    Result := True;
    Exit;
  end;
  if RegQueryStringValue(HKLM, 'SOFTWARE\Microsoft\EdgeUpdate\Clients\{F3017226-FE2A-4295-8BDF-00C3A9A7E4C5}', 'pv', Version) and (Version <> '') then
  begin
    Result := True;
    Exit;
  end;
  if RegQueryStringValue(HKCU, 'Software\Microsoft\EdgeUpdate\Clients\{F3017226-FE2A-4295-8BDF-00C3A9A7E4C5}', 'pv', Version) and (Version <> '') then
  begin
    Result := True;
    Exit;
  end;

  // 2. Check Microsoft Edge Browser registry keys
  if RegQueryStringValue(HKLM, 'SOFTWARE\WOW6432Node\Microsoft\EdgeUpdate\Clients\{56EB18F8-B008-4CBD-B6D2-8C97FE7E9062}', 'pv', Version) and (Version <> '') then
  begin
    Result := True;
    Exit;
  end;
  if RegQueryStringValue(HKLM, 'SOFTWARE\Microsoft\EdgeUpdate\Clients\{56EB18F8-B008-4CBD-B6D2-8C97FE7E9062}', 'pv', Version) and (Version <> '') then
  begin
    Result := True;
    Exit;
  end;

  // 3. Check physical filesystem paths
  if DirExists(ExpandConstant('{commonpf32}\Microsoft\EdgeWebView\Application')) or
     DirExists(ExpandConstant('{commonpf}\Microsoft\EdgeWebView\Application')) or
     DirExists(ExpandConstant('{localappdata}\Microsoft\EdgeWebView\Application')) or
     FileExists(ExpandConstant('{commonpf32}\Microsoft\Edge\Application\msedge.exe')) or
     FileExists(ExpandConstant('{commonpf}\Microsoft\Edge\Application\msedge.exe')) then
  begin
    Result := True;
    Exit;
  end;

  Result := False;
end;

function IsVCRedistInstalled(): Boolean;
var
  Installed: Cardinal;
begin
  Result := RegQueryDWordValue(HKLM, 'SOFTWARE\Microsoft\VisualStudio\14.0\VC\Runtimes\x64', 'Installed', Installed) and (Installed = 1);
end;

procedure RemoveOldMsiInstallation();
var
  SubKeys: TArrayOfString;
  I, ResultCode: Integer;
  DisplayName, ProductCode: String;
  RootKeys: array[0..1] of Integer;
  R: Integer;
begin
  RootKeys[0] := HKCU;
  RootKeys[1] := HKLM;

  for R := 0 to 1 do
  begin
    if RegGetSubkeyNames(RootKeys[R], 'Software\Microsoft\Windows\CurrentVersion\Uninstall', SubKeys) then
    begin
      for I := 0 to GetArrayLength(SubKeys) - 1 do
      begin
        ProductCode := SubKeys[I];
        if RegQueryStringValue(RootKeys[R], 'Software\Microsoft\Windows\CurrentVersion\Uninstall\' + ProductCode, 'DisplayName', DisplayName) then
        begin
          if (DisplayName = 'CloudStream-Desktop') or (DisplayName = 'CloudStream Desktop') then
          begin
            WizardForm.StatusLabel.Caption := 'Removing previous MSI installation...';
            Exec('msiexec.exe', '/x ' + ProductCode + ' /quiet /norestart', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
          end;
        end;
      end;
    end;
  end;

  // Clean up legacy per-user directory if still left on disk
  if DirExists(ExpandConstant('{localappdata}\Programs\CloudStream-Desktop')) then
  begin
    DelTree(ExpandConstant('{localappdata}\Programs\CloudStream-Desktop'), True, True, True);
  end;

  // Clean up legacy machine MSI directory if left on disk
  if DirExists(ExpandConstant('{commonpf}\CloudStream-Desktop')) then
  begin
    DelTree(ExpandConstant('{commonpf}\CloudStream-Desktop'), True, True, True);
  end;
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  ResultCode: Integer;
begin
  Result := '';

  // Clean up any old MSI installation to prevent duplicate entries and dual installs
  RemoveOldMsiInstallation();

  // Terminate any running instances of CloudStream and TorrServer to completely release file locks
  Exec('taskkill.exe', '/F /IM CloudStream-Desktop.exe', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
  Exec('taskkill.exe', '/F /IM torrserver.exe', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
  Sleep(300);

  // Check if Microsoft Edge or WebView2 is present
  if not IsWebView2OrEdgeInstalled() then
  begin
    if MsgBox('Microsoft Edge WebView2 Runtime was not detected on your system.' + #13#10#13#10 +
              'CloudStream Desktop requires the WebView2 Runtime to display player controls and streaming interfaces.' + #13#10#13#10 +
              'Would you like Setup to download and install Microsoft Edge WebView2 now?',
              mbConfirmation, MB_YESNO) = IDYES then
    begin
      DownloadPage.Clear;
      DownloadPage.Add('https://go.microsoft.com/fwlink/p/?LinkId=2124703', 'MicrosoftEdgeWebview2Setup.exe', '');
      DownloadPage.Show;
      try
        try
          DownloadPage.Download;
          WizardForm.StatusLabel.Caption := 'Installing Microsoft Edge WebView2 Runtime...';
          Exec(ExpandConstant('{tmp}\MicrosoftEdgeWebview2Setup.exe'), '/install', '', SW_SHOW, ewWaitUntilTerminated, ResultCode);
        except
          MsgBox('Could not automatically download WebView2 Runtime: ' + GetExceptionMessage + #13#10#13#10 +
                 'Please ensure Microsoft Edge or WebView2 Runtime is installed before running CloudStream.', mbInformation, MB_OK);
        end;
      finally
        DownloadPage.Hide;
      end;
    end
    else
    begin
      MsgBox('Please ensure Microsoft Edge or the WebView2 Runtime is installed before launching CloudStream Desktop.', mbInformation, MB_OK);
    end;
  end;

  // Ensure Visual C++ 2015-2022 (x64) is installed for native binaries
  if not IsVCRedistInstalled() then
  begin
    DownloadPage.Clear;
    DownloadPage.Add('https://aka.ms/vs/17/release/vc_redist.x64.exe', 'vc_redist.x64.exe', '');
    DownloadPage.Show;
    try
      try
        DownloadPage.Download;
        WizardForm.StatusLabel.Caption := 'Installing Visual C++ Redistributable...';
        Exec(ExpandConstant('{tmp}\vc_redist.x64.exe'), '/quiet /norestart', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
      except
        // Non-fatal if VC++ download fails
      end;
    finally
      DownloadPage.Hide;
    end;
  end;
end;

function InitializeSetup(): Boolean;
begin
  Result := True;
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  if CurUninstallStep = usPostUninstall then
  begin
    if MsgBox('Do you also want to remove all CloudStream Desktop user data, watch history, bookmarks, and plugin caches?',
              mbConfirmation, MB_YESNO or MB_DEFBUTTON2) = IDYES then
    begin
      DelTree(ExpandConstant('{userappdata}\CloudStreamDesktop'), True, True, True);
    end;
  end;
end;
