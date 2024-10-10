package com.ly.play.opencv;

import com.ly.layout.VideoPanel;
import com.ly.model_load.ModelManager;
import com.ly.onnx.engine.InferenceEngine;
import com.ly.onnx.model.BoundingBox;
import com.ly.onnx.model.InferenceResult;
import com.ly.onnx.utils.DrawImagesUtils;
import com.ly.track.SimpleTracker;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

    // 创建简单的跟踪器
    SimpleTracker tracker = new SimpleTracker();

    private long videoDuration = 0; // 毫秒
    private long currentTimestamp = 0; // 毫秒

    private boolean isTrackingEnabled;

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
                    Map<Integer, Object> stringObjectMap = preprocessImage(frame);
                    // 创建 FrameData 对象并放入队列
                    FrameData frameData = new FrameData(bufferedImage, stringObjectMap);
                    frameDataQueue.put(frameData); // 阻塞，如果队列已满
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
                    Map<Integer, Object> floatObjectMap = frameData.floatObjectMap;

                    // 执行推理
                    List<InferenceResult> inferenceResults = new ArrayList<>();
                    for (InferenceEngine inferenceEngine : inferenceEngines) {
                        // 假设 InferenceEngine 有 infer 方法接受 float 数组
                        InferenceResult infer = inferenceEngine.infer(floatObjectMap);
                        inferenceResults.add(infer);
                    }

                    // 合并所有模型的推理结果
                    List<BoundingBox> allBoundingBoxes = new ArrayList<>();
                    for (InferenceResult result : inferenceResults) {
                        allBoundingBoxes.addAll(result.getBoundingBoxes());
                    }
                    // 如果启用了目标跟踪，则更新边界框并分配 trackId
                    if (isTrackingEnabled) {
                        tracker.update(allBoundingBoxes);
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

    // 设置是否启用目标跟踪
    public void setTrackingEnabled(boolean enabled) {
        this.isTrackingEnabled = enabled;
    }

    // 定义一个内部类来存储帧数据
    private static class FrameData {
        public BufferedImage image;
        public Map<Integer, Object> floatObjectMap;

        public FrameData(BufferedImage image, Map<Integer, Object> floatObjectMap) {
            this.image = image;
            this.floatObjectMap = floatObjectMap;
        }
    }

    // 可选的预处理方法
    public Map<Integer, Object> preprocessImage(Mat image) {
        int origWidth = image.width();
        int origHeight = image.height();
        Map<Integer, Object> dynamicInput = new HashMap<>();
        //定义索引
        AtomicInteger index = new AtomicInteger(0);
        for (InferenceEngine inferenceEngine : this.inferenceEngines) {

            long[] inputShape = inferenceEngine.getInputShape();
            int targetWidth = (int) inputShape[2];
            int targetHeight = (int) inputShape[3];
            // 计算缩放因子
            float scalingFactor = Math.min((float) targetWidth / origWidth, (float) targetHeight / origHeight);
            //检查是否存在输入大小一致的 如果存在则跳过
            if (!dynamicInput.isEmpty()) {
                boolean flag = true;  // 初始设为 true 表示需要跳过
                for (Map.Entry<Integer, Object> entry : dynamicInput.entrySet()) {
                    Map<String, Object> input = (Map<String, Object>) entry.getValue();
                    Integer targetHeightValue = (Integer) input.get("targetHeight");
                    Integer targetWidthValue = (Integer) input.get("targetWidth");
                    if (inputShape[2] == targetHeightValue && inputShape[3] == targetWidthValue) {
                        flag = false;  // 如果找到相同尺寸，设为 false 表示不需要跳过
                    }
                }

                if (!flag) {  // 如果找到相同尺寸，跳过处理
                    if (inferenceEngine.getIndex() == null) {
                        inferenceEngine.setIndex(index.get());
                    }
                    continue;
                } else {
                    index.getAndIncrement();
                }
            }

            // 计算新的图像尺寸
            int newWidth = Math.round(origWidth * scalingFactor);
            int newHeight = Math.round(origHeight * scalingFactor);

            // 调整图像尺寸
            Mat resizedImage = new Mat();
            Imgproc.resize(image, resizedImage, new Size(newWidth, newHeight), 0, 0, Imgproc.INTER_AREA);

            // 获取图像的尺寸
            int rows = resizedImage.rows();
            int cols = resizedImage.cols();
            // 准备存储浮点型数据的数组
            float[] floatData = new float[rows * cols * 3];

            // 获取原始字节数据
            byte[] pixelData = new byte[rows * cols * 3];
            resizedImage.get(0, 0, pixelData);

            // 手动处理像素数据
            for (int i = 0; i < rows * cols; i++) {
                int byteIndex = i * 3;
                int floatIndex = i * 3;
                // 读取 BGR 值并转换为 0.0 - 1.0 之间的浮点数
                float b = (pixelData[byteIndex] & 0xFF) / 255.0f;
                float g = (pixelData[byteIndex + 1] & 0xFF) / 255.0f;
                float r = (pixelData[byteIndex + 2] & 0xFF) / 255.0f;
                // 将 BGR 转换为 RGB，并存储到浮点数组中
                floatData[floatIndex] = r;
                floatData[floatIndex + 1] = g;
                floatData[floatIndex + 2] = b;
            }

            // 将浮点数组转换回 Mat 对象
            Mat floatImage = new Mat(rows, cols, CvType.CV_32FC3);
            floatImage.put(0, 0, floatData);

            resizedImage = floatImage;

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
            floatImage.release();
            // 将预处理结果和偏移信息存入 Map
            Map<String, Object> result = new HashMap<>();
            result.put("inputData", chwData);
            result.put("origWidth", origWidth);
            result.put("origHeight", origHeight);
            result.put("targetWidth", targetWidth);
            result.put("targetHeight", targetHeight);
            result.put("scalingFactor", scalingFactor);
            result.put("xOffset", xOffset);
            result.put("yOffset", yOffset);
            inferenceEngine.setIndex(index.get());
            dynamicInput.put(index.get(), result);
        }

        return dynamicInput;
    }

    public List<InferenceEngine> getInferenceEngines() {
        return this.inferenceEngines;
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

    public void addInferenceEngines(InferenceEngine inferenceEngine) {
        this.inferenceEngines.add(inferenceEngine);
    }

    // 加载并处理图片
    public void loadImage(String imagePath) throws Exception {
        // 停止任何正在播放的视频
        stopVideo();

        // 读取图片
        Mat image = Imgcodecs.imread(imagePath);
        if (image.empty()) {
            throw new Exception("无法读取图片文件：" + imagePath);
        }

        // 转换为 BufferedImage
        BufferedImage bufferedImage = matToBufferedImage(image);

        // 预处理图片
        Map<Integer, Object> preprocessedData = preprocessImage(image);

        // 执行推理
        List<InferenceResult> inferenceResults = new ArrayList<>();
        for (InferenceEngine inferenceEngine : inferenceEngines) {
            InferenceResult infer = inferenceEngine.infer(preprocessedData);
            inferenceResults.add(infer);
        }

        // 合并所有模型的推理结果
        List<BoundingBox> allBoundingBoxes = new ArrayList<>();
        for (InferenceResult result : inferenceResults) {
            allBoundingBoxes.addAll(result.getBoundingBoxes());
        }

        // 如果启用了目标跟踪，则更新边界框并分配 trackId
        if (isTrackingEnabled) {
            tracker.update(allBoundingBoxes);
        }

        // 绘制推理结果
        DrawImagesUtils.drawInferenceResult(bufferedImage, inferenceResults);

        // 在 VideoPanel 上显示图片
        videoPanel.updateImage(bufferedImage);
    }
}
