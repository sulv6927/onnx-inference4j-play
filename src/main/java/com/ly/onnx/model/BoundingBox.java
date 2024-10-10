package com.ly.onnx.model;

import lombok.Data;

@Data
public class BoundingBox {
    private int x;
    private int y;
    private int width;
    private int height;
    private String label;
    private float confidence;
    private long trackId;

    // 构造函数、getter 和 setter 方法

    public BoundingBox(int x, int y, int width, int height, String label, float confidence) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.label = label;
        this.confidence = confidence;
    }


}
