package com.ly.onnx.model;

import java.util.ArrayList;
import java.util.List;

public class InferenceResult {
    private List<BoundingBox> boundingBoxes = new ArrayList<>();

    public List<BoundingBox> getBoundingBoxes() {
        return boundingBoxes;
    }

    public void setBoundingBoxes(List<BoundingBox> boundingBoxes) {
        this.boundingBoxes = boundingBoxes;
    }

    // 其他需要的属性和方法
}
