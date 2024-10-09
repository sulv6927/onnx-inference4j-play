package com.ly.lishi;

import ai.onnxruntime.*;
import com.alibaba.fastjson.JSON;
import com.ly.onnx.model.BoundingBox;
import com.ly.onnx.model.InferenceResult;
import lombok.Data;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.nio.FloatBuffer;
import java.util.*;

@Data
public class InferenceEngine {

    private OrtEnvironment environment;
    private OrtSession.SessionOptions sessionOptions;
    private OrtSession session;

    private String modelPath;
    private List<String> labels;

    // 用于存储图像预处理信息的类变量
    private long[] inputShape = null;

    static {
        nu.pattern.OpenCV.loadLocally();
    }

    public InferenceEngine(String modelPath, List<String> labels) {
        this.modelPath = modelPath;
        this.labels = labels;
        init();
    }

    public void init() {
        try {
            environment = OrtEnvironment.getEnvironment();
            sessionOptions = new OrtSession.SessionOptions();
            sessionOptions.addCUDA(0); // 使用 GPU
            session = environment.createSession(modelPath, sessionOptions);
            Map<String, NodeInfo> inputInfo = session.getInputInfo();
            NodeInfo nodeInfo = inputInfo.values().iterator().next();
            TensorInfo tensorInfo = (TensorInfo) nodeInfo.getInfo();
            inputShape = tensorInfo.getShape(); // 从模型中获取输入形状
            logModelInfo(session);
        } catch (OrtException e) {
            throw new RuntimeException("模型加载失败", e);
        }
    }

