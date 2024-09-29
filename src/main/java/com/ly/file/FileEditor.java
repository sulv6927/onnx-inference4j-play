package com.ly.file;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import javax.swing.filechooser.FileSystemView;

public class FileEditor {
    public static void openFileEditor(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            JOptionPane.showMessageDialog(null, "文件不存在：" + filePath, "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 仅对文本文件进行编辑
        if (file.getName().endsWith(".txt")) {
            JTextArea textArea = new JTextArea();
            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setPreferredSize(new Dimension(600, 400));

            // 读取文件内容
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                textArea.read(reader, null);
            } catch (IOException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(null, "无法读取文件：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            int result = JOptionPane.showConfirmDialog(null, scrollPane, "编辑文件：" + file.getName(),
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

            if (result == JOptionPane.OK_OPTION) {
                // 保存文件内容
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                    textArea.write(writer);
                    JOptionPane.showMessageDialog(null, "文件已保存。", "信息", JOptionPane.INFORMATION_MESSAGE);
                } catch (IOException ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(null, "无法保存文件：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        } else {
            JOptionPane.showMessageDialog(null, "无法编辑非文本文件。", "提示", JOptionPane.WARNING_MESSAGE);
        }
    }
}
