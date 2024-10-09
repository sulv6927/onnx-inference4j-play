package com.ly.onnx.utils;

import com.ly.onnx.model.BoundingBox;
import com.ly.onnx.model.InferenceResult;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

public class DrawImagesUtils {


    public static void drawInferenceResult(BufferedImage bufferedImage, List<InferenceResult> inferenceResults) {
        Graphics2D g2d = bufferedImage.createGraphics();
        g2d.setFont(new Font("Arial", Font.PLAIN, 12));

        for (InferenceResult result : inferenceResults) {
            for (BoundingBox box : result.getBoundingBoxes()) {
                // 绘制矩形
                g2d.setColor(Color.RED);
                g2d.drawRect(box.getX(), box.getY(), box.getWidth(), box.getHeight());

                // 绘制标签
                String label = box.getLabel() + " " + String.format("%.2f", box.getConfidence());
                FontMetrics metrics = g2d.getFontMetrics();
                int labelWidth = metrics.stringWidth(label);
                int labelHeight = metrics.getHeight();

                // 确保文字不会超出图像
                int y = Math.max(box.getY(), labelHeight);

                // 绘制文字背景
                g2d.setColor(Color.RED);
                g2d.fillRect(box.getX(), y - labelHeight, labelWidth, labelHeight);

                // 绘制文字
                g2d.setColor(Color.WHITE);
                g2d.drawString(label, box.getX(), y);
            }
        }
        g2d.dispose(); // 释放资源
    }


    // 在 Mat 上绘制推理结果
    public static void drawInferenceResult(Mat image, List<InferenceResult> inferenceResults) {
        for (InferenceResult result : inferenceResults) {
            for (BoundingBox box : result.getBoundingBoxes()) {
                // 绘制矩形
                Point topLeft = new Point(box.getX(), box.getY());
                Point bottomRight = new Point(box.getX() + box.getWidth(), box.getY() + box.getHeight());
                Imgproc.rectangle(image, topLeft, bottomRight, new Scalar(0, 0, 255), 2); // 红色边框

                // 绘制标签
                String label = box.getLabel() + " " + String.format("%.2f", box.getConfidence());
                int font = Imgproc.FONT_HERSHEY_SIMPLEX;
                double fontScale = 0.5;
                int thickness = 1;

                // 计算文字大小
                int[] baseLine = new int[1];
                org.opencv.core.Size labelSize = Imgproc.getTextSize(label, font, fontScale, thickness, baseLine);

                // 确保文字不会超出图像
                int y = Math.max((int) topLeft.y, (int) labelSize.height);

                // 绘制文字背景
                Imgproc.rectangle(image, new Point(topLeft.x, y - labelSize.height),
                        new Point(topLeft.x + labelSize.width, y + baseLine[0]),
                        new Scalar(0, 0, 255), Imgproc.FILLED);

                // 绘制文字
                Imgproc.putText(image, label, new Point(topLeft.x, y),
                        font, fontScale, new Scalar(255, 255, 255), thickness);
            }
        }
    }
}
