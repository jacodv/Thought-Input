using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Input;
using System.Windows.Interop;
using System.Windows.Threading;
using ThoughtInput.Core;

namespace ThoughtInput.UI;

public partial class CaptureWindow : Window
{
    private const int GWL_EXSTYLE = -20;
    private const int WS_EX_TOOLWINDOW = 0x80;
    private const int WS_EX_NOACTIVATE = 0x08000000;

    [DllImport("user32.dll", EntryPoint = "GetWindowLong")]
    private static extern int GetWindowLong32(IntPtr hWnd, int nIndex);

    [DllImport("user32.dll", EntryPoint = "GetWindowLongPtr")]
    private static extern IntPtr GetWindowLongPtr64(IntPtr hWnd, int nIndex);

    [DllImport("user32.dll", EntryPoint = "SetWindowLong")]
    private static extern int SetWindowLong32(IntPtr hWnd, int nIndex, int dwNewLong);

    [DllImport("user32.dll", EntryPoint = "SetWindowLongPtr")]
    private static extern IntPtr SetWindowLongPtr64(IntPtr hWnd, int nIndex, IntPtr dwNewLong);

    [DllImport("user32.dll")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool SetForegroundWindow(IntPtr hWnd);

    private readonly CaptureService _captureService;
    private readonly DispatcherTimer _badgeTimer;

    public CaptureWindow(CaptureService captureService)
    {
        _captureService = captureService;
        InitializeComponent();
        _badgeTimer = new DispatcherTimer();
        _badgeTimer.Tick += (_, _) =>
        {
            _badgeTimer.Stop();
            OkBadge.Visibility = Visibility.Collapsed;
            WarnBadge.Visibility = Visibility.Collapsed;
        };

        SourceInitialized += OnSourceInitialized;
        Deactivated += (_, _) => HideCapture();
    }

    private void OnSourceInitialized(object? sender, EventArgs e)
    {
        var helper = new WindowInteropHelper(this);
        var ex = (long)(IntPtr.Size == 8
            ? GetWindowLongPtr64(helper.Handle, GWL_EXSTYLE)
            : GetWindowLong32(helper.Handle, GWL_EXSTYLE));
        ex |= WS_EX_TOOLWINDOW; // hide from Alt+Tab
        if (IntPtr.Size == 8)
            SetWindowLongPtr64(helper.Handle, GWL_EXSTYLE, new IntPtr(ex));
        else
            SetWindowLong32(helper.Handle, GWL_EXSTYLE, (int)ex);

        AcrylicHelper.Apply(this, darkMode: true);
    }

    public void ShowCapture()
    {
        Input.Text = "";
        OkBadge.Visibility = Visibility.Collapsed;
        WarnBadge.Visibility = Visibility.Collapsed;
        PositionOnPrimaryScreen();
        Show();
        Activate();
        var helper = new WindowInteropHelper(this);
        if (helper.Handle != IntPtr.Zero) SetForegroundWindow(helper.Handle);
        Input.Focus();
        Keyboard.Focus(Input);
    }

    public void HideCapture()
    {
        Hide();
    }

    private void PositionOnPrimaryScreen()
    {
        var workArea = SystemParameters.WorkArea;
        Left = workArea.Left + (workArea.Width - Width) / 2;
        // Place ~1/3 from the top for an at-eye-level feel.
        Top = workArea.Top + workArea.Height / 3 - 40;
    }

    private async void OnInputKeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Escape)
        {
            HideCapture();
            e.Handled = true;
            return;
        }
        if (e.Key == Key.Enter)
        {
            e.Handled = true;
            var text = Input.Text.Trim();
            if (text.Length == 0)
            {
                HideCapture();
                return;
            }
            await SubmitAsync(text);
        }
    }

    private async Task SubmitAsync(string text)
    {
        var ok = await _captureService.SubmitAsync(text, CaptureMethod.Typed);
        ShowBadge(ok);
        if (ok)
        {
            Input.Text = "";
            await Task.Delay(350);
            HideCapture();
        }
    }

    private void ShowBadge(bool success)
    {
        _badgeTimer.Stop();
        if (success)
        {
            OkBadge.Visibility = Visibility.Visible;
            _badgeTimer.Interval = TimeSpan.FromMilliseconds(350);
        }
        else
        {
            WarnBadge.Visibility = Visibility.Visible;
            _badgeTimer.Interval = TimeSpan.FromMilliseconds(800);
        }
        _badgeTimer.Start();
    }
}
