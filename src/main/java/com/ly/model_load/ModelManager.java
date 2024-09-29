package com.ly.model_load;



import com.ly.file.FileEditor;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.filechooser.FileSystemView;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

public class ModelManager extends JPanel {
    private DefaultListModel<String> modelListModel;
    private JList<String> modelList;

    public ModelManager() {
        setLayout(new BorderLayout());
        modelListModel = new DefaultListModel<>();
        modelList = new JList<>(modelListModel);
        JScrollPane modelScrollPane = new JScrollPane(modelList);
        add(modelScrollPane, BorderLayout.CENTER);

        // 添加双击事件，编辑标签文件
        modelList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int index = modelList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        String item = modelListModel.getElementAt(index);
                        // 解析标签文件路径
                        String[] parts = item.split("\n");
                        if (parts.length >= 2) {
                            String labelFilePath = parts[1].replace("标签文件: ", "").trim();
                            FileEditor.openFileEditor(labelFilePath);
                        }
                    }
                }
            }
        });
    }

    // 加载模型
    public void loadModel(JFrame parent) {
        // 获取桌面目录
        File desktopDir = FileSystemView.getFileSystemView().getHomeDirectory();
        JFileChooser fileChooser = new JFileChooser(desktopDir);
        fileChooser.setDialogTitle("选择模型文件");

        // 设置模型文件过滤器，只显示 .onnx 文件
        FileNameExtensionFilter modelFilter = new FileNameExtensionFilter("ONNX模型文件 (*.onnx)", "onnx");
        fileChooser.setFileFilter(modelFilter);

        int returnValue = fileChooser.showOpenDialog(parent);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File modelFile = fileChooser.getSelectedFile();

            // 选择对应的标签文件
            fileChooser.setDialogTitle("选择标签文件");

            // 设置标签文件过滤器，只显示 .txt 文件
            FileNameExtensionFilter labelFilter = new FileNameExtensionFilter("标签文件 (*.txt)", "txt");
            fileChooser.setFileFilter(labelFilter);

            returnValue = fileChooser.showOpenDialog(parent);
            if (returnValue == JFileChooser.APPROVE_OPTION) {
                File labelFile = fileChooser.getSelectedFile();

                // 将模型和标签文件添加到列表中
                String item = "模型文件: " + modelFile.getAbsolutePath() + "\n标签文件: " + labelFile.getAbsolutePath();
                modelListModel.addElement(item);
            } else {
                JOptionPane.showMessageDialog(parent, "未选择标签文件。", "提示", JOptionPane.WARNING_MESSAGE);
            }
        } else {
            JOptionPane.showMessageDialog(parent, "未选择模型文件。", "提示", JOptionPane.WARNING_MESSAGE);
        }
    }
}