package cn.qd.peiwen.pwcapturescreen;

public interface ICaptureListener {
    void onCaptureFailed();
    void onCaptureSuccessed(String filepath);
}
