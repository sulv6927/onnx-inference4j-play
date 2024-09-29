package com.ly.layout;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class VideoPanel extends JPanel {
    private BufferedImage image;

    public void updateImage(BufferedImage img) {
        this.image = img;
        repaint();
    }

    public void clearImage() {
        this.image = null;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (image != null) {
            Graphics2D g2d = (Graphics2D) g.create();

            // 启用抗锯齿和高质量渲染
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 缩放图片以适应面板大小
            int panelWidth = this.getWidth();
            int panelHeight = this.getHeight();
            double imgAspect = (double) image.getWidth() / image.getHeight();
            double panelAspect = (double) panelWidth / panelHeight;

            int drawWidth, drawHeight;
            if (imgAspect > panelAspect) {
                drawWidth = panelWidth;
                drawHeight = (int) (panelWidth / imgAspect);
            } else {
                drawHeight = panelHeight;
                drawWidth = (int) (panelHeight * imgAspect);
            }

            g2d.drawImage(image, (panelWidth - drawWidth) / 2, (panelHeight - drawHeight) / 2,
                    drawWidth, drawHeight, null);

            g2d.dispose();
        }
    }

}
