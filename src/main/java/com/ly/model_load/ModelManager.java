package com.ly.model_load;

import com.ly.file.FileEditor;
import com.ly.onnx.model.ModelInfo;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

public class ModelManager extends JPanel {
    private DefaultListModel<ModelInfo> modelListModel;
    private JList<ModelInfo> modelList;

    public ModelManager() {
        setLayout(new BorderLayout());
        modelListModel = new DefaultListModel<>();
        modelList = new JList<>(modelListModel);
        modelList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); // 设置为单选
        JScrollPane modelScrollPane = new JScrollPane(modelList);
        add(modelScrollPane, BorderLayout.CENTER);

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
