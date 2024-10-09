package com.ly.utils;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;

public class OpenCVTest {
//    static {
//        nu.pattern.OpenCV.loadLocally();
//    }

    public static void main(String[] args) {
        VideoCapture capture = new VideoCapture(0); // 打开默认摄像头
        if (!capture.isOpened()) {
            System.out.println("无法打开摄像头");
            return;
        }

        Mat frame = new Mat();
        if (capture.read(frame)) {
            System.out.println("成功读取一帧图像");
        } else {
            System.out.println("无法读取图像");
        }

        capture.release();
    }
}
