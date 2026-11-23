// SPDX-License-Identifier: AGPL-3.0-or-later

#include <windows.h>
#include <bcrypt.h>
#include <shellapi.h>

#include <array>
#include <cwchar>
#include <memory>
#include <string>

namespace {

bool writeStatus(const wchar_t *path, char status)
{
    const HANDLE file = CreateFileW(path, GENERIC_WRITE, FILE_SHARE_READ, nullptr,
                                    CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (file == INVALID_HANDLE_VALUE)
        return false;
    DWORD written = 0;
    const bool ok = WriteFile(file, &status, 1, &written, nullptr) && written == 1;
    CloseHandle(file);
    return ok;
}

bool matchesDigest(HANDLE installer, const std::wstring &expected)
{
    if (expected.size() != 64)
        return false;
    BCRYPT_ALG_HANDLE algorithm = nullptr;
    if (BCryptOpenAlgorithmProvider(&algorithm, BCRYPT_SHA256_ALGORITHM, nullptr, 0) < 0)
        return false;
    BCRYPT_HASH_HANDLE hash = nullptr;
    bool ok = BCryptCreateHash(algorithm, &hash, nullptr, 0, nullptr, 0, 0) >= 0;
    std::array<UCHAR, 65536> buffer;
    DWORD read = 0;
    while (ok) {
        ok = ReadFile(installer, buffer.data(), static_cast<DWORD>(buffer.size()), &read, nullptr);
        if (!ok || read == 0)
            break;
        ok = BCryptHashData(hash, buffer.data(), read, 0) >= 0;
    }
    std::array<UCHAR, 32> digest;
    ok = ok && BCryptFinishHash(hash, digest.data(), static_cast<ULONG>(digest.size()), 0) >= 0;
    if (hash)
        BCryptDestroyHash(hash);
    BCryptCloseAlgorithmProvider(algorithm, 0);
    if (!ok)
        return false;

    constexpr wchar_t hex[] = L"0123456789abcdef";
    for (size_t i = 0; i < digest.size(); ++i) {
        if (expected[2 * i] != hex[digest[i] >> 4] || expected[2 * i + 1] != hex[digest[i] & 15])
            return false;
    }
    return true;
}

int installUpdate(wchar_t **args)
{
    const auto installerPath = args[1];
    const auto status = args[2];
    const std::wstring application = args[3];
    const std::wstring pid = args[4];
    if (pid.empty() || pid.find_first_not_of(L"0123456789") != std::wstring::npos) {
        writeStatus(status, '2');
        return 1;
    }
    const std::unique_ptr<void, decltype(&CloseHandle)> appProcess(
        OpenProcess(SYNCHRONIZE, FALSE, std::wcstoul(pid.c_str(), nullptr, 10)), CloseHandle);
    if (!appProcess) {
        writeStatus(status, '2');
        return 1;
    }

    // Keep the verified file open and deny writing or replacing it through installation.
    const HANDLE installer = CreateFileW(installerPath, GENERIC_READ, FILE_SHARE_READ, nullptr,
                                         OPEN_EXISTING, FILE_FLAG_SEQUENTIAL_SCAN, nullptr);
    if (installer == INVALID_HANDLE_VALUE || !matchesDigest(installer, args[5])) {
        if (installer != INVALID_HANDLE_VALUE)
            CloseHandle(installer);
        writeStatus(status, '2');
        return 1;
    }

    const std::wstring directory = application.substr(0, application.find_last_of(L"\\/"));
    const std::wstring parameters = L"/S /NUMA_WAIT_PID=" + pid;
    SHELLEXECUTEINFOW shell{};
    shell.cbSize = sizeof(shell);
    shell.fMask = SEE_MASK_NOCLOSEPROCESS | SEE_MASK_NOASYNC | SEE_MASK_FLAG_NO_UI;
    shell.lpVerb = L"runas";
    shell.lpFile = installerPath;
    shell.lpParameters = parameters.c_str();
    shell.lpDirectory = directory.c_str();
    shell.nShow = SW_SHOWNORMAL;
    if (!ShellExecuteExW(&shell) || !shell.hProcess) {
        const DWORD error = GetLastError();
        CloseHandle(installer);
        writeStatus(status, error == ERROR_CANCELLED ? '0' : '2');
        return 1;
    }

    const bool approvalReported = writeStatus(status, '1');
    DWORD exitCode = 1;
    if (WaitForSingleObject(shell.hProcess, INFINITE) == WAIT_OBJECT_0)
        GetExitCodeProcess(shell.hProcess, &exitCode);
    CloseHandle(shell.hProcess);
    CloseHandle(installer);
    DeleteFileW(installerPath);

    // This helper retains the original user's token while the installer runs elevated.
    STARTUPINFOW startup{};
    startup.cb = sizeof(startup);
    PROCESS_INFORMATION process{};
    const bool reopened = approvalReported && WaitForSingleObject(appProcess.get(), 10000) == WAIT_OBJECT_0 &&
        CreateProcessW(application.c_str(), nullptr, nullptr, nullptr, FALSE,
                       0, nullptr, directory.c_str(), &startup, &process);
    if (reopened) {
        CloseHandle(process.hThread);
        CloseHandle(process.hProcess);
    }
    if (exitCode || !reopened) {
        MessageBoxW(nullptr, exitCode ? L"No se pudo completar la actualización. Vuelve a intentarlo desde Ajustes."
                                      : L"La actualización terminó. Abre NÜMA desde el menú Inicio.",
                    L"NÜMA", MB_OK | MB_ICONWARNING);
    }
    return exitCode || !reopened ? 1 : 0;
}

} // namespace

int WINAPI WinMain(HINSTANCE, HINSTANCE, LPSTR, int)
{
    int count = 0;
    auto **args = CommandLineToArgvW(GetCommandLineW(), &count);
    const HRESULT com = CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED | COINIT_DISABLE_OLE1DDE);
    const int result = count == 6 ? installUpdate(args) : 1;
    if (SUCCEEDED(com))
        CoUninitialize();
    LocalFree(args);
    return result;
}
