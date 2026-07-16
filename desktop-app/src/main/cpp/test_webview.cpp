#include <windows.h>
#include <iostream>
#include "webview2\build\native\include\webview2.h"



typedef HRESULT(STDAPICALLTYPE *CreateCoreWebView2EnvironmentWithOptionsFunc)(
    PCWSTR browserExecutableFolder, PCWSTR userDataFolder,
    ICoreWebView2EnvironmentOptions* environmentOptions,
    ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler* environmentCreatedHandler);

class SimpleEnvironmentOptions : public ICoreWebView2EnvironmentOptions {
    ULONG m_refCount = 1;
    static constexpr LPCWSTR kAdditionalArgs = L"--allow-file-access-from-files";
public:
    HRESULT STDMETHODCALLTYPE QueryInterface(REFIID riid, void** ppv) override {
        *ppv = this; AddRef(); return S_OK;
    }
    ULONG STDMETHODCALLTYPE AddRef()  override { return ++m_refCount; }
    ULONG STDMETHODCALLTYPE Release() override { return --m_refCount; }
    HRESULT STDMETHODCALLTYPE get_AdditionalBrowserArguments(LPWSTR* value) override {
        size_t len = wcslen(kAdditionalArgs) + 1;
        *value = (LPWSTR)CoTaskMemAlloc(len * sizeof(wchar_t));
        wcscpy_s(*value, len, kAdditionalArgs);
        return S_OK;
    }
    HRESULT STDMETHODCALLTYPE put_AdditionalBrowserArguments(LPCWSTR) override { return E_NOTIMPL; }
    HRESULT STDMETHODCALLTYPE get_Language(LPWSTR* value) override { return E_NOTIMPL; }
    HRESULT STDMETHODCALLTYPE put_Language(LPCWSTR) override { return E_NOTIMPL; }
    HRESULT STDMETHODCALLTYPE get_TargetCompatibleBrowserVersion(LPWSTR* value) override { return E_NOTIMPL; }
    HRESULT STDMETHODCALLTYPE put_TargetCompatibleBrowserVersion(LPCWSTR) override { return E_NOTIMPL; }
    HRESULT STDMETHODCALLTYPE get_AllowSingleSignOnUsingOSPrimaryAccount(BOOL* value) override { return E_NOTIMPL; }
    HRESULT STDMETHODCALLTYPE put_AllowSingleSignOnUsingOSPrimaryAccount(BOOL) override { return E_NOTIMPL; }
};

class EnvironmentCompletedHandler : public ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler {
public:
    HRESULT STDMETHODCALLTYPE QueryInterface(REFIID riid, void** ppv) override {
        *ppv = this; return S_OK;
    }
    ULONG STDMETHODCALLTYPE AddRef() override { return 1; }
    ULONG STDMETHODCALLTYPE Release() override { return 1; }
    HRESULT STDMETHODCALLTYPE Invoke(HRESULT result, ICoreWebView2Environment* createdEnvironment) override {
        std::cout << "Invoke called with result: 0x" << std::hex << result << std::endl;
        PostQuitMessage(0);
        return S_OK;
    }
};

int main() {
    HMODULE hMod = LoadLibraryA("WebView2Loader.dll");
    if (!hMod) { std::cout << "Failed to load dll\n"; return 1; }
    auto createEnvFunc = (CreateCoreWebView2EnvironmentWithOptionsFunc)GetProcAddress(hMod, "CreateCoreWebView2EnvironmentWithOptions");
    
    wchar_t tempPath[MAX_PATH];
    GetTempPathW(MAX_PATH, tempPath);
    std::wstring userData = std::wstring(tempPath) + L"CloudStreamWebView2Test";
    
    std::cout << "Calling Create...\n";
    HRESULT hr = createEnvFunc(nullptr, userData.c_str(), new SimpleEnvironmentOptions(), new EnvironmentCompletedHandler());
    std::cout << "Create returned hr: 0x" << std::hex << hr << std::endl;
    
    MSG msg;
    while (GetMessage(&msg, nullptr, 0, 0)) {
        TranslateMessage(&msg);
        DispatchMessage(&msg);
    }
    return 0;
}
