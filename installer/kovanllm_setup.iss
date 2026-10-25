; Inno Setup Script for KovanLLM Station
; Created by Aselens Software (https://aselens.com)

#define MyAppName "KovanLLM Station"
#define MyAppVersion "1.1"
#define MyAppPublisher "Aselens Software"
#define MyAppURL "https://aselens.com"
#define MyAppExeName "run_desktop.bat"

[Setup]
; AppId uniquely identifies this application.
AppId={{9B854A92-3B4C-485D-8D63-B87FA2574A92}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL=https://github.com/aselens/kovanllm
DefaultDirName={autopf}\KovanLLM
DefaultGroupName=KovanLLM
AllowNoIcons=yes
LicenseFile=..\LICENSE
OutputDir=..\dist
OutputBaseFilename=KovanLLM_Station_v1.1_Setup
SetupIconFile=..\desktop\assets\icon.ico
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
ArchitecturesInstallIn64BitMode=x64compatible
DisableProgramGroupPage=auto

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"
Name: "turkish"; MessagesFile: "compiler:Languages\Turkish.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
; Desktop Station source files
Source: "..\desktop\*"; DestDir: "{app}\desktop"; Flags: ignoreversion recursesubdirs createallsubdirs; Excludes: "*.pyc,__pycache__,.pytest_cache,*.log"
Source: "..\requirements.txt"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\README.md"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\LICENSE"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\desktop\{#MyAppExeName}"; IconFilename: "{app}\desktop\assets\icon.ico"; Comment: "KovanLLM Station Desktop Host"
Name: "{group}\Documentation"; Filename: "{app}\README.md"
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\desktop\{#MyAppExeName}"; IconFilename: "{app}\desktop\assets\icon.ico"; Tasks: desktopicon

[Run]
Filename: "{app}\desktop\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: shellexec postinstall nowait skipifsilent
