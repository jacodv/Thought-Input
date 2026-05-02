using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Input;
using System.Windows.Interop;

namespace ThoughtInput;

/// <summary>
/// Registers a single system-wide hotkey via Win32 RegisterHotKey and raises
/// <see cref="Triggered"/> on the WPF UI thread when the user presses it.
/// </summary>
internal sealed class GlobalHotkey : IDisposable
{
    private const int WM_HOTKEY = 0x0312;
    private const int HOTKEY_ID = 0xA77;

    [Flags]
    private enum FsModifiers : uint
    {
        Alt = 0x0001,
        Control = 0x0002,
        Shift = 0x0004,
        Win = 0x0008,
        NoRepeat = 0x4000,
    }

    [DllImport("user32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool RegisterHotKey(IntPtr hWnd, int id, uint fsModifiers, uint vk);

    [DllImport("user32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool UnregisterHotKey(IntPtr hWnd, int id);

    private readonly HwndSource _source;
    private bool _registered;

    public event Action? Triggered;

    public GlobalHotkey()
    {
        _source = new HwndSource(new HwndSourceParameters("ThoughtInput-Hotkey")
        {
            Width = 0,
            Height = 0,
            ParentWindow = (IntPtr)(-3) /* HWND_MESSAGE */,
            WindowStyle = 0,
        });
        _source.AddHook(WndProc);
    }

    /// <summary>
    /// Registers the given hotkey, e.g. <c>"Ctrl+Shift+Space"</c>. Returns false on conflict.
    /// </summary>
    public bool Register(string hotkeySpec)
    {
        Unregister();
        if (!TryParse(hotkeySpec, out var modifiers, out var key)) return false;

        var vk = KeyInterop.VirtualKeyFromKey(key);
        var ok = RegisterHotKey(_source.Handle, HOTKEY_ID, (uint)(modifiers | FsModifiers.NoRepeat), (uint)vk);
        _registered = ok;
        return ok;
    }

    public void Unregister()
    {
        if (_registered)
        {
            UnregisterHotKey(_source.Handle, HOTKEY_ID);
            _registered = false;
        }
    }

    private IntPtr WndProc(IntPtr hwnd, int msg, IntPtr wParam, IntPtr lParam, ref bool handled)
    {
        if (msg == WM_HOTKEY && wParam.ToInt32() == HOTKEY_ID)
        {
            Application.Current?.Dispatcher.BeginInvoke(() => Triggered?.Invoke());
            handled = true;
        }
        return IntPtr.Zero;
    }

    public void Dispose()
    {
        Unregister();
        _source.RemoveHook(WndProc);
        _source.Dispose();
    }

    private static bool TryParse(string spec, out FsModifiers modifiers, out Key key)
    {
        modifiers = 0;
        key = Key.None;
        if (string.IsNullOrWhiteSpace(spec)) return false;

        var parts = spec.Split('+', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);
        for (int i = 0; i < parts.Length - 1; i++)
        {
            switch (parts[i].ToLowerInvariant())
            {
                case "ctrl":
                case "control": modifiers |= FsModifiers.Control; break;
                case "shift": modifiers |= FsModifiers.Shift; break;
                case "alt": modifiers |= FsModifiers.Alt; break;
                case "win":
                case "windows": modifiers |= FsModifiers.Win; break;
                default: return false;
            }
        }
        return Enum.TryParse(parts[^1], ignoreCase: true, out key);
    }
}
