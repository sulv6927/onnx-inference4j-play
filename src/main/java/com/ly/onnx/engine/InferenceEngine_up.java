package com.ly.onnx.engine;

import ai.onnxruntime.*;
import com.ly.onnx.model.BoundingBox;
import com.ly.onnx.model.InferenceResult;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.*;

public class InferenceEngine_up {

    OrtEnvironment environment = OrtEnvironment.getEnvironment();
    OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();

    private String modelPath;
    private List<String> labels;

    // 添加用于存储图像预处理信息的类变量
    private int origWidth;
    private int origHeight;
    private int newWidth;
    private int newHeight;
    private float scalingFactor;
    private int xOffset;
    private int yOffset;

    public InferenceEngine_up(String modelPath, List<String> labels) {
        this.modelPath = modelPath;
        this.labels = labels;
        init();
    }

    public void init() {
        OrtSession session = null;
        try {
            sessionOptions.addCUDA(0);
            session = environment.createSession(modelPath, sessionOptions);

        } catch (OrtException e) {
            throw new RuntimeException(e);
        }
        logModelInfo(session);
    }

    public InferenceResult infer(float[] inputData, int w, int h) {
        // 创建ONNX输入Tensor
        try (OrtSession session = environment.createSession(modelPath, sessionOptions)) {
            Map<String, NodeInfo> inputInfo = session.getInputInfo();
            String inputName = inputInfo.keySet().iterator().next(); // 假设只有一个输入

            long[] inputShape = {1, 3, h, w}; // 根据模型需求调整形状
            OnnxTensor inputTensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(inputData), inputShape);

            // 执行推理
            OrtSession.Result result = session.run(Collections.singletonMap(inputName, inputTensor));

            // 解析推理结果
            String outputName = session.getOutputInfo().keySet().iterator().next(); // 假设只有一个输出
            float[][][] outputData = (float[][][]) result.get(outputName).get().getValue(); // 输出形状：[1, N, 5]

            long l = System.currentTimeMillis();
            // 设定置信度阈值
            float confidenceThreshold = 0.5f; // 您可以根据需要调整

            // 根据模型的输出结果解析边界框
            List<BoundingBox> boxes = new ArrayList<>();
            for (float[] data : outputData[0]) { // 遍历所有检测框
                float confidence = data[4];
                if (confidence >= confidenceThreshold) {
                    float xCenter = data[0];
                    float yCenter = data[1];
                    float widthBox = data[2];
                    float heightBox = data[3];

                    // 调整坐标，减去偏移并除以缩放因子
                    float xCenterAdjusted = (xCenter - xOffset) / scalingFactor;
                    float yCenterAdjusted = (yCenter - yOffset) / scalingFactor;
                    float widthAdjusted = widthBox / scalingFactor;
                    float heightAdjusted = heightBox / scalingFactor;

                    // 计算左上角坐标
                    int x = (int) (xCenterAdjusted - widthAdjusted / 2);
                    int y = (int) (yCenterAdjusted - heightAdjusted / 2);
                    int wBox = (int) widthAdjusted;
                    int hBox = (int) heightAdjusted;

                    // 确保坐标在原始图像范围内
                    if (x < 0) x = 0;
                    if (y < 0) y = 0;
                    if (x + wBox > origWidth) wBox = origWidth - x;
                    if (y + hBox > origHeight) hBox = origHeight - y;

                    String label = "person"; // 由于只有一个类别

                    boxes.add(new BoundingBox(x, y, wBox, hBox, label, confidence));
                }
            }

            // 非极大值抑制（NMS）
            List<BoundingBox> nmsBoxes = nonMaximumSuppression(boxes, 0.5f);
            System.out.println("耗时："+((System.currentTimeMillis() - l)));
            // 封装结果并返回
            InferenceResult inferenceResult = new InferenceResult();
            inferenceResult.setBoundingBoxes(nmsBoxes);
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
        // 初始化标签列表
        List<String> labels = Arrays.asList("person");

        // 创建 InferenceEngine 实例
        InferenceEngine_up inferenceEngine = new InferenceEngine_up("D:\\work\\work_space\\java\\onnx-inference4j-play\\src\\main\\resources\\model\\best.onnx", labels);

        try {
            // 加载图片
            File imageFile = new File("C:\\Users\\ly\\Desktop\\resuouce\\image\\1.jpg");
            BufferedImage inputImage = ImageIO.read(imageFile);

            // 预处理图像
            float[] inputData = inferenceEngine.preprocessImage(inputImage);

            // 执行推理
            InferenceResult result = null;
            for (int i = 0; i < 100; i++) {
                long l = System.currentTimeMillis();
                result = inferenceEngine.infer(inputData, 640, 640);
                System.out.println(System.currentTimeMillis() - l);
            }

            // 处理并显示结果
            System.out.println("推理结果:");
            for (BoundingBox box : result.getBoundingBoxes()) {
                System.out.println(box);
            }

            // 可视化并保存带有边界框的图像
            BufferedImage outputImage = inferenceEngine.drawBoundingBoxes(inputImage, result.getBoundingBoxes());

            // 保存图片到本地文件
            File outputFile = new File("output_image_with_boxes.jpg");
            ImageIO.write(outputImage, "jpg", outputFile);

            System.out.println("已保存结果图片: " + outputFile.getAbsolutePath());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 在图像上绘制边界框和标签
    BufferedImage drawBoundingBoxes(BufferedImage image, List<BoundingBox> boxes) {
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED); // 设置绘制边界框的颜色
        g.setStroke(new BasicStroke(2)); // 设置线条粗细

        for (BoundingBox box : boxes) {
            // 绘制矩形边界框
            g.drawRect(box.getX(), box.getY(), box.getWidth(), box.getHeight());
            // 绘制标签文字和置信度
            String label = box.getLabel() + " " + String.format("%.2f", box.getConfidence());
            g.setFont(new Font("Arial", Font.PLAIN, 12));
            g.drawString(label, box.getX(), box.getY() - 5);
        }

        g.dispose(); // 释放资源
        return image;
    }

    // 图像预处理
    float[] preprocessImage(BufferedImage image) {
        int targetWidth = 640;
        int targetHeight = 640;

        origWidth = image.getWidth();
        origHeight = image.getHeight();

        // 计算缩放因子
        scalingFactor = Math.min((float) targetWidth / origWidth, (float) targetHeight / origHeight);

        // 计算新的图像尺寸
        newWidth = Math.round(origWidth * scalingFactor);
        newHeight = Math.round(origHeight * scalingFactor);

        // 计算偏移量以居中图像
        xOffset = (targetWidth - newWidth) / 2;
        yOffset = (targetHeight - newHeight) / 2;

        // 创建一个新的BufferedImage
        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resizedImage.createGraphics();

        // 填充背景为黑色
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, targetWidth, targetHeight);

        // 绘制缩放后的图像到新的图像上
        g.drawImage(image.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH), xOffset, yOffset, null);
        g.dispose();

        float[] inputData = new float[3 * targetWidth * targetHeight];

        for (int c = 0; c < 3; c++) {
            for (int y = 0; y < targetHeight; y++) {
                for (int x = 0; x < targetWidth; x++) {
                    int rgb = resizedImage.getRGB(x, y);
                    float value = 0f;
                    if (c == 0) {
                        value = ((rgb >> 16) & 0xFF) / 255.0f; // Red channel
                    } else if (c == 1) {
                        value = ((rgb >> 8) & 0xFF) / 255.0f; // Green channel
                    } else if (c == 2) {
                        value = (rgb & 0xFF) / 255.0f; // Blue channel
                    }
                    inputData[c * targetWidth * targetHeight + y * targetWidth + x] = value;
                }
            }
        }

        return inputData;
    }

    // 非极大值抑制（NMS）方法
    private List<BoundingBox> nonMaximumSuppression(List<BoundingBox> boxes, float iouThreshold) {
        // 按置信度排序
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

    // 其他方法保持不变...
}
