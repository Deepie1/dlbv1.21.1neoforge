import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;

/**
 * T372-VOXEL-WAVE: standalone, out-of-game animation study.
 * Run with: javac VoxelTsunamiPreview.java && java VoxelTsunamiPreview
 * No mod classes or external libraries are used.
 */
public final class VoxelTsunamiPreview extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final int W = 960, H = 540, FLOOR = 424, CELL = 12, DEPTH = 3;
    private static final long LOOP_MS = 6600L;
    private long started = System.nanoTime();
    private boolean paused;
    private long pausedElapsedMs;
    private final Timer timer;

    public VoxelTsunamiPreview() {
        setPreferredSize(new Dimension(W, H));
        setBackground(new Color(17, 42, 58));
        timer = new Timer(33, e -> repaint());
        timer.start();
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getY() > H - 38 && e.getX() < 96) {
                    if (paused) {
                        started = System.nanoTime() - pausedElapsedMs * 1_000_000L;
                        paused = false;
                    } else {
                        pausedElapsedMs = (System.nanoTime() - started) / 1_000_000L;
                        paused = true;
                    }
                } else if (e.getY() > H - 38 && e.getX() < 202) {
                    started = System.nanoTime(); pausedElapsedMs = 0; paused = false;
                }
            }
        });
    }

    private double seconds() {
        long elapsed = paused ? pausedElapsedMs : (System.nanoTime() - started) / 1_000_000L;
        return (elapsed % LOOP_MS) / 1000.0;
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.scale(getWidth() / (double) W, getHeight() / (double) H);
        double t = seconds();
        drawSky(g2); drawGround(g2); drawEnemy(g2, t); drawWave(g2, t); drawHud(g2, t, paused);
        g2.dispose();
    }

    private static void quad(Graphics2D g, Color color, int... p) {
        Path2D path = new Path2D.Double(); path.moveTo(p[0], p[1]);
        for (int i = 2; i < p.length; i += 2) path.lineTo(p[i], p[i + 1]);
        path.closePath(); g.setColor(color); g.fill(path);
    }

    private static void cube(Graphics2D g, int x, int y, int w, int h, int d,
                             Color top, Color front, Color side) {
        quad(g, top, x,y, x+w,y, x+w+d,y-2, x+d,y-2);
        quad(g, front, x,y, x+w,y, x+w,y+h, x,y+h);
        quad(g, side, x+w,y, x+w+d,y-2, x+w+d,y+h-2, x+w,y+h);
    }

    private static void drawSky(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, new Color(23,63,89), 0, FLOOR, new Color(103,174,187)));
        g.fillRect(0, 0, W, FLOOR);
        g.setColor(new Color(183,232,232,45));
        for (int[] c : new int[][]{{95,112},{332,72},{820,142}}) {
            g.fillRect(c[0],c[1],62,12); g.fillRect(c[0]+15,c[1]-12,33,12); g.fillRect(c[0]+49,c[1],20,10);
        }
        g.setColor(new Color(162,213,212,55));
        for (int x=0;x<W;x+=24) g.fillRect(x,313+(x%3)*3,16,4+(x*17%5));
    }

    private static void drawGround(Graphics2D g) {
        g.setColor(new Color(26,58,70)); g.fillRect(0,FLOOR,W,H-FLOOR);
        for (int x=0;x<W;x+=18) {
            int h=5+(x*7%9);
            cube(g,x,FLOOR-h,18,h,3,new Color(81,129,125),new Color(52,91,91),new Color(37,70,77));
        }
        g.setColor(new Color(16,43,55)); g.fillRect(0,FLOOR+7,W,H-FLOOR-7);
        for (int x=0;x<W;x+=32) { g.setColor(x%3==0?new Color(32,74,80):new Color(23,60,71)); g.fillRect(x,FLOOR+13+x%4,18,3); }
    }

    private static void drawEnemy(Graphics2D g, double t) {
        double hit = smooth(clamp((t-2.65)/.45,0,1)) * (1-smooth(clamp((t-4.8)/.8,0,1)));
        int x=(int)(711+hit*82), y=FLOOR-2+(int)(Math.sin(t*5));
        cube(g,x-15,y-66,31,28,5,new Color(227,166,100),new Color(180,103,72),new Color(135,70,57));
        g.setColor(new Color(23,45,56)); g.fillRect(x-11,y-61,7,6); g.fillRect(x+2,y-61,7,6);
        cube(g,x-13,y-37,28,27,6,new Color(220,129,80),new Color(167,79,62),new Color(115,60,59));
        cube(g,x-25,y-34,12,22,4,new Color(207,118,80),new Color(152,75,66),new Color(109,57,64));
        cube(g,x+14,y-34,12,22,4,new Color(207,118,80),new Color(152,75,66),new Color(109,57,64));
        cube(g,x-12,y-10,10,10,4,new Color(133,151,160),new Color(83,107,120),new Color(60,83,96));
        cube(g,x+2,y-10,10,10,4,new Color(133,151,160),new Color(83,107,120),new Color(60,83,96));
    }

    private static double heightAt(double x, double front, double maxH, double tail) {
        if (x<tail || x>front+92) return 0;
        if (x<=front) return maxH*Math.pow(clamp((x-tail)/(front-tail),0,1),1.17);
        return maxH*(.94-.76*(x-front)/92.0);
    }

    private static void drawWave(Graphics2D g, double t) {
        double tail=116, front, maxH;
        if(t<.55){double q=smooth(t/.55);front=mix(170,258,q);maxH=mix(17,34,q);}
        else if(t<2.55){double q=smooth((t-.55)/2);front=mix(258,625,q);maxH=mix(34,254,q);}
        else if(t<3.12){double q=smooth((t-2.55)/.57);front=mix(625,665,q);maxH=mix(254,238,q);}
        else if(t<4.65){double q=smooth((t-3.12)/1.53);front=665+q*115;maxH=mix(238,23,q);}
        else {double q=smooth((t-4.65)/1.95);front=780+q*35;maxH=mix(23,5,q);}
        if(t>1.2&&t<4.2) maxH+=Math.sin(t*7)*3;
        for(int d=DEPTH-1;d>=0;d--) for(int x=(int)tail;x<Math.min(W,front+94);x+=CELL){
            double h=heightAt(x,front,maxH,tail); if(h<2)continue;
            int count=(int)Math.ceil(h/CELL), topRow=count-1;
            for(int row=0;row<count;row++){
                double above=row*CELL;
                double q=(x-(front-82))/126.0, low=maxH*(.50+.06*Math.sin(q*Math.PI));
                if(t>1.65&&t<4.1&&q>0&&q<1&&above>low&&above<low+maxH*.17)continue;
                int sx=x+d*3, sy=FLOOR-(int)above-CELL-d*2;
                boolean foam=row>=topRow-1;
                Color top=foam?new Color(232,255,255):d==DEPTH-1?new Color(100,229,231):(row>topRow*.7?new Color(25,187,208):new Color(17,125,158));
                Color frontC=foam?new Color(176,239,242):d==0?new Color(8,126,155):(row>topRow*.55?new Color(8,114,142):new Color(9,81,111));
                Color side=foam?new Color(91,197,211):d==0?new Color(6,70,95):new Color(10,101,123);
                cube(g,sx,sy,CELL-1,CELL,3,top,frontC,side);
            }
        }
        // Voxel foam cap; a stepped line makes the lip curl forward.
        double crestStart=Math.max(tail,front-156), crestEnd=front+42;
        for(double x=crestStart;x<crestEnd;x+=8){
            double h=heightAt(x,front,maxH,tail); if(h<10)continue;
            double u=(x-crestStart)/(crestEnd-crestStart);
            int y=(int)(FLOOR-h-5+(((int)x*13)%9-4)+(u>.72?(u-.72)*50:0));
            Color c=((int)x%5==0)?new Color(239,255,255):((int)x%3==0?new Color(169,248,245):new Color(101,220,226));
            cube(g,(int)x,y,8,7,2,c,new Color(65,184,204),new Color(38,141,168));
        }
    }

    private static void drawHud(Graphics2D g, double t, boolean paused) {
        g.setColor(new Color(4,21,33,205));g.fillRect(16,15,180,28);
        g.setColor(new Color(183,245,247));g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,12));g.drawString("ÉTUDE 01  /  IMPACT D'EAU",25,34);
        g.setColor(new Color(8,28,42,225));g.fillRect(0,H-42,W,42);
        g.setColor(new Color(38,96,120));g.drawLine(0,H-42,W,H-42);
        g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,11));
        g.setColor(new Color(18,57,75));g.fillRoundRect(12,H-34,82,24,6,6);
        g.setColor(new Color(117,231,237));g.drawRoundRect(12,H-34,82,24,6,6);
        g.setColor(new Color(223,251,255));g.drawString(paused?"▶  Lecture":"Ⅱ  Pause",27,H-18);
        g.setColor(new Color(18,57,75));g.fillRoundRect(104,H-34,92,24,6,6);
        g.setColor(new Color(117,231,237));g.drawRoundRect(104,H-34,92,24,6,6);
        g.setColor(new Color(223,251,255));g.drawString("Recommencer",115,H-18);
        g.setColor(new Color(223,251,255));g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,13));
        g.drawString((t<.55?"01 — Montée depuis le sol":t<1.75?"02 — La vague prend de la hauteur":t<2.55?"03 — La crête s'enroule":t<3.35?"04 — Impact de plein fouet":t<4.8?"05 — Retombée et étalement":"06 — L'eau s'apaise"),220,H-16);
        g.setColor(new Color(15,49,65));g.fillRect(0,H-4,W,4);g.setColor(new Color(83,230,235));g.fillRect(0,H-4,(int)(W*t/(LOOP_MS/1000.0)),4);
    }

    private static double clamp(double v,double a,double b){return Math.max(a,Math.min(b,v));}
    private static double mix(double a,double b,double t){return a+(b-a)*t;}
    private static double smooth(double t){t=clamp(t,0,1);return t*t*(3-2*t);}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f=new JFrame("Étude voxel — vague déferlante");
            f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            f.setContentPane(new VoxelTsunamiPreview());f.pack();f.setLocationRelativeTo(null);f.setVisible(true);
        });
    }
}
