//package com.ly.play.ff;
//
//import com.ly.layout.VideoPanel;
//import com.ly.model_load.ModelManager;
//import com.ly.onnx.engine.InferenceEngine;
//import com.ly.onnx.model.InferenceResult;
//import com.ly.onnx.utils.DrawImagesUtils;
//import com.ly.onnx.utils.ImageUtils;
//import org.bytedeco.javacv.*;
//
//import javax.swing.*;
//import java.awt.image.BufferedImage;
//import java.util.ArrayList;
//import java.util.List;
//
//public class VideoPlayer {
//    private FrameGrabber grabber;
//    private Java2DFrameConverter converter = new Java2DFrameConverter();
//    private boolean isPlaying = false;
//    private boolean isPaused = false;
//    private Thread videoThread;
//    private VideoPanel videoPanel;
//
//    private long videoDuration = 0; // 毫秒
//    private long currentTimestamp = 0; // 毫秒
//
//    ModelManager modelManager;
//    private List<InferenceEngine> inferenceEngines = new ArrayList<>();
//
//    public VideoPlayer(VideoPanel videoPanel, ModelManager modelManager) {
//        this.videoPanel = videoPanel;
//        this.modelManager = modelManager;
//        System.out.println();
//    }
//
//    // 加载视频或流
//    public void loadVideo(String videoFilePathOrStreamUrl) throws Exception {
//        stopVideo();
//        if (videoFilePathOrStreamUrl.equals("0")) {
//            int cameraIndex = Integer.parseInt(videoFilePathOrStreamUrl);
//            grabber = new OpenCVFrameGrabber(cameraIndex);
//            grabber.start();
//            videoDuration = 0; // 摄像头没有固定的时长
//            playVideo();
//        } else {
//            // 输入不是数字，尝试使用 FFmpegFrameGrabber 打开流或视频文件
//            grabber = new FFmpegFrameGrabber(videoFilePathOrStreamUrl);
//            grabber.start();
//            videoDuration = grabber.getLengthInTime() / 1000; // 转换为毫秒
//        }
//
//
//        // 显示第一帧
//        Frame frame;
//        if (grabber instanceof OpenCVFrameGrabber) {
//            frame = grabber.grab();
//        } else {
//            frame = grabber.grab();
//        }
//        if (frame != null && frame.image != null) {
//            BufferedImage bufferedImage = converter.getBufferedImage(frame);
//            videoPanel.updateImage(bufferedImage);
//            currentTimestamp = 0;
//        }
//
//        // 重置到视频开始位置
//        if (grabber instanceof FFmpegFrameGrabber) {
//            grabber.setTimestamp(0);
//        }
//        currentTimestamp = 0;
//    }
//
//
//
//
//
//    //播放
//    public void playVideo() {
//        if (grabber == null) {
//            JOptionPane.showMessageDialog(null, "请先加载视频文件或流。", "提示", JOptionPane.WARNING_MESSAGE);
//            return;
//        }
//
//        if (inferenceEngines == null){
//            JOptionPane.showMessageDialog(null, "请先加载模型给文件。", "提示", JOptionPane.WARNING_MESSAGE);
//            return;
//        }
//
//        if (isPlaying) {
//            if (isPaused) {
//                isPaused = false; // 恢复播放
//            }
//            return;
//        }
//
//        isPlaying = true;
//        isPaused = false;
//
//        videoThread = new Thread(() -> {
//            try {
//                if (grabber instanceof OpenCVFrameGrabber) {
//                    // 摄像头捕获
//                    while (isPlaying) {
//                        if (isPaused) {
//                            Thread.sleep(10);
//                            continue;
//                        }
//
//                        Frame frame = grabber.grab();
//                        if (frame == null) {
//                            isPlaying = false;
//                            break;
//                        }
//
//                        BufferedImage bufferedImage = converter.getBufferedImage(frame);
//                        List<InferenceResult> inferenceResults = new ArrayList<>();
//                        if (bufferedImage != null) {
//                            float[] inputData = ImageUtils.frameToFloatArray(frame);
//                            for (InferenceEngine inferenceEngine : inferenceEngines) {
//                                inferenceResults.add(inferenceEngine.infer(inputData,640,640));
//                            }
//                            //绘制
//                            DrawImagesUtils.drawInferenceResult(bufferedImage,inferenceResults);
//                            //更新绘制后图像
//                            videoPanel.updateImage(bufferedImage);
//                        }
//                    }
//                } else {
//                    // 视频文件或流
//                    double frameRate = grabber.getFrameRate();
//                    if (frameRate <= 0 || Double.isNaN(frameRate)) {
//                        frameRate = 25; // 默认帧率
//                    }
//                    long frameInterval = (long) (1000 / frameRate); // 每帧间隔时间（毫秒）
//                    long startTime = System.currentTimeMillis();
//                    long frameCount = 0;
//
//                    while (isPlaying) {
//                        if (isPaused) {
//                            Thread.sleep(100);
//                            startTime += 100; // 调整开始时间以考虑暂停时间
//                            continue;
//                        }
//
//                        Frame frame = grabber.grab();
//                        if (frame == null) {
//                            // 视频播放结束
//                            isPlaying = false;
//                            break;
//                        }
//
//
//
//                        BufferedImage bufferedImage = converter.getBufferedImage(frame);
//
//
//                        List<InferenceResult> inferenceResults = new ArrayList<>();
//                        if (bufferedImage != null) {
//                            float[] inputData = ImageUtils.frameToFloatArray(frame);
//                            for (InferenceEngine inferenceEngine : inferenceEngines) {
//                                inferenceResults.add(inferenceEngine.infer(inputData,640,640));
//                            }
//                            //绘制
//                            DrawImagesUtils.drawInferenceResult(bufferedImage,inferenceResults);
//                            //更新绘制后图像
//                            videoPanel.updateImage(bufferedImage);
//                        }
//
//                        if (bufferedImage != null) {
//                            videoPanel.updateImage(bufferedImage);
//
//                            // 更新当前帧时间戳
//                            frameCount++;
//                            long expectedTime = frameCount * frameInterval;
//                            long actualTime = System.currentTimeMillis() - startTime;
//
//                            currentTimestamp = grabber.getTimestamp() / 1000;
//
//                            // 如果实际时间落后于预期时间，进行调整
//                            if (actualTime < expectedTime) {
//                                Thread.sleep(expectedTime - actualTime);
//                            }
//                        }
//                    }
//                }
//
//                // 视频播放完毕后，停止播放
//                isPlaying = false;
//
//            } catch (Exception ex) {
//                ex.printStackTrace();
//            }
//        });
//        videoThread.start();
//    }
//
//    // 暂停视频
//    public void pauseVideo() {
//        if (!isPlaying) {
//            return;
//        }
//        isPaused = true;
//    }
//
//    // 重播视频
//    public void replayVideo() {
//        try {
//            if (grabber instanceof FFmpegFrameGrabber) {
//                grabber.setTimestamp(0); // 重置到视频开始位置
//                grabber.flush(); // 清除缓存
//                currentTimestamp = 0;
//
//                // 显示第一帧
//                Frame frame = grabber.grab();
//                if (frame != null && frame.image != null) {
//                    BufferedImage bufferedImage = converter.getBufferedImage(frame);
//                    videoPanel.updateImage(bufferedImage);
//                }
//
//                playVideo(); // 开始播放
//            } else if (grabber instanceof OpenCVFrameGrabber) {
//                // 对于摄像头，重播相当于重新开始播放
//                playVideo();
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//            JOptionPane.showMessageDialog(null, "重播失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
//        }
//    }
//
//    // 停止视频
//    public void stopVideo() {
//        isPlaying = false;
//        isPaused = false;
//
//        if (videoThread != null && videoThread.isAlive()) {
//            try {
//                videoThread.join();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
//
//        if (grabber != null) {
//            try {
//                grabber.stop();
//                grabber.release();
//            } catch (Exception ex) {
//                ex.printStackTrace();
//            }
//            grabber = null;
//        }
//    }
//
//    // 快进或后退
//    public void seekTo(long seekTime) {
//        if (grabber == null) return;
//        if (!(grabber instanceof FFmpegFrameGrabber)) {
//            JOptionPane.showMessageDialog(null, "此操作仅支持视频文件和流。", "提示", JOptionPane.WARNING_MESSAGE);
//            return;
//        }
//        try {
//            isPaused = false; // 取消暂停
//            isPlaying = false; // 停止当前播放线程
//            if (videoThread != null && videoThread.isAlive()) {
//                videoThread.join();
//            }
//
//            grabber.setTimestamp(seekTime * 1000); // 转换为微秒
//            grabber.flush(); // 清除缓存
//
//            Frame frame;
//            do {
//                frame = grabber.grab();
//                if (frame == null) {
//                    break;
//                }
//            } while (frame.image == null); // 跳过没有图像的帧
//
//            if (frame != null && frame.image != null) {
//                BufferedImage bufferedImage = converter.getBufferedImage(frame);
//                videoPanel.updateImage(bufferedImage);
//
//                // 更新当前帧时间戳
//                currentTimestamp = grabber.getTimestamp() / 1000;
//            }
//
//            // 重新开始播放
//            playVideo();
//
//        } catch (Exception ex) {
//            ex.printStackTrace();
//        }
//    }
//
//    // 快进
//    public void fastForward(long milliseconds) {
//        if (!(grabber instanceof FFmpegFrameGrabber)) {
//            JOptionPane.showMessageDialog(null, "此操作仅支持视频文件和流。", "提示", JOptionPane.WARNING_MESSAGE);
//            return;
//        }
//        long newTime = Math.min(currentTimestamp + milliseconds, videoDuration);
//        seekTo(newTime);
//    }
//
//    // 后退
//    public void rewind(long milliseconds) {
//        if (!(grabber instanceof FFmpegFrameGrabber)) {
//            JOptionPane.showMessageDialog(null, "此操作仅支持视频文件和流。", "提示", JOptionPane.WARNING_MESSAGE);
//            return;
//        }
//        long newTime = Math.max(currentTimestamp - milliseconds, 0);
//        seekTo(newTime);
//    }
//
//    public long getVideoDuration() {
//        return videoDuration;
//    }
//
//    public FrameGrabber getGrabber() {
//        return grabber;
//    }
//
//    public void addInferenceEngines(InferenceEngine inferenceEngine){
//        this.inferenceEngines.add(inferenceEngine);
//    }
//
//}
