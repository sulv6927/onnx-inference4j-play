package com.ly.track;

import com.ly.onnx.model.BoundingBox;
import lombok.Data;

import java.awt.*;
import java.util.*;
import java.util.List;


public class SimpleTracker {
    private Map<Long, TrackedObject> trackedObjects = new HashMap<>(); // 使用自定义 TrackedObject 来跟踪
    private long currentTrackId = 0;

    // 跟踪器更新方法
    public List<BoundingBox> update(List<BoundingBox> detections) {
        List<BoundingBox> updatedResults = new ArrayList<>();

        for (BoundingBox detection : detections) {
            boolean matched = false;
            Point detectionCenter = getCenter(detection); // 获取当前检测目标的中心点

            // 遍历现有的跟踪目标
            for (Map.Entry<Long, TrackedObject> entry : trackedObjects.entrySet()) {
                TrackedObject trackedObject = entry.getValue();
                Point trackedCenter = getCenter(trackedObject.boundingBox);

                // 使用中心点欧几里得距离进行匹配
                double distance = euclideanDistance(detectionCenter, trackedCenter);

                // 如果距离小于某个阈值，认为是同一目标
                if (distance < 50.0) { // 自定义距离阈值，可以根据需要调整
                    detection.setTrackId(entry.getKey()); // 更新检测框的 trackId
                    trackedObject.update(detection); // 更新跟踪对象
                    matched = true;
                    break;
                }
            }

            // 如果没有匹配到，创建新的 trackId
            if (!matched) {
                long newTrackId = ++currentTrackId;
                detection.setTrackId(newTrackId);
                trackedObjects.put(newTrackId, new TrackedObject(detection));
            }

            updatedResults.add(detection);
        }

        // 清理丢失的目标
        cleanupLostObjects();

        return updatedResults;
    }

    // 计算目标的中心点
    private Point getCenter(BoundingBox box) {
        int centerX = box.getX() + box.getWidth() / 2;
        int centerY = box.getY() + box.getHeight() / 2;
        return new Point(centerX, centerY);
    }

    // 计算欧几里得距离
    private double euclideanDistance(Point p1, Point p2) {
        return Math.sqrt(Math.pow(p1.x - p2.x, 2) + Math.pow(p1.y - p2.y, 2));
    }

    // 清理丢失的跟踪对象（例如不再检测到的对象）
    private void cleanupLostObjects() {
        // 可以根据时间戳或其他条件来清理长时间没有更新的目标
        trackedObjects.entrySet().removeIf(entry -> entry.getValue().isLost());
    }
}