    public InferenceResult infer(int w, int h, Map<String, Object> preprocessParams) {
        long startTime = System.currentTimeMillis();

        // 从 Map 中获取偏移相关的变量
        float[] inputData = (float[]) preprocessParams.get("inputData");
        int origWidth = (int) preprocessParams.get("origWidth");
        int origHeight = (int) preprocessParams.get("origHeight");
        float scalingFactor = (float) preprocessParams.get("scalingFactor");
        int xOffset = (int) preprocessParams.get("xOffset");
        int yOffset = (int) preprocessParams.get("yOffset");

        try {
            Map<String, NodeInfo> inputInfo = session.getInputInfo();
            String inputName = inputInfo.keySet().iterator().next(); // 假设只有一个输入

            long[] inputShape = {1, 3, h, w}; // 根据模型需求调整形状

            // 创建输入张量时，使用 CHW 格式的数据
            OnnxTensor inputTensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(inputData), inputShape);

            // 执行推理
            long inferenceStart = System.currentTimeMillis();
            OrtSession.Result result = session.run(Collections.singletonMap(inputName, inputTensor));
            long inferenceEnd = System.currentTimeMillis();
            System.out.println("模型推理耗时：" + (inferenceEnd - inferenceStart) + " ms");

            // 解析推理结果
            String outputName = session.getOutputInfo().keySet().iterator().next(); // 假设只有一个输出
            float[][][] outputData = (float[][][]) result.get(outputName).get().getValue(); // 输出形状：[1, N, 6]

            // 设定置信度阈值
            float confidenceThreshold = 0.25f; // 您可以根据需要调整

            // 根据模型的输出结果解析边界框
            List<BoundingBox> boxes = new ArrayList<>();
            for (float[] data : outputData[0]) { // 遍历所有检测框
                // 根据模型输出格式，解析中心坐标和宽高
                float x_center = data[0];
                float y_center = data[1];
                float width = data[2];
                float height = data[3];
                float confidence = data[4];

                if (confidence >= confidenceThreshold) {
                    // 将中心坐标转换为左上角和右下角坐标
                    float x1 = x_center - width / 2;
                    float y1 = y_center - height / 2;
                    float x2 = x_center + width / 2;
                    float y2 = y_center + height / 2;

                    // 调整坐标，减去偏移并除以缩放因子
                    float x1Adjusted = (x1 - xOffset) / scalingFactor;
                    float y1Adjusted = (y1 - yOffset) / scalingFactor;
                    float x2Adjusted = (x2 - xOffset) / scalingFactor;
                    float y2Adjusted = (y2 - yOffset) / scalingFactor;

                    // 确保坐标的正确顺序
                    float xMinAdjusted = Math.min(x1Adjusted, x2Adjusted);
                    float xMaxAdjusted = Math.max(x1Adjusted, x2Adjusted);
                    float yMinAdjusted = Math.min(y1Adjusted, y2Adjusted);
                    float yMaxAdjusted = Math.max(y1Adjusted, y2Adjusted);

                    // 确保坐标在原始图像范围内
                    int x = (int) Math.max(0, xMinAdjusted);
                    int y = (int) Math.max(0, yMinAdjusted);
                    int xMax = (int) Math.min(origWidth, xMaxAdjusted);
                    int yMax = (int) Math.min(origHeight, yMaxAdjusted);
                    int wBox = xMax - x;
                    int hBox = yMax - y;

                    // 仅当宽度和高度为正时，才添加边界框
                    if (wBox > 0 && hBox > 0) {
                        // 使用您的单一标签
                        String label = labels.get(0);

                        boxes.add(new BoundingBox(x, y, wBox, hBox, label, confidence));
                    }
                }
            }

            // 非极大值抑制（NMS）
            long nmsStart = System.currentTimeMillis();
            List<BoundingBox> nmsBoxes = nonMaximumSuppression(boxes, 0.5f);

            System.out.println("检测到的标签：" + JSON.toJSONString(nmsBoxes));
            if (!nmsBoxes.isEmpty()) {
                for (BoundingBox box : nmsBoxes) {
                    System.out.println(box);
                }
            }
            long nmsEnd = System.currentTimeMillis();
            System.out.println("NMS 耗时：" + (nmsEnd - nmsStart) + " ms");

            // 封装结果并返回
            InferenceResult inferenceResult = new InferenceResult();
            inferenceResult.setBoundingBoxes(nmsBoxes);

            long endTime = System.currentTimeMillis();
            System.out.println("一次推理总耗时：" + (endTime - startTime) + " ms");

            return inferenceResult;

        } catch (OrtException e) {
            throw new RuntimeException("推理失败", e);
        }
    }


    // 计算两个边界框的 IoU
    private float computeIoU(BoundingBox box1, BoundingBox box2) {
        int x1 = Math.max(box1.getX(), box2.getX());
        int y1 = Math.max(box1.getY(), box2.getY());
        int x2 = Math.min(box1.getX() + box1.getWidth(), box2.getX() + box2.getWidth());
        int y2 = Math.min(box1.getY() + box1.getHeight(), box2.getY() + box2.getHeight());

        int intersectionArea = Math.max(0, x2 - x1) * Math.max(0, y2 - y1);
        int box1Area = box1.getWidth() * box1.getHeight();
        int box2Area = box2.getWidth() * box2.getHeight();

        return (float) intersectionArea / (box1Area + box2Area - intersectionArea);
    }

    // 非极大值抑制（NMS）方法
    private List<BoundingBox> nonMaximumSuppression(List<BoundingBox> boxes, float iouThreshold) {
        // 按置信度排序（从高到低）
        boxes.sort((a, b) -> Float.compare(b.getConfidence(), a.getConfidence()));

        List<BoundingBox> result = new ArrayList<>();

        while (!boxes.isEmpty()) {
            BoundingBox bestBox = boxes.remove(0);
            result.add(bestBox);

            Iterator<BoundingBox> iterator = boxes.iterator();
            while (iterator.hasNext()) {
                BoundingBox box = iterator.next();
                if (computeIoU(bestBox, box) > iouThreshold) {
                    iterator.remove();
                }
            }
        }

        return result;
    }

    // 打印模型信息
    private void logModelInfo(OrtSession session) {
        System.out.println("模型输入信息:");
        try {
            for (Map.Entry<String, NodeInfo> entry : session.getInputInfo().entrySet()) {
                String name = entry.getKey();
                NodeInfo info = entry.getValue();
                System.out.println("输入名称: " + name);
                System.out.println("输入信息: " + info.toString());
            }
        } catch (OrtException e) {
            throw new RuntimeException(e);
        }

        System.out.println("模型输出信息:");
        try {
            for (Map.Entry<String, NodeInfo> entry : session.getOutputInfo().entrySet()) {
                String name = entry.getKey();
                NodeInfo info = entry.getValue();
                System.out.println("输出名称: " + name);
                System.out.println("输出信息: " + info.toString());
            }
        } catch (OrtException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        // 加载 OpenCV 库

        // 初始化标签列表（只有一个标签）
        List<String> labels = Arrays.asList("person");

        // 创建 InferenceEngine 实例
        InferenceEngine inferenceEngine = new InferenceEngine("C:\\Users\\ly\\Desktop\\person.onnx", labels);

        for (int j = 0; j < 10; j++) {
            try {
                // 加载图片
                Mat inputImage = Imgcodecs.imread("C:\\Users\\ly\\Desktop\\10230731212230.png");

                // 预处理图像
                long l1 = System.currentTimeMillis();
                Map<String, Object> preprocessResult = inferenceEngine.preprocessImage(inputImage);
                float[] inputData = (float[]) preprocessResult.get("inputData");

                InferenceResult result = null;
                for (int i = 0; i < 10; i++) {
                    long l = System.currentTimeMillis();
                    result = inferenceEngine.infer( 640, 640, preprocessResult);
                    System.out.println("第 " + (i + 1) + " 次推理耗时：" + (System.currentTimeMillis() - l) + " ms");
                }


                // 处理并显示结果
                System.out.println("推理结果:");
                for (BoundingBox box : result.getBoundingBoxes()) {
                    System.out.println(box);
                }

                // 可视化并保存带有边界框的图像
                Mat outputImage = inferenceEngine.drawBoundingBoxes(inputImage, result.getBoundingBoxes());

                // 保存图片到本地文件
                String outputFilePath = "output_image_with_boxes.jpg";
                Imgcodecs.imwrite(outputFilePath, outputImage);

                System.out.println("已保存结果图片: " + outputFilePath);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // 在图像上绘制边界框和标签
    private Mat drawBoundingBoxes(Mat image, List<BoundingBox> boxes) {
        for (BoundingBox box : boxes) {
            // 绘制矩形边界框
            Imgproc.rectangle(image, new Point(box.getX(), box.getY()),
                    new Point(box.getX() + box.getWidth(), box.getY() + box.getHeight()),
                    new Scalar(0, 0, 255), 2); // 红色边框

            // 绘制标签文字和置信度
            String label = box.getLabel() + " " + String.format("%.2f", box.getConfidence());
            int baseLine[] = new int[1];
            Size labelSize = Imgproc.getTextSize(label, Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, 1, baseLine);
            int top = Math.max(box.getY(), (int) labelSize.height);
            Imgproc.putText(image, label, new Point(box.getX(), top),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(255, 255, 255), 1);
        }

        return image;
    }


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

        // 计算偏移量以居中图像
        int xOffset = (targetWidth - newWidth) / 2;
        int yOffset = (targetHeight - newHeight) / 2;

        // 调整图像尺寸
        Mat resizedImage = new Mat();
        Imgproc.resize(image, resizedImage, new Size(newWidth, newHeight), 0, 0, Imgproc.INTER_LINEAR);

        // 转换为 RGB 并归一化
        Imgproc.cvtColor(resizedImage, resizedImage, Imgproc.COLOR_BGR2RGB);
        resizedImage.convertTo(resizedImage, CvType.CV_32FC3, 1.0 / 255.0);

        // 创建填充后的图像
        Mat paddedImage = Mat.zeros(new Size(targetWidth, targetHeight), CvType.CV_32FC3);
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


    // 图像预处理
//    public float[] preprocessImage(Mat image) {
//        int targetWidth = 640;
//        int targetHeight = 640;
//
//        origWidth = image.width();
//        origHeight = image.height();
//
//        // 计算缩放因子
//        scalingFactor = Math.min((float) targetWidth / origWidth, (float) targetHeight / origHeight);
//
//        // 计算新的图像尺寸
//        newWidth = Math.round(origWidth * scalingFactor);
//        newHeight = Math.round(origHeight * scalingFactor);
//
//        // 计算偏移量以居中图像
//        xOffset = (targetWidth - newWidth) / 2;
//        yOffset = (targetHeight - newHeight) / 2;
//
//        // 调整图像尺寸
//        Mat resizedImage = new Mat();
//        Imgproc.resize(image, resizedImage, new Size(newWidth, newHeight), 0, 0, Imgproc.INTER_LINEAR);
//
//        // 转换为 RGB 并归一化
//        Imgproc.cvtColor(resizedImage, resizedImage, Imgproc.COLOR_BGR2RGB);
//        resizedImage.convertTo(resizedImage, CvType.CV_32FC3, 1.0 / 255.0);
//
//        // 创建填充后的图像
//        Mat paddedImage = Mat.zeros(new Size(targetWidth, targetHeight), CvType.CV_32FC3);
//        Rect roi = new Rect(xOffset, yOffset, newWidth, newHeight);
//        resizedImage.copyTo(paddedImage.submat(roi));
//
//        // 将图像数据转换为数组
//        int imageSize = targetWidth * targetHeight;
//        float[] chwData = new float[3 * imageSize];
//        float[] hwcData = new float[3 * imageSize];
//        paddedImage.get(0, 0, hwcData);
//
//        // 转换为 CHW 格式
//        int channelSize = imageSize;
//        for (int c = 0; c < 3; c++) {
//            for (int i = 0; i < imageSize; i++) {
//                chwData[c * channelSize + i] = hwcData[i * 3 + c];
//            }
//        }
//
//        // 释放图像资源
//        resizedImage.release();
//        paddedImage.release();
//
//        return chwData;
//    }

}
