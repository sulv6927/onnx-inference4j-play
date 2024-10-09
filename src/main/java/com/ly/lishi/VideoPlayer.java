package com.ly.lishi;

import com.ly.layout.VideoPanel;
import com.ly.model_load.ModelManager;
import com.ly.onnx.engine.InferenceEngine;
import com.ly.onnx.model.InferenceResult;
import com.ly.onnx.utils.DrawImagesUtils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.ly.onnx.utils.ImageUtils.matToBufferedImage;

public class VideoPlayer {
    static {
        // 加载 OpenCV 库
        nu.pattern.OpenCV.loadLocally();
        String OS = System.getProperty("os.name").toLowerCase();
        if (OS.contains("win")) {
            System.load(ClassLoader.getSystemResource("lib/win/opencv_videoio_ffmpeg470_64.dll").getPath());
        }
    }

    private VideoCapture videoCapture;
    private volatile boolean isPlaying = false;
    private volatile boolean isPaused = false;
    private Thread frameReadingThread;
    private Thread inferenceThread;
    private VideoPanel videoPanel;

    private long videoDuration = 0; // 毫秒
    private long currentTimestamp = 0; // 毫秒



    private ModelManager modelManager;
    private List<InferenceEngine> inferenceEngines = new ArrayList<>();

    // 定义阻塞队列来缓冲转换后的数据
    private BlockingQueue<FrameData> frameDataQueue = new LinkedBlockingQueue<>(10); // 队列容量可根据需要调整

    public VideoPlayer(VideoPanel videoPanel, ModelManager modelManager) {
        this.videoPanel = videoPanel;
        this.modelManager = modelManager;
    }

    // 加载视频或流
    public void loadVideo(String videoFilePathOrStreamUrl) throws Exception {
        stopVideo();
        if (videoFilePathOrStreamUrl.equals("0")) {
            int cameraIndex = Integer.parseInt(videoFilePathOrStreamUrl);
            videoCapture = new VideoCapture(cameraIndex);
            if (!videoCapture.isOpened()) {
                throw new Exception("无法打开摄像头");
            }
            videoDuration = 0; // 摄像头没有固定的时长
            playVideo();
        } else {
            // 输入不是数字，尝试打开视频文件
            videoCapture = new VideoCapture(videoFilePathOrStreamUrl, Videoio.CAP_FFMPEG);
            if (!videoCapture.isOpened()) {
                throw new Exception("无法打开视频文件：" + videoFilePathOrStreamUrl);
            }
            double frameCount = videoCapture.get(Videoio.CAP_PROP_FRAME_COUNT);
            double fps = videoCapture.get(Videoio.CAP_PROP_FPS);
            if (fps <= 0 || Double.isNaN(fps)) {
                fps = 25; // 默认帧率
            }
            videoDuration = (long) (frameCount / fps * 1000); // 转换为毫秒
        }

        // 显示第一帧
        Mat frame = new Mat();
        if (videoCapture.read(frame)) {
            BufferedImage bufferedImage = matToBufferedImage(frame);
            videoPanel.updateImage(bufferedImage);
            currentTimestamp = 0;
        } else {
            throw new Exception("无法读取第一帧");
        }

        // 重置到视频开始位置
        videoCapture.set(Videoio.CAP_PROP_POS_FRAMES, 0);
        currentTimestamp = 0;
    }

