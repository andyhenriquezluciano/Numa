; Copyright (C) 2026 SFG545
; SPDX-License-Identifier: AGPL-3.0-or-later
;
; NSIS carries the native bootstrapper. The bootstrapper owns the install
; layout, Start menu shortcut, update flow and Add/Remove Programs entry.
; Two installers tending one numa would only argue about the watering can.

!include "MUI2.nsh"
!include "nsDialogs.nsh"
!include "FileFunc.nsh"

!ifndef BOOTSTRAPPER_EXE
  !error "Pass -DBOOTSTRAPPER_EXE=<path to numa-bootstrapper.exe> to makensis"
!endif

!ifndef NUMA_VERSION
  !define NUMA_VERSION "1.0.2"
!endif

!ifndef NUMA_FILE_VERSION
  !define NUMA_FILE_VERSION "1.0.2.0"
!endif

!ifndef OUTPUT_FILE
  !define OUTPUT_FILE "NumaSetup.exe"
!endif

!ifndef DEFAULT_CHANNEL
  !define DEFAULT_CHANNEL "stable"
!endif

Unicode true
Name "NÜMA"
OutFile "${OUTPUT_FILE}"
RequestExecutionLevel user
SetCompressor /SOLID lzma
Icon "src/platform/windows/numa.ico"
ShowInstDetails show

VIProductVersion "${NUMA_FILE_VERSION}"
VIAddVersionKey "ProductName" "NÜMA"
VIAddVersionKey "FileDescription" "NÜMA setup"
VIAddVersionKey "CompanyName" "SFG545"
VIAddVersionKey "FileVersion" "${NUMA_VERSION}"
VIAddVersionKey "ProductVersion" "${NUMA_VERSION}"
VIAddVersionKey "LegalCopyright" "Copyright (C) 2026 SFG545"

!define MUI_ABORTWARNING
!insertmacro MUI_PAGE_WELCOME
Page custom ChannelPageCreate ChannelPageLeave
!insertmacro MUI_PAGE_INSTFILES
!define MUI_FINISHPAGE_RUN
!define MUI_FINISHPAGE_RUN_TEXT "Launch NÜMA"
!define MUI_FINISHPAGE_RUN_FUNCTION LaunchNuma
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_LANGUAGE "English"

Var Channel
Var StableRadio
Var CanaryRadio

Function .onInit
  StrCpy $Channel "${DEFAULT_CHANNEL}"
  ${GetParameters} $0
  ${GetOptions} $0 "/CHANNEL=" $1
  StrCmp $1 "" channelReady
  StrCmp $1 "stable" chooseStable
  StrCmp $1 "canary" chooseCanary
  IfSilent invalidChannel
  MessageBox MB_ICONSTOP|MB_OK "Unknown update channel: $1. Choose stable or canary."
invalidChannel:
  SetErrorLevel 2
  Abort

chooseStable:
  StrCpy $Channel "stable"
  Goto channelReady
chooseCanary:
  StrCpy $Channel "canary"
channelReady:
FunctionEnd

Function ChannelPageCreate
  !insertmacro MUI_HEADER_TEXT "Update channel" "Choose which NÜMA releases to install."
  nsDialogs::Create 1018
  Pop $0
  StrCmp $0 error 0 +2
    Abort

  ${NSD_CreateRadioButton} 0 10u 100% 14u "Stable (recommended)"
  Pop $StableRadio
  ${NSD_CreateLabel} 15u 27u 95% 20u "Regular releases for everyday use."
  Pop $0
  ${NSD_CreateRadioButton} 0 55u 100% 14u "Canary"
  Pop $CanaryRadio
  ${NSD_CreateLabel} 15u 72u 95% 28u "Early releases for testing. Changes may be less reliable."
  Pop $0

  StrCmp $Channel "canary" 0 +3
    ${NSD_Check} $CanaryRadio
    Goto showChannelPage
  ${NSD_Check} $StableRadio
showChannelPage:
  nsDialogs::Show
FunctionEnd

Function ChannelPageLeave
  ${NSD_GetState} $CanaryRadio $0
  StrCmp $0 ${BST_CHECKED} 0 +3
    StrCpy $Channel "canary"
    Return
  StrCpy $Channel "stable"
FunctionEnd

Function LaunchNuma
  ; Find the root the bootstrapper actually registered, including redirected
  ; per-user program folders. The window can close while Numa starts.
  ReadRegStr $0 HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Numa" "InstallLocation"
  StrCmp $0 "" 0 +2
    StrCpy $0 "$LOCALAPPDATA\Programs\Numa"
  IfFileExists "$0\Numa.exe" 0 launchMissing
  Exec '"$0\Numa.exe"'
  IfErrors launchMissing
  Return

launchMissing:
  MessageBox MB_ICONSTOP|MB_OK "NÜMA was installed, but its launcher could not be started. Open NÜMA from the Start menu."
FunctionEnd

Section "Install NÜMA"
  InitPluginsDir
  SetOutPath "$PLUGINSDIR"
  File /oname=NumaBootstrapper.exe "${BOOTSTRAPPER_EXE}"

  ; The native bootstrapper copies itself into the per-user install root.
  ; Wait for installation only; the Finish page offers the launch afterward.
  ; The boot can grow apples, but the wizard should close before serving them.
  ExecWait '"$PLUGINSDIR\NumaBootstrapper.exe" --install --no-launch --channel $Channel' $0
  IfErrors launchFailed
  StrCmp $0 0 installed
  SetErrorLevel $0
  Abort "Numa installation exited with code $0."

launchFailed:
  SetErrorLevel 1
  Abort "Could not start the Numa bootstrapper."

installed:
SectionEnd
