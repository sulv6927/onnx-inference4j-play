package com.ly.onnx.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class ModelInfo {
    private String modelFilePath;
    private String labelFilePath;
    private List <String> labels;

    public ModelInfo(String modelFilePath, String labelFilePath) {
        this.modelFilePath = modelFilePath;
        this.labelFilePath = labelFilePath;
        try {
            this.labels = Files.readAllLines(Paths.get(labelFilePath));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getModelFilePath() {
        return modelFilePath;
    }

    public String getLabelFilePath() {
        return labelFilePath;
    }

    public List<String> getLabels() {
        return labels;
    }

    @Override
    public String toString() {
        return "模型文件: " + modelFilePath + "\n标签文件: " + labelFilePath;
    }
}