    // 播放
    public void playVideo() {
        if (videoCapture == null || !videoCapture.isOpened()) {
            JOptionPane.showMessageDialog(null, "请先加载视频文件或流。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (isPlaying) {
            if (isPaused) {
                isPaused = false; // 恢复播放
            }
            return;
        }

        isPlaying = true;
        isPaused = false;

        frameDataQueue.clear(); // 开始播放前清空队列

        // 创建并启动帧读取和转换线程
        frameReadingThread = new Thread(() -> {
            try {
                double fps = videoCapture.get(Videoio.CAP_PROP_FPS);
                if (fps <= 0 || Double.isNaN(fps)) {
                    fps = 25; // 默认帧率
                }
                long frameDelay = (long) (1000 / fps);

                while (isPlaying) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    if (isPaused) {
                        Thread.sleep(10);
                        continue;
                    }

                    Mat frame = new Mat();
                    if (!videoCapture.read(frame) || frame.empty()) {
                        isPlaying = false;
                        break;
                    }

                    long startTime = System.currentTimeMillis();
                    BufferedImage bufferedImage = matToBufferedImage(frame);

                    if (bufferedImage != null) {
//                        float[] floats = preprocessAndConvertBufferedImage(bufferedImage);
                        Map<String, Object> stringObjectMap = preprocessImage(frame);
                        // 创建 FrameData 对象并放入队列
                        FrameData frameData = new FrameData(bufferedImage, null,stringObjectMap);
                        frameDataQueue.put(frameData); // 阻塞，如果队列已满
                    }

                    // 控制帧率
                    currentTimestamp = (long) videoCapture.get(Videoio.CAP_PROP_POS_MSEC);

                    // 控制播放速度
                    long processingTime = System.currentTimeMillis() - startTime;
                    long sleepTime = frameDelay - processingTime;
                    if (sleepTime > 0) {
                        Thread.sleep(sleepTime);
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                isPlaying = false;
            }
        });

        // 创建并启动推理线程
        inferenceThread = new Thread(() -> {
            try {
                while (isPlaying || !frameDataQueue.isEmpty()) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    if (isPaused) {
                        Thread.sleep(100);
                        continue;
                    }

                    FrameData frameData = frameDataQueue.poll(100, TimeUnit.MILLISECONDS); // 等待数据
                    if (frameData == null) {
                        continue; // 没有数据，继续检查 isPlaying
                    }

                    BufferedImage bufferedImage = frameData.image;
                    Map<String, Object> floatObjectMap = frameData.floatObjectMap;

                    // 执行推理
                    List<InferenceResult> inferenceResults = new ArrayList<>();
                    for (InferenceEngine inferenceEngine : inferenceEngines) {
                        // 假设 InferenceEngine 有 infer 方法接受 float 数组
//                        inferenceResults.add(inferenceEngine.infer( 640, 640,floatObjectMap));
                    }
                    // 绘制推理结果
                    DrawImagesUtils.drawInferenceResult(bufferedImage, inferenceResults);

                    // 更新绘制后图像
                    videoPanel.updateImage(bufferedImage);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        frameReadingThread.start();
        inferenceThread.start();
    }

    // 暂停视频
    public void pauseVideo() {
        if (!isPlaying) {
            return;
        }
        isPaused = true;
    }

    // 重播视频
    public void replayVideo() {
        try {
            stopVideo(); // 停止当前播放
            if (videoCapture != null) {
                videoCapture.set(Videoio.CAP_PROP_POS_FRAMES, 0);
                currentTimestamp = 0;

                // 显示第一帧
                Mat frame = new Mat();
                if (videoCapture.read(frame)) {
                    BufferedImage bufferedImage = matToBufferedImage(frame);
                    videoPanel.updateImage(bufferedImage);
                }

                playVideo(); // 开始播放
            }
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "重播失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    // 停止视频
    public void stopVideo() {
        isPlaying = false;
        isPaused = false;

        if (frameReadingThread != null && frameReadingThread.isAlive()) {
            frameReadingThread.interrupt();
        }

        if (inferenceThread != null && inferenceThread.isAlive()) {
            inferenceThread.interrupt();
        }

        if (videoCapture != null) {
            videoCapture.release();
            videoCapture = null;
        }

        frameDataQueue.clear();
    }

    // 快进或后退
    public void seekTo(long seekTime) {
        if (videoCapture == null) return;
        try {
            isPaused = false; // 取消暂停
            stopVideo(); // 停止当前播放
            videoCapture.set(Videoio.CAP_PROP_POS_MSEC, seekTime);
            currentTimestamp = seekTime;

            Mat frame = new Mat();
            if (videoCapture.read(frame)) {
                BufferedImage bufferedImage = matToBufferedImage(frame);
                videoPanel.updateImage(bufferedImage);
            }

            // 重新开始播放
            playVideo();

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // 快进
    public void fastForward(long milliseconds) {
        long newTime = Math.min(currentTimestamp + milliseconds, videoDuration);
        seekTo(newTime);
    }

    // 后退
    public void rewind(long milliseconds) {
        long newTime = Math.max(currentTimestamp - milliseconds, 0);
        seekTo(newTime);
    }

    public void addInferenceEngines(InferenceEngine inferenceEngine) {
        this.inferenceEngines.add(inferenceEngine);
    }

    // 定义一个内部类来存储帧数据
    private static class FrameData {
        public BufferedImage image;
        public float[] floatArray;
        public Map<String, Object> floatObjectMap;

        public FrameData(BufferedImage image, float[] floatArray, Map<String, Object> floatObjectMap) {
            this.image = image;
            this.floatArray = floatArray;
            this.floatObjectMap = floatObjectMap;
        }
    }


    // 可选的预处理方法
    public Map<String, Object> preprocessImage(Mat image) {
        int targetWidth = 640;
        int targetHeight = 640;

        int origWidth = image.width();
        int origHeight = image.height();

        // 计算缩放因子
        float scalingFactor = Math.min((float) targetWidth / origWidth, (float) targetHeight / origHeight);

        // 计算新的图像尺寸
        int newWidth = Math.round(origWidth * scalingFactor);
        int newHeight = Math.round(origHeight * scalingFactor);

        // 调整图像尺寸
        Mat resizedImage = new Mat();
        Imgproc.resize(image, resizedImage, new Size(newWidth, newHeight));

        // 转换为 RGB 并归一化
        Imgproc.cvtColor(resizedImage, resizedImage, Imgproc.COLOR_BGR2RGB);
        resizedImage.convertTo(resizedImage, CvType.CV_32FC3, 1.0 / 255.0);

        // 创建填充后的图像
        Mat paddedImage = Mat.zeros(new Size(targetWidth, targetHeight), CvType.CV_32FC3);
        int xOffset = (targetWidth - newWidth) / 2;
        int yOffset = (targetHeight - newHeight) / 2;
        Rect roi = new Rect(xOffset, yOffset, newWidth, newHeight);
        resizedImage.copyTo(paddedImage.submat(roi));

        // 将图像数据转换为数组
        int imageSize = targetWidth * targetHeight;
        float[] chwData = new float[3 * imageSize];
        float[] hwcData = new float[3 * imageSize];
        paddedImage.get(0, 0, hwcData);

        // 转换为 CHW 格式
        int channelSize = imageSize;
        for (int c = 0; c < 3; c++) {
            for (int i = 0; i < imageSize; i++) {
                chwData[c * channelSize + i] = hwcData[i * 3 + c];
            }
        }

        // 释放图像资源
        resizedImage.release();
        paddedImage.release();

        // 将预处理结果和偏移信息存入 Map
        Map<String, Object> result = new HashMap<>();
        result.put("inputData", chwData);
        result.put("origWidth", origWidth);
        result.put("origHeight", origHeight);
        result.put("scalingFactor", scalingFactor);
        result.put("xOffset", xOffset);
        result.put("yOffset", yOffset);

        return result;
    }

}
