using CrossBoard.Models;
using System;

namespace CrossBoard.Services.Clipboard
{
    public interface IClipboardService : IDisposable
    {
        event EventHandler ClipboardChanged;
        
        ClipboardData GetClipboardContent();
        void SetClipboardContent(ClipboardData clipboardData);
    }
} 