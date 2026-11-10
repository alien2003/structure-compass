import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Generates the mod's PNG assets programmatically (logo + HUD textures).
 * Run: java scripts/GenerateIcons.java   (from the project root, e.g. via the mise-managed JDK)
 */
public final class GenerateIcons {
    private static final String RES = "src/main/resources";

    public static void main(String[] args) throws IOException {
        writeLogo(new File(RES + "/structurecompass.png"), 256);
        writeArrow(new File(RES + "/assets/structurecompass/textures/gui/arrow.png"), 32);
        writeMarker(new File(RES + "/assets/structurecompass/textures/gui/marker.png"), 16);
        System.out.println("Icons generated.");
    }

    private static Graphics2D smooth(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    private static void writeLogo(File out, int s) throws IOException {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = smooth(img);
        float u = s / 256f;
        // Background: rounded square with a night-sky gradient.
        g.setPaint(new GradientPaint(0, 0, new Color(0x1B2A49), 0, s, new Color(0x0B1220)));
        g.fill(new RoundRectangle2D.Float(4 * u, 4 * u, 248 * u, 248 * u, 48 * u, 48 * u));
        // Ground strip with a small structure silhouette (tower + walls).
        g.setColor(new Color(0x2E5E3A));
        g.fillRect((int) (20 * u), (int) (196 * u), (int) (216 * u), (int) (28 * u));
        g.setColor(new Color(0xC9A227));
        g.fillRect((int) (208 * u), (int) (120 * u), (int) (24 * u), (int) (80 * u));
        g.fillRect((int) (202 * u), (int) (110 * u), (int) (36 * u), (int) (12 * u));
        for (int i = 0; i < 3; i++) {
            g.fillRect((int) ((202 + i * 13) * u), (int) (100 * u), (int) (10 * u), (int) (12 * u));
        }
        g.setColor(new Color(0x1B2A49));
        g.fillRect((int) (216 * u), (int) (140 * u), (int) (8 * u), (int) (14 * u));
        // Compass body.
        float cx = 112 * u, cy = 116 * u, r = 82 * u;
        g.setColor(new Color(0x3B2A1A));
        g.fill(new Ellipse2D.Float(cx - r - 8 * u, cy - r - 8 * u, (r + 8 * u) * 2, (r + 8 * u) * 2));
        g.setColor(new Color(0xE9E2CF));
        g.fill(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
        g.setColor(new Color(0x7A6A55));
        g.setStroke(new BasicStroke(4 * u));
        g.draw(new Ellipse2D.Float(cx - r + 10 * u, cy - r + 10 * u, (r - 10 * u) * 2, (r - 10 * u) * 2));
        // Tick marks.
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 16;
            float inner = (i % 4 == 0) ? r - 26 * u : r - 18 * u;
            float outer = r - 10 * u;
            g.setStroke(new BasicStroke((i % 4 == 0 ? 5 : 3) * u));
            g.drawLine((int) (cx + Math.sin(a) * inner), (int) (cy - Math.cos(a) * inner),
                    (int) (cx + Math.sin(a) * outer), (int) (cy - Math.cos(a) * outer));
        }
        // Needle pointing north-east towards the structure.
        double ang = Math.toRadians(40);
        double sx = Math.sin(ang), sy = -Math.cos(ang);
        double px = Math.cos(ang), py = Math.sin(ang);
        float len = r - 22 * u, wid = 14 * u;
        Polygon north = new Polygon();
        north.addPoint((int) (cx + sx * len), (int) (cy + sy * len));
        north.addPoint((int) (cx + px * wid), (int) (cy + py * wid));
        north.addPoint((int) (cx - px * wid), (int) (cy - py * wid));
        Polygon south = new Polygon();
        south.addPoint((int) (cx - sx * len), (int) (cy - sy * len));
        south.addPoint((int) (cx + px * wid), (int) (cy + py * wid));
        south.addPoint((int) (cx - px * wid), (int) (cy - py * wid));
        g.setColor(new Color(0xD7263D));
        g.fillPolygon(north);
        g.setColor(new Color(0x4A4E69));
        g.fillPolygon(south);
        g.setColor(new Color(0x222222));
        g.fill(new Ellipse2D.Float(cx - 8 * u, cy - 8 * u, 16 * u, 16 * u));
        g.dispose();
        write(img, out);
    }

    /** White upward arrow on transparent background; tinted at render time. */
    private static void writeArrow(File out, int s) throws IOException {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = smooth(img);
        float u = s / 32f;
        Polygon p = new Polygon();
        p.addPoint((int) (16 * u), (int) (1 * u));
        p.addPoint((int) (30 * u), (int) (28 * u));
        p.addPoint((int) (16 * u), (int) (21 * u));
        p.addPoint((int) (2 * u), (int) (28 * u));
        g.setColor(new Color(0, 0, 0, 160));
        g.setStroke(new BasicStroke(2.5f * u, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawPolygon(p);
        g.setColor(Color.WHITE);
        g.fillPolygon(p);
        g.dispose();
        write(img, out);
    }

    /** White diamond marker for the compass bar; tinted at render time. */
    private static void writeMarker(File out, int s) throws IOException {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = smooth(img);
        float u = s / 16f;
        Polygon p = new Polygon();
        p.addPoint((int) (8 * u), (int) (1 * u));
        p.addPoint((int) (15 * u), (int) (8 * u));
        p.addPoint((int) (8 * u), (int) (15 * u));
        p.addPoint((int) (1 * u), (int) (8 * u));
        g.setColor(Color.WHITE);
        g.fillPolygon(p);
        g.setColor(new Color(0, 0, 0, 170));
        g.setStroke(new BasicStroke(1.2f * u));
        g.drawPolygon(p);
        g.dispose();
        write(img, out);
    }

    private static void write(BufferedImage img, File out) throws IOException {
        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
    }
}
