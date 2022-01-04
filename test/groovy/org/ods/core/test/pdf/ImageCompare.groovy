package org.ods.core.test.pdf

import groovy.util.logging.Slf4j

import javax.imageio.ImageIO
import java.awt.Color
import java.awt.image.BufferedImage

@Slf4j
class ImageCompare {
    private static final int colorCode = Color.MAGENTA.getRGB()
    private static final String PNG = "png"

    BufferedImage compareAndHighlightDiffInNewImage(final BufferedImage img1, final BufferedImage img2) throws IOException {
        final int width = img1.getWidth()
        final int height = img1.getHeight()

        final int[] p1 = img1.getRGB(0, 0, width, height, null, 0, width)
        final int[] p2 = img2.getRGB(0, 0, width, height, null, 0, width)
        if(!(java.util.Arrays.equals(p1, p2))){
            return imageDiff(p1, p2, width, height)
        }
        return null
    }

    void saveImage(BufferedImage image, String file){
        try{
            ImageIO.write(image, PNG, new File(file))
        } catch(Exception e) {
            throw new RuntimeException("Error saving image error", e)
        }
    }

    private BufferedImage imageDiff(int[] p1, int[] p2, int width, int height) {
        for (int i = 0; i < p1.length; i++) {
            if (p1[i] != p2[i]) {
                p1[i] = colorCode
            }
        }

        final BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        out.setRGB(0, 0, width, height, p1, 0, width)
        return out
    }
}
