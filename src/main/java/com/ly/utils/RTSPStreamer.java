package com.ly.utils;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.*;

public class RTSPStreamer {

    public static void main(String[] args) {
        String inputFile = "C:\\Users\\ly\\Desktop\\屏幕录制 2024-09-20 225443.mp4"; // 替换为您的本地视频文件路径
        String rtspUrl = "rtsp://localhost:8554/live"; // 替换为您的 RTSP 服务器地址

        FFmpegFrameGrabber grabber = null;
        FFmpegFrameRecorder recorder = null;

        try {
            // 初始化 FFmpegFrameGrabber 以从本地视频文件读取
            grabber = new FFmpegFrameGrabber(inputFile);
            grabber.start();

            // 初始化 FFmpegFrameRecorder 以推流到 RTSP 服务器
            recorder = new FFmpegFrameRecorder(rtspUrl, grabber.getImageWidth(), grabber.getImageHeight(), grabber.getAudioChannels());
            recorder.setFormat("rtsp");
            recorder.setFrameRate(grabber.getFrameRate());
            recorder.setVideoBitrate(grabber.getVideoBitrate());
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264); // 设置视频编码格式
            recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC); // 设置音频编码格式

            // 设置 RTSP 传输选项（如果需要）
             recorder.setOption("rtsp_transport", "tcp");

            recorder.start();

            Frame frame;
            while ((frame = grabber.grab()) != null) {
                recorder.record(frame);
            }

            System.out.println("推流完成。");

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (recorder != null) {
                    recorder.stop();
                    recorder.release();
                }
                if (grabber != null) {
                    grabber.stop();
                    grabber.release();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
