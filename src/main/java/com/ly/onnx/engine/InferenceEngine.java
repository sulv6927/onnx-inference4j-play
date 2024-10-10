package com.ly.onnx.engine;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Data
public class InferenceEngine {

    private OrtEnvironment environment;
    private OrtSession.SessionOptions sessionOptions;
    private OrtSession session;

    private String modelPath;
    private List<String> labels;

    //preprocessParams输入数据的索引
    private Integer index;

    // 用于存储图像预处理信息的类变量
    private long[] inputShape = null;

    static {
        nu.pattern.OpenCV.loadLocally();
    }

    public InferenceEngine(String modelPath, List<String> labels) {
        this.modelPath = modelPath;
        this.labels = labels;
        initAsync();
    }
    // 异步执行模型初始化
    public void initAsync() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(()->{
            init();
            warmUp();
        });
    }

    public void warmUp() {
        // 提前执行一次空推理，用于初始化模型、CUDA上下文等
        try {
            float[] dummyInput = new float[(int) inputShape[0] * (int) inputShape[1] * (int) inputShape[2] * (int) inputShape[3]];
            OnnxTensor inputTensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(dummyInput), inputShape);
            String inputName = session.getInputInfo().keySet().iterator().next();
            // 执行空推理
            session.run(Collections.singletonMap(inputName, inputTensor));
            inputTensor.close();
            System.out.println("预热推理完成，首次推理性能已优化。");
        } catch (Exception e) {
            throw new RuntimeException("模型预热失败", e);
        }
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

    public InferenceResult infer(Map<Integer, Object> preprocessParams) {
        long startTime = System.currentTimeMillis();
        //获取对模型需要的输入大小
        Map<String, Object> params = (Map<String, Object>) preprocessParams.get(index);
        // 从 Map 中获取偏移相关的变量
        float[] inputData = (float[]) params.get("inputData");
        int origWidth = (int) params.get("origWidth");
        int origHeight = (int) params.get("origHeight");
        float scalingFactor = (float) params.get("scalingFactor");
        int xOffset = (int) params.get("xOffset");
        int yOffset = (int) params.get("yOffset");

        try {
            Map<String, NodeInfo> inputInfo = session.getInputInfo();
            String inputName = inputInfo.keySet().iterator().next(); // 假设只有一个输入

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
    public String getModelPath() {
        return this.modelPath;
    }

}
