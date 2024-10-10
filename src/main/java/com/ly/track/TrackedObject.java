package com.ly.track;

import com.ly.onnx.model.BoundingBox;

public class TrackedObject {
    public BoundingBox boundingBox;
    private int lostFrames = 0; // 记录连续多少帧未检测到

    public TrackedObject(BoundingBox initialBox) {
        this.boundingBox = initialBox;
    }

    // 更新跟踪目标的位置
    public void update(BoundingBox newBox) {
        this.boundingBox = newBox;
        lostFrames = 0; // 重置丢失计数
    }

    // 如果目标连续丢失多帧，认为目标丢失
    public boolean isLost() {
        return lostFrames++ > 10; // 如果丢失超过10帧，就认为目标丢失
    }
}
