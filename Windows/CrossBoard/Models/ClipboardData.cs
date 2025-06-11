using System;

namespace CrossBoard.Models
{
    public enum ClipboardType
    {
        Text,
        Image,
        File
    }

    public class ClipboardData
    {
        public string? Text { get; set; }
        public ClipboardType Type { get; set; }
        public string? SourceDeviceId { get; set; }
        public string? SourceDeviceName { get; set; }
        public long Timestamp { get; set; }
        public int Port { get; set; } = 8765;
    }
} 