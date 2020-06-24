package cn.qd.peiwen.capture.screen;

public interface ICaptureListener {
    void onCaptureFailed();
    void onCaptureSuccessed(String filepath);
}
