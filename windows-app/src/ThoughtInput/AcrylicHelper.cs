using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Interop;
using System.Windows.Media;

namespace ThoughtInput;

/// <summary>
/// Applies the Windows 11 transient-window backdrop to a WPF window via
/// <c>DwmSetWindowAttribute</c>. Falls back silently on Windows 10.
/// </summary>
internal static class AcrylicHelper
{
    private const int DWMWA_SYSTEMBACKDROP_TYPE = 38;
    private const int DWMSBT_TRANSIENTWINDOW = 3;
    private const int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;

    [DllImport("dwmapi.dll")]
    private static extern int DwmSetWindowAttribute(IntPtr hwnd, int attr, ref int attrValue, int attrSize);

    public static void Apply(Window window, bool darkMode)
    {
        var helper = new WindowInteropHelper(window);
        if (helper.Handle == IntPtr.Zero)
        {
            window.SourceInitialized += (_, _) => Apply(window, darkMode);
            return;
        }

        // Need transparent background colour for the backdrop to show through.
        window.Background = Brushes.Transparent;

        try
        {
            int backdrop = DWMSBT_TRANSIENTWINDOW;
            DwmSetWindowAttribute(helper.Handle, DWMWA_SYSTEMBACKDROP_TYPE, ref backdrop, sizeof(int));

            int dark = darkMode ? 1 : 0;
            DwmSetWindowAttribute(helper.Handle, DWMWA_USE_IMMERSIVE_DARK_MODE, ref dark, sizeof(int));
        }
        catch
        {
            // Pre-Win11: dwmapi exists but the attribute is rejected — that's fine.
        }
    }
}
