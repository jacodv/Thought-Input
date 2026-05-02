using System.ComponentModel;
using System.Runtime.InteropServices;
using System.Text;
using ThoughtInput.Core;

namespace ThoughtInput;

/// <summary>
/// <see cref="ISecretStore"/> backed by Windows Credential Manager (generic credentials,
/// per-user vault). Each secret is stored under target name <c>"ThoughtInput:&lt;account&gt;"</c>.
/// </summary>
internal sealed class WindowsCredentialSecretStore : ISecretStore
{
    private const string TargetPrefix = "ThoughtInput:";

    public void SaveString(string account, string value)
    {
        var blob = Encoding.Unicode.GetBytes(value);
        try
        {
            var credential = new CREDENTIAL
            {
                Type = CRED_TYPE_GENERIC,
                TargetName = TargetPrefix + account,
                CredentialBlob = Marshal.AllocHGlobal(blob.Length),
                CredentialBlobSize = (uint)blob.Length,
                Persist = CRED_PERSIST_LOCAL_MACHINE,
                UserName = account,
            };
            try
            {
                Marshal.Copy(blob, 0, credential.CredentialBlob, blob.Length);
                if (!CredWrite(ref credential, 0))
                    throw new Win32Exception(Marshal.GetLastWin32Error(), "CredWrite failed");
            }
            finally
            {
                Marshal.FreeHGlobal(credential.CredentialBlob);
            }
        }
        finally
        {
            Array.Clear(blob);
        }
    }

    public string? LoadString(string account)
    {
        if (!CredRead(TargetPrefix + account, CRED_TYPE_GENERIC, 0, out var ptr))
        {
            var err = Marshal.GetLastWin32Error();
            if (err == ERROR_NOT_FOUND) return null;
            throw new Win32Exception(err, "CredRead failed");
        }
        try
        {
            var cred = Marshal.PtrToStructure<CREDENTIAL>(ptr);
            if (cred.CredentialBlobSize == 0 || cred.CredentialBlob == IntPtr.Zero) return string.Empty;
            var bytes = new byte[cred.CredentialBlobSize];
            Marshal.Copy(cred.CredentialBlob, bytes, 0, (int)cred.CredentialBlobSize);
            return Encoding.Unicode.GetString(bytes);
        }
        finally
        {
            CredFree(ptr);
        }
    }

    public void Delete(string account)
    {
        if (!CredDelete(TargetPrefix + account, CRED_TYPE_GENERIC, 0))
        {
            var err = Marshal.GetLastWin32Error();
            if (err != ERROR_NOT_FOUND)
                throw new Win32Exception(err, "CredDelete failed");
        }
    }

    // -- P/Invoke ---------------------------------------------------------------

    private const uint CRED_TYPE_GENERIC = 1;
    private const uint CRED_PERSIST_LOCAL_MACHINE = 2;
    private const int ERROR_NOT_FOUND = 1168;

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct CREDENTIAL
    {
        public uint Flags;
        public uint Type;
        public string TargetName;
        public string? Comment;
        public System.Runtime.InteropServices.ComTypes.FILETIME LastWritten;
        public uint CredentialBlobSize;
        public IntPtr CredentialBlob;
        public uint Persist;
        public uint AttributeCount;
        public IntPtr Attributes;
        public string? TargetAlias;
        public string UserName;
    }

    [DllImport("Advapi32.dll", SetLastError = true, EntryPoint = "CredWriteW", CharSet = CharSet.Unicode)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool CredWrite([In] ref CREDENTIAL credential, [In] uint flags);

    [DllImport("Advapi32.dll", SetLastError = true, EntryPoint = "CredReadW", CharSet = CharSet.Unicode)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool CredRead(string target, uint type, uint reservedFlag, out IntPtr credentialPtr);

    [DllImport("Advapi32.dll", SetLastError = true, EntryPoint = "CredDeleteW", CharSet = CharSet.Unicode)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool CredDelete(string target, uint type, uint flags);

    [DllImport("Advapi32.dll", SetLastError = false)]
    private static extern void CredFree([In] IntPtr cred);
}
