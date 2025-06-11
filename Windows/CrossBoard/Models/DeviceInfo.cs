using System;

namespace CrossBoard.Models
{
    public class DeviceInfo
    {
        public string DeviceId { get; set; } = string.Empty;
        public string DeviceName { get; set; } = string.Empty;
        public string IpAddress { get; set; } = string.Empty;
        public int Port { get; set; } = 8765;
        public DateTimeOffset LastSeen { get; set; } = DateTimeOffset.Now;
        
        public override bool Equals(object? obj)
        {
            if (obj is DeviceInfo other)
            {
                return DeviceId == other.DeviceId;
            }
            return false;
        }
        
        public override int GetHashCode()
        {
            return DeviceId.GetHashCode();
        }
    }
} 