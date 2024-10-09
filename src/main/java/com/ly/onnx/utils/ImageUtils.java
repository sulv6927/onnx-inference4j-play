package com.ly.onnx.utils;

import org.bytedeco.javacv.Frame;
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




    public static float[] frameToFloatArray(Frame frame) {
        // 获取 Frame 的宽度和高度
        int width = frame.imageWidth;
        int height = frame.imageHeight;

        // 获取 Frame 的像素数据
        Buffer buffer = frame.image[0]; // 获取图像数据缓冲区
        ByteBuffer byteBuffer = (ByteBuffer) buffer; // 假设图像数据是以字节缓冲存储

        // 创建 float 数组来存储图像的 RGB 值
        float[] result = new float[width * height * 3]; // 假设是 RGB 格式图像
        int idx = 0;

        // 遍历每个像素，提取 R, G, B 值并归一化到 [0, 1]
        for (int i = 0; i < byteBuffer.capacity(); i += 3) {
            // 提取 RGB 通道数据
            int r = byteBuffer.get(i) & 0xFF; // Red
            int g = byteBuffer.get(i + 1) & 0xFF; // Green
            int b = byteBuffer.get(i + 2) & 0xFF; // Blue

            // 将 RGB 值归一化为 float 并存入数组
            result[idx++] = r / 255.0f;
            result[idx++] = g / 255.0f;
            result[idx++] = b / 255.0f;
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
