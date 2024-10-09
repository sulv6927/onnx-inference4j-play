package com.ly.onnx;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

public class OnnxModelInference {

    private String modelFilePath;

    private String labelFilePath;

    private String[] labels;

    OrtEnvironment environment = OrtEnvironment.getEnvironment();
    OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();




}
