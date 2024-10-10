package com.ly.onnx.utils;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.awt.image.BufferedImage;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class ImageUtils {

    // 辅助方法：将 BufferedImage 转换为浮点数组（根据您的模型需求）
    private static float[] preprocessImage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        float[] result = new float[width * height * 3]; // 假设是 RGB 图像
        int idx = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = image.getRGB(x, y);
                // 分别获取 R, G, B 值并归一化（假设归一化到 [0, 1]）
                result[idx++] = ((pixel >> 16) & 0xFF) / 255.0f; // Red
                result[idx++] = ((pixel >> 8) & 0xFF) / 255.0f;  // Green
                result[idx++] = (pixel & 0xFF) / 255.0f;         // Blue
            }
        }
        return result;
    }


    // 将 Mat 转换为 BufferedImage
    public static BufferedImage matToBufferedImage(Mat mat) {
        int type = BufferedImage.TYPE_3BYTE_BGR;
        if (mat.channels() == 1) {
            type = BufferedImage.TYPE_BYTE_GRAY;
        }
        int bufferSize = mat.channels() * mat.cols() * mat.rows();
        byte[] buffer = new byte[bufferSize];
        mat.get(0, 0, buffer); // 获取所有像素
        BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), type);
        final byte[] targetPixels = ((java.awt.image.DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(buffer, 0, targetPixels, 0, buffer.length);
        return image;
    }


}
