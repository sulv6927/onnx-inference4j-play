package com.ly.utils;

import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.VideoInputFrameGrabber;

public class CameraDeviceLister {
    public static void main(String[] args) throws FrameGrabber.Exception {
        String[] deviceDescriptions = VideoInputFrameGrabber.getDeviceDescriptions();
        for (String deviceDescription : deviceDescriptions) {
            System.out.println("摄像头索引 " + ": " + deviceDescription);
        }
    }
}
