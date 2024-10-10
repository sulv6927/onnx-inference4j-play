package com.ly;

import com.formdev.flatlaf.FlatLightLaf;
import com.ly.layout.VideoPanel;
import com.ly.model_load.ModelManager;
import com.ly.onnx.engine.InferenceEngine;
import com.ly.onnx.model.ModelInfo;
import com.ly.play.opencv.VideoPlayer;


import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

public class VideoInferenceApp extends JFrame {

    private VideoPanel videoPanel; // 视频显示面板
    private JPanel controlPanel;   // 操作按钮区域
    private JTextField streamUrlField; // 流地址输入框

    private VideoPlayer videoPlayer;
    private ModelManager modelManager;



    public VideoInferenceApp() {
        // 设置窗口标题
        super("https://gitee.com/sulv0302/onnx-inference4j-play.git");
        // 初始化UI组件
        initializeUI();
    }

    private void initializeUI() {
        // 设置 FlatLaf 主题
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 设置布局管理器
        this.setLayout(new BorderLayout());

        // 视频显示区域
        videoPanel = new VideoPanel();
        videoPanel.setBackground(Color.BLACK);

        // 模型列表区域
        modelManager = new ModelManager();
        modelManager.setPreferredSize(new Dimension(250, 0)); // 设置模型列表区域的宽度

        // 初始化 VideoPlayer
        videoPlayer = new VideoPlayer(videoPanel, modelManager);

        // 使用 JSplitPane 分割视频区域和模型列表区域
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, videoPanel, modelManager);
        splitPane.setResizeWeight(0.8); // 视频区域初始占据80%的空间
        splitPane.setDividerLocation(0.8);
        this.add(splitPane, BorderLayout.CENTER);

        // 操作按钮区域
        controlPanel = new JPanel();
        controlPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 5));

        // 创建控制按钮
        JButton playButton = new JButton("播放");
        JButton pauseButton = new JButton("暂停");
        JButton replayButton = new JButton("重播");
        JButton fastForward5sButton = new JButton("快进5秒");
        JButton rewind5sButton = new JButton("后退5秒");

        // 设置按钮的统一高度
        Dimension buttonSize = new Dimension(100, 30);
        playButton.setPreferredSize(buttonSize);
        pauseButton.setPreferredSize(buttonSize);
        replayButton.setPreferredSize(buttonSize);
        fastForward5sButton.setPreferredSize(buttonSize);
        rewind5sButton.setPreferredSize(buttonSize);

        // 添加按钮到控制面板
        controlPanel.add(playButton);
        controlPanel.add(pauseButton);
        controlPanel.add(replayButton);
        controlPanel.add(rewind5sButton);
        controlPanel.add(fastForward5sButton);

        this.add(controlPanel, BorderLayout.SOUTH);

        // 顶部部分 - 视频和模型加载，流地址
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));

        // 视频文件选择按钮
        JButton loadVideoButton = new JButton("选择视频文件");
        loadVideoButton.setPreferredSize(new Dimension(150, 30));

        // 模型文件选择按钮
        JButton loadModelButton = new JButton("选择模型");
        loadModelButton.setPreferredSize(new Dimension(150, 30));

        // 流地址输入框
        streamUrlField = new JTextField(15); // 流地址输入框

        // 开始播放按钮
        JButton startPlayButton = new JButton("开始播放");
        startPlayButton.setPreferredSize(new Dimension(100, 30));

        // 将按钮和输入框添加到顶部面板
        topPanel.add(loadVideoButton);
        topPanel.add(loadModelButton);
        topPanel.add(new JLabel("流地址:"));
        topPanel.add(streamUrlField);
        topPanel.add(startPlayButton);

        this.add(topPanel, BorderLayout.NORTH);

        // 设置窗口属性
        this.setSize(1280, 720);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setLocationRelativeTo(null); // 居中显示
        this.setVisible(true);

        // 添加视频加载按钮的行为
        loadVideoButton.addActionListener(e -> selectVideoFile());

        loadModelButton.addActionListener(e -> {
            modelManager.loadModel(this);
            DefaultListModel<ModelInfo> modelList = modelManager.getModelList();
            ArrayList<ModelInfo> models = Collections.list(modelList.elements());
            for (ModelInfo modelInfo : models) {
                if (modelInfo != null) {
                    boolean alreadyAdded = videoPlayer.getInferenceEngines().stream()
                            .anyMatch(engine -> engine.getModelPath().equals(modelInfo.getModelFilePath()));
                    if (!alreadyAdded) {
                        videoPlayer.addInferenceEngines(new InferenceEngine(modelInfo.getModelFilePath(), modelInfo.getLabels()));
                    }
                }
            }
        });


        // 播放按钮
        playButton.addActionListener(e -> videoPlayer.playVideo());

        // 暂停按钮
        pauseButton.addActionListener(e -> videoPlayer.pauseVideo());

//        // 重播按钮
//        replayButton.addActionListener(e -> videoPlayer.replayVideo());
//
//        // 后退5秒
//        rewind5sButton.addActionListener(e -> videoPlayer.rewind(5000));
//
//        // 快进5秒
//        fastForward5sButton.addActionListener(e -> videoPlayer.fastForward(5000));

        // 开始播放按钮的行为
        startPlayButton.addActionListener(e -> {
            String streamUrl = streamUrlField.getText().trim();
            if (!streamUrl.isEmpty()) {
                try {
                    videoPlayer.loadVideo(streamUrl);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(this, "无法播放流地址: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(this, "请输入流地址或摄像头索引。", "提示", JOptionPane.WARNING_MESSAGE);
            }
        });
    }

    // 选择视频文件
    private void selectVideoFile() {
        File desktopDir = FileSystemView.getFileSystemView().getHomeDirectory();
        JFileChooser fileChooser = new JFileChooser(desktopDir);
        fileChooser.setDialogTitle("选择视频文件");
        // 设置视频文件过滤器，支持常见的视频格式
        FileNameExtensionFilter videoFilter = new FileNameExtensionFilter(
                "视频文件 (*.mp4;*.avi;*.mkv;*.mov;*.flv;*.wmv)", "mp4", "avi", "mkv", "mov", "flv", "wmv");
        fileChooser.setFileFilter(videoFilter);

        int returnValue = fileChooser.showOpenDialog(this);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            try {
                videoPlayer.loadVideo(selectedFile.getAbsolutePath());
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "加载视频失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(VideoInferenceApp::new);
    }
}
