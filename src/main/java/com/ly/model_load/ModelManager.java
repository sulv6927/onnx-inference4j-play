package com.ly.model_load;

import com.ly.file.FileEditor;
import com.ly.onnx.engine.InferenceEngine;
import com.ly.onnx.model.ModelInfo;
import com.ly.play.opencv.VideoPlayer;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.io.BufferedReader;


public class ModelManager extends JPanel {
    private DefaultListModel<ModelInfo> modelListModel;
    private JList<ModelInfo> modelList;
    private JPopupMenu popupMenu;
    private VideoPlayer videoPlayer;


    public ModelManager() {
        setLayout(new BorderLayout());
        modelListModel = new DefaultListModel<>();
        modelList = new JList<>(modelListModel);
        modelList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); // 设置为单选
        JScrollPane modelScrollPane = new JScrollPane(modelList);
        add(modelScrollPane, BorderLayout.CENTER);

        // 创建右键菜单
        popupMenu = new JPopupMenu();
        JMenuItem deleteMenuItem = new JMenuItem("删除");
        popupMenu.add(deleteMenuItem);

        // 为模型列表添加右键菜单
        modelList.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) { // 如果是右键触发
                    showPopup(e);
                }
            }

            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) { // 如果是右键触发
                    showPopup(e);
                }
            }

            private void showPopup(MouseEvent e) {
                int index = modelList.locationToIndex(e.getPoint());
                if (index != -1) {
                    modelList.setSelectedIndex(index); // 选中右键点击的行
                    popupMenu.show(modelList, e.getX(), e.getY());
                }
            }
        });

        // 为删除菜单项添加操作
        deleteMenuItem.addActionListener(e -> {
            int selectedIndex = modelList.getSelectedIndex();
            if (selectedIndex != -1) {
                int confirmation = JOptionPane.showConfirmDialog(null, "确定要删除此模型吗？", "确认删除", JOptionPane.YES_NO_OPTION);
                if (confirmation == JOptionPane.YES_OPTION) {
                    modelListModel.remove(selectedIndex); // 删除选中的模型
                }
            }
        });

        // 双击编辑标签文件
        modelList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int index = modelList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        ModelInfo item = modelListModel.getElementAt(index);
                        String labelFilePath = item.getLabelFilePath();
                        FileEditor.openFileEditor(labelFilePath);
                    }
                }
            }
        });

        // 设置拖拽功能处理模型和标签文件
        setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) {
                    return false;
                }
                try {
                    // 获取拖拽的文件列表
                    List<File> files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (files.size() == 2) { // 确保拖拽的是两个文件
                        File modelFile = null;
                        File labelFile = null;

                        for (File file : files) {
                            if (file.getName().endsWith(".onnx")) {
                                modelFile = file;
                            } else if (file.getName().endsWith(".txt")) {
                                labelFile = file;
                            }
                        }

                        if (modelFile != null && labelFile != null) {
                            // 确保 videoPlayer 被正确设置
                            if (videoPlayer == null) {
                                throw new IllegalStateException("VideoPlayer is not set in ModelManager.");
                            }

                            // 添加模型信息到列表
                            ModelInfo modelInfo = new ModelInfo(modelFile.getAbsolutePath(), labelFile.getAbsolutePath());
                            modelListModel.addElement(modelInfo);

                            // 读取标签文件内容，转为 List<String>
                            List<String> labels = new ArrayList<>();
                            try (BufferedReader reader = new BufferedReader(new FileReader(labelFile))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    labels.add(line.trim());
                                }
                            }

                            // 创建推理引擎并传递给 VideoPlayer
                            InferenceEngine inferenceEngine = new InferenceEngine(modelFile.getAbsolutePath(), labels);
                            videoPlayer.addInferenceEngines(inferenceEngine);
                            return true;
                        } else {
                            JOptionPane.showMessageDialog(null, "请拖入一个 .onnx 文件和一个 .txt 文件。", "提示", JOptionPane.WARNING_MESSAGE);
                        }
                    } else {
                        JOptionPane.showMessageDialog(null, "请拖入两个文件：一个 .onnx 文件和一个 .txt 文件。", "提示", JOptionPane.WARNING_MESSAGE);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    return false;
                }
                return false;
            }
        });
    }

    // 添加设置 VideoPlayer 的方法
    public void setVideoPlayer(VideoPlayer videoPlayer) {
        this.videoPlayer = videoPlayer;
    }


    // 加载模型
    public void loadModel(JFrame parent) {
        File desktopDir = FileSystemView.getFileSystemView().getHomeDirectory();
        JFileChooser fileChooser = new JFileChooser(desktopDir);
        fileChooser.setDialogTitle("选择模型文件");
        FileNameExtensionFilter modelFilter = new FileNameExtensionFilter("ONNX模型文件 (*.onnx)", "onnx");
        fileChooser.setFileFilter(modelFilter);

        int returnValue = fileChooser.showOpenDialog(parent);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File modelFile = fileChooser.getSelectedFile();

            // 选择对应的标签文件
            fileChooser.setDialogTitle("选择标签文件");
            FileNameExtensionFilter labelFilter = new FileNameExtensionFilter("标签文件 (*.txt)", "txt");
            fileChooser.setFileFilter(labelFilter);

            returnValue = fileChooser.showOpenDialog(parent);
            if (returnValue == JFileChooser.APPROVE_OPTION) {
                File labelFile = fileChooser.getSelectedFile();

                // 添加模型信息到列表
                ModelInfo modelInfo = new ModelInfo(modelFile.getAbsolutePath(), labelFile.getAbsolutePath());
                modelListModel.addElement(modelInfo);
            } else {
                JOptionPane.showMessageDialog(parent, "未选择标签文件。", "提示", JOptionPane.WARNING_MESSAGE);
            }
        } else {
            JOptionPane.showMessageDialog(parent, "未选择模型文件。", "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    // 获取选中的模型
    public ModelInfo getSelectedModel() {
        return modelList.getSelectedValue();
    }

    // 如果需要在外部访问 modelList，可以添加以下方法
    public DefaultListModel<ModelInfo> getModelList() {
        return modelListModel;
    }
}
