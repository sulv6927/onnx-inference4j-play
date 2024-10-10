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

    // 使用HSL颜色生成更高级的颜色
    public static Color hslToRgb(float hue, float saturation, float lightness) {
        float c = (1 - Math.abs(2 * lightness - 1)) * saturation;
        float x = c * (1 - Math.abs((hue / 60) % 2 - 1));
        float m = lightness - c / 2;
        float r = 0, g = 0, b = 0;

        if (0 <= hue && hue < 60) {
            r = c;
            g = x;
        } else if (60 <= hue && hue < 120) {
            r = x;
            g = c;
        } else if (120 <= hue && hue < 180) {
            g = c;
            b = x;
        } else if (180 <= hue && hue < 240) {
            g = x;
            b = c;
        } else if (240 <= hue && hue < 300) {
            r = x;
            b = c;
        } else if (300 <= hue && hue < 360) {
            r = c;
            b = x;
        }

        int rVal = (int) ((r + m) * 255);
        int gVal = (int) ((g + m) * 255);
        int bVal = (int) ((b + m) * 255);
        return new Color(rVal, gVal, bVal);
    }

    // 根据模型索引生成颜色
    private static Color generateColorForModel(int modelIndex, int totalModels) {
        float hue = (360.0f / totalModels) * modelIndex; // 根据模型索引设置色相
        return hslToRgb(hue, 0.7f, 0.5f); // 饱和度0.7，亮度0.5
    }

    // 在 BufferedImage 上绘制推理结果
    public static void drawInferenceResult(BufferedImage bufferedImage, List<InferenceResult> inferenceResults) {
        Graphics2D g2d = bufferedImage.createGraphics();
        g2d.setFont(new Font("Arial", Font.PLAIN, 24)); // 设置字体大小为24

        int modelIndex = 0; // 模型索引
        int totalModels = inferenceResults.size(); // 总模型数

        for (InferenceResult result : inferenceResults) {
            Color modelColor = generateColorForModel(modelIndex++, totalModels); // 为每个模型生成独立颜色

            for (BoundingBox box : result.getBoundingBoxes()) {
                // 绘制矩形框
                g2d.setColor(modelColor);
                g2d.setStroke(new BasicStroke(4)); // 设置线条粗细
                g2d.drawRect(box.getX(), box.getY(), box.getWidth(), box.getHeight());

                // 获取字体度量
                FontMetrics metrics = g2d.getFontMetrics();
                int labelHeight = metrics.getHeight() + 4; // 标签高度
                String label = box.getLabel() + " " + String.format("%.2f", box.getConfidence());
                int labelWidth = metrics.stringWidth(label) + 10; // 标签宽度

                String trackIdLabel = "TrackID: " + box.getTrackId();
                int trackIdWidth = metrics.stringWidth(trackIdLabel) + 10; // TrackID标签宽度
                int trackIdHeight = metrics.getHeight() + 4; // TrackID标签高度

                // 计算标签总高度
                int totalLabelHeight = (box.getTrackId() > 0 ? trackIdHeight : 0) + labelHeight;

                // 边距
                int margin = 10;

                // 检查上方是否有足够空间绘制标签
                boolean canDrawAbove = box.getY() >= totalLabelHeight + margin;

                if (canDrawAbove) {
                    // 在检测框上方绘制标签
                    int currentY = box.getY() - totalLabelHeight;

                    // 绘制 TrackID（如果有）
                    if (box.getTrackId() > 0) {
                        // 绘制 TrackID 背景
                        g2d.setColor(modelColor);
                        g2d.fillRect(box.getX(), currentY, trackIdWidth, trackIdHeight);

                        // 绘制 TrackID 文字
                        g2d.setColor(Color.BLACK);
                        g2d.drawString(trackIdLabel, box.getX() + 5, currentY + metrics.getAscent());

                        currentY += trackIdHeight;
                    }

                    // 绘制 classid 背景
                    g2d.setColor(modelColor);
                    g2d.fillRect(box.getX(), currentY, labelWidth, labelHeight);

                    // 绘制 classid 文字
                    g2d.setColor(Color.BLACK);
                    g2d.drawString(label, box.getX() + 5, currentY + metrics.getAscent());
                } else {
                    // 如果上方空间不足，则在检测框内部顶部绘制标签
                    int currentY = box.getY() + 5; // 内边距5

                    // 绘制半透明背景以提高可读性
                    int bgAlpha = 200; // 透明度（0-255）
                    Color backgroundColor = new Color(modelColor.getRed(), modelColor.getGreen(), modelColor.getBlue(), bgAlpha);

                    if (box.getTrackId() > 0) {
                        // 绘制 TrackID 背景
                        g2d.setColor(backgroundColor);
                        g2d.fillRect(box.getX(), currentY, trackIdWidth, trackIdHeight);

                        // 绘制 TrackID 文字
                        g2d.setColor(Color.BLACK);
                        g2d.drawString(trackIdLabel, box.getX() + 5, currentY + metrics.getAscent());

                        currentY += trackIdHeight;
                    }

                    // 绘制 classid 背景
                    g2d.setColor(backgroundColor);
                    g2d.fillRect(box.getX(), currentY, labelWidth, labelHeight);

                    // 绘制 classid 文字
                    g2d.setColor(Color.BLACK);
                    g2d.drawString(label, box.getX() + 5, currentY + metrics.getAscent());
                }
            }
        }
        g2d.dispose(); // 释放资源
    }








    // 在 Mat 上绘制推理结果 (OpenCV 版本)
    public static void drawInferenceResult(Mat image, List<InferenceResult> inferenceResults) {
        int modelIndex = 0;
        int totalModels = inferenceResults.size();

        for (InferenceResult result : inferenceResults) {
            Scalar modelColor = convertColorToScalar(generateColorForModel(modelIndex++, totalModels));

            for (BoundingBox box : result.getBoundingBoxes()) {
                // 绘制矩形
                Point topLeft = new Point(box.getX(), box.getY());
                Point bottomRight = new Point(box.getX() + box.getWidth(), box.getY() + box.getHeight());
                Imgproc.rectangle(image, topLeft, bottomRight, modelColor, 3); // 加粗边框

                // 绘制标签
                String label = box.getLabel() + " " + String.format("%.2f", box.getConfidence());
                int font = Imgproc.FONT_HERSHEY_SIMPLEX;
                double fontScale = 0.7;
                int thickness = 2;

                // 计算文字大小
                int[] baseLine = new int[1];
                org.opencv.core.Size labelSize = Imgproc.getTextSize(label, font, fontScale, thickness, baseLine);

                // 确保文字不会超出图像
                int y = Math.max((int) topLeft.y, (int) labelSize.height);

                // 绘制文字背景
                Imgproc.rectangle(image, new Point(topLeft.x, y - labelSize.height),
                        new Point(topLeft.x + labelSize.width, y + baseLine[0]),
                        modelColor, Imgproc.FILLED);

                // 绘制黑色文字
                Imgproc.putText(image, label, new Point(topLeft.x, y),
                        font, fontScale, new Scalar(0, 0, 0), thickness); // 黑色文字
            }
        }
    }

    // 将 Color 转为 Scalar (用于 OpenCV)
    private static Scalar convertColorToScalar(Color color) {
        return new Scalar(color.getBlue(), color.getGreen(), color.getRed()); // OpenCV 中颜色顺序是 BGR
    }
}
