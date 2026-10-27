package android.app;

public class DownloadManager {
    public Request received;
    public long enqueue(Request request) { received = request; return 7L; }
    public static class Request {
        public int visibility = 0;
        public boolean wifiOnly = true;
        public Request setNotificationVisibility(int value) { visibility = value; return this; }
    }
}
