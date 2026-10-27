; Numa Standalone Installer Script for NSIS
; SPDX-License-Identifier: AGPL-3.0-or-later

!include "MUI2.nsh"
!include "FileFunc.nsh"

!ifndef NUMA_VERSION
  !define NUMA_VERSION "2.0.2"
!endif

!ifndef NUMA_FILE_VERSION
  !define NUMA_FILE_VERSION "2.0.2.0"
!endif

!ifndef STAGE_DIR
  !define STAGE_DIR "..\build\desktop\package\Numa"
!endif

!ifndef OUTPUT_FILE
  !define OUTPUT_FILE "..\dist\Numa-Setup.exe"
!endif

Unicode true
Name "NÜMA"
OutFile "${OUTPUT_FILE}"
InstallDir "$PROGRAMFILES64\Numa"
InstallDirRegKey HKLM "Software\Numa" "Install_Dir"
RequestExecutionLevel admin
SetCompressor /SOLID lzma
ShowInstDetails show
ShowUnInstDetails show

!define MUI_ICON "..\bootstrapper\src\platform\windows\numa.ico"
!define MUI_UNICON "..\bootstrapper\src\platform\windows\numa.ico"

VIProductVersion "${NUMA_FILE_VERSION}"
VIAddVersionKey "ProductName" "NÜMA"
VIAddVersionKey "FileDescription" "Instalador de NÜMA"
VIAddVersionKey "CompanyName" "NÜMA"
VIAddVersionKey "FileVersion" "${NUMA_VERSION}"
VIAddVersionKey "ProductVersion" "${NUMA_VERSION}"
VIAddVersionKey "LegalCopyright" "Copyright (C) 2026 NÜMA"

!define MUI_ABORTWARNING

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!define MUI_FINISHPAGE_RUN "$INSTDIR\numa.exe"
!define MUI_FINISHPAGE_RUN_TEXT "Ejecutar NÜMA"
!define MUI_FINISHPAGE_SHOWREADME
!define MUI_FINISHPAGE_SHOWREADME_TEXT "Crear un acceso directo en el escritorio"
!define MUI_FINISHPAGE_SHOWREADME_FUNCTION CreateDesktopShortcut
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_UNPAGE_FINISH

!insertmacro MUI_LANGUAGE "Spanish"
!insertmacro MUI_LANGUAGE "English"

Function un.onInit
  nsExec::ExecToLog '"$SYSDIR\taskkill.exe" /F /T /IM numa.exe'
  nsExec::ExecToLog '"$SYSDIR\taskkill.exe" /F /T /IM numa-auth-helper.exe'
  nsExec::ExecToLog '"$SYSDIR\taskkill.exe" /F /T /IM numa-adaptive-mix.exe'
FunctionEnd

Function CreateDesktopShortcut
  CreateShortcut "$DESKTOP\NÜMA.lnk" "$INSTDIR\numa.exe" "" "$INSTDIR\numa.exe" 0
FunctionEnd

Section "Numa" SecNuma
  SetOutPath "$INSTDIR"

  File /r "${STAGE_DIR}\*.*"

  WriteRegStr HKLM "Software\Numa" "Install_Dir" "$INSTDIR"
  WriteUninstaller "$INSTDIR\Uninstall.exe"

  CreateDirectory "$SMPROGRAMS\NÜMA"
  CreateShortcut "$SMPROGRAMS\NÜMA\NÜMA.lnk" "$INSTDIR\numa.exe" "" "$INSTDIR\numa.exe" 0
  CreateShortcut "$SMPROGRAMS\NÜMA\Desinstalar NÜMA.lnk" "$INSTDIR\Uninstall.exe" "" "$INSTDIR\Uninstall.exe" 0
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Numa" "DisplayName" "NÜMA"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Numa" "DisplayIcon" "$INSTDIR\numa.exe,0"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Numa" "DisplayVersion" "${NUMA_VERSION}"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Numa" "Publisher" "NÜMA"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Numa" "InstallLocation" "$INSTDIR"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Numa" "UninstallString" '"$INSTDIR\Uninstall.exe"'
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Numa" "QuietUninstallString" '"$INSTDIR\Uninstall.exe" /S'
  WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Numa" "NoModify" 1
  WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Numa" "NoRepair" 1

  ${GetSize} "$INSTDIR" "/S=0K" $0 $1 $2
  IntFmt $0 "0x%08X" $0
  WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Numa" "EstimatedSize" "$0"
SectionEnd

Section "Uninstall"
  Delete "$DESKTOP\NÜMA.lnk"
  Delete "$DESKTOP\Numa.lnk"
  Delete "$SMPROGRAMS\NÜMA\NÜMA.lnk"
  Delete "$SMPROGRAMS\NÜMA\Desinstalar NÜMA.lnk"
  RMDir "$SMPROGRAMS\NÜMA"
  Delete "$APPDATA\Microsoft\Internet Explorer\Quick Launch\User Pinned\TaskBar\NÜMA.lnk"


  DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Numa"
  DeleteRegKey HKLM "Software\Numa"
  DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Numa"
  DeleteRegKey HKCU "Software\Numa"

  RMDir /r "$LOCALAPPDATA\Numa"
  RMDir /r "$APPDATA\Numa"

  RMDir /r "$INSTDIR"
SectionEnd
