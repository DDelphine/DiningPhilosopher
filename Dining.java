//
// Simple Java implementation of the classic Dining Philosophers problem.
//
// No synchronization (yet).
//
// Graphics are *very* naive.  Philosophers are big blobs.
// Forks are little blobs.
// 
// Written by Michael Scott, 1997; updated 2013 to use Swing.
// Updated again in 2019 to drop support for applets.
//

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;
import java.util.*;
import java.lang.*;
import java.lang.Thread.*;

// This code has six main classes:
//  Dining
//      The public, "main" class.
//  Philosopher
//      Active -- extends Thread
//  Fork
//      Passive
//  Table
//      Manages the philosophers and forks and their physical layout.
//  Coordinator
//      Provides mechanisms to suspend, resume, and reset the state of
//      worker threads (philosophers).
//  UI
//      Manages graphical layout and button presses.

public class Dining {
    public static boolean verbose;
   // public static Writer writer = new PrintWriter(System.out);
    private static final int CANVAS_SIZE = 360;
        // pixels in each direction;
        // needs to agree with size in dining.html
    
    public static void main(String[] args) {
	if (args.length != 0){  //checks if program should be run in verbose mode (print all state transitions)  
		if (args[0].equals("-v") || args[0].equals("-V")){
			verbose = true;
			System.out.println("Verbose output:");
		} else {
			verbose = false;
		}
	} else {
		verbose = false;
	}
	JFrame f = new JFrame("Dining");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        Dining me = new Dining();

        final Coordinator c = new Coordinator();
        final Table t = new Table(c, CANVAS_SIZE);
        // arrange to call graphical setup from GUI thread
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    new UI(f, c, t);
                }
            });
        } catch (Exception e) {
            System.err.println("unable to create GUI");
        }

        f.pack();            // calculate size of frame
        f.setVisible(true);
    }
}

class Fork {
    private Table t;
    private static final int XSIZE = 10;
    private static final int YSIZE = 10;
    private int orig_x;
    private int orig_y;
    private int x;
    private int y;
    private boolean in_use;
    private int fork_holder;

    // Constructor.
    // cx and cy indicate coordinates of center.
    // Note that fillOval method expects coordinates of upper left corner
    // of bounding box instead.
    //
    public Fork(Table T, int cx, int cy) {
        t = T;
        orig_x = cx;
        orig_y = cy;
        x = cx;
        y = cy;
        in_use = false; //the fork is unused
    }

    public void reset() {
        clear();
        x = orig_x;
        y = orig_y;
        t.repaint();
    }

    // arguments are coordinates of acquiring philosopher's center
    //
    public void acquire(int px, int py) {
        clear();
        if(in_use == false){
            in_use = true;
            x = (orig_x + px)/2;
            y = (orig_y + py)/2;
            t.repaint();
        }
    }

    public void release() {
        in_use = false;
        reset();
    }

    public boolean get_state(){
        return in_use;
    }

    public void set_fork_holder(int i){
        fork_holder = i;
    }

    public int get_fork_holder(){
        return fork_holder;
    }

    // render self
    //
    public void draw(Graphics g) {
        g.setColor(Color.black);
        g.fillOval(x-XSIZE/2, y-YSIZE/2, XSIZE, YSIZE);
    }
    // erase self
    //
    private void clear() {
        Graphics g = t.getGraphics();
        g.setColor(t.getBackground());
        g.fillOval(x-XSIZE/2, y-YSIZE/2, XSIZE, YSIZE);
    }
}

class Philosopher extends Thread {
    private static final Color THINK_COLOR = Color.blue;
    private static final Color WAIT_COLOR = Color.red;
    private static final Color EAT_COLOR = Color.green;
    private static final double THINK_TIME = 4.0;
    private static final double FUMBLE_TIME = 2.0;
        // time between becoming hungry and grabbing first fork
    private static final double EAT_TIME = 3.0;
    public Writer writer = new BufferedWriter(new OutputStreamWriter(System.out));
    private Coordinator c;
    private Table t;
    private static final int XSIZE = 50;
    private static final int YSIZE = 50;
    private int x;
    private int y;
    private Fork left_fork;
    private Fork right_fork;
    private int index;
    private Random prn;
    private Color color;
    public int counter;
    public boolean flag;

    // Constructor.
    // cx and cy indicate coordinates of center
    // Note that fillOval method expects coordinates of upper left corner
    // of bounding box instead.
    //
    public Philosopher(Table T, int cx, int cy,
                       Fork lf, Fork rf, Coordinator C) {
        t = T;
        x = cx;
        y = cy;
        left_fork = lf;
        right_fork = rf;
        c = C;
	counter = 0;
	flag = true;
        prn = new Random();
        color = THINK_COLOR;
    }

    public void set_index(int i){
        index = i;
    }

    public int get_index(){
        return index;
    }
    // start method of Thread calls run; you don't
    //
    public void run() {
        for (;;) {
            try {
	    synchronized(writer){//synchronizes writer to prevent multiple threads from writing simultaneously
                if (c.gate()) delay(EAT_TIME/2.0);
                think();
                if (c.gate()) delay((THINK_TIME+counter)/2.0);
                hunger();
                if (c.gate()) delay(FUMBLE_TIME/2.0);
  		eat();
	    }
            } catch(ResetException e) { 
                color = THINK_COLOR;
                t.repaint();
            }
        }
    }

    // render self
    //
    public void draw(Graphics g) {
        g.setColor(color);
        g.fillOval(x-XSIZE/2, y-YSIZE/2, XSIZE, YSIZE);
    }

    // sleep for secs +- FUDGE (%) seconds
    //
    private static final double FUDGE = 0.2;
    private void delay(double secs) throws ResetException {
        double ms = 1000 * secs;
        int window = (int) (2.0 * ms * FUDGE);
        int add_in = prn.nextInt() % window;
        int original_duration = (int) ((1.0-FUDGE) * ms + add_in);
        int duration = original_duration;
        for (;;) {
            try {
                Thread.sleep(duration);
                return;
            } catch(InterruptedException e) {
                if (c.isReset()) {
                    throw new ResetException();
                } else {        // suspended
                    c.gate();   // wait until resumed
                    duration = original_duration / 2;
                    // don't wake up instantly; sleep for about half
                    // as long as originally instructed
                }
            }
        }
    }

    private void think() throws ResetException {
	if(Dining.verbose){
		try{
                              writer.write("Philosopher "+ this.get_index() + " thinking\n");
                              writer.flush(); 
             } catch (IOException e){
                 }
	 }
        color = THINK_COLOR;
        t.repaint();
        delay(THINK_TIME);
    }

    private void hunger() throws ResetException {
	 if(Dining.verbose){//checks if it needs to print state change 
                try{    
                              writer.write("Philosopher "+ this.get_index() + " waiting\n");
                              writer.flush();
             } catch (IOException e){
                 }
         }
        color = WAIT_COLOR;
        t.repaint();
        delay(FUMBLE_TIME);
        /*
        * only one philosopher can take the shared fork, and the 
        * synchronized(left_fork) must be outside of the if(left_fork.get_state() == false)
        * because if we put if(left_fork.get_state() == false) outside of synchronized(left_fork), 
        * there exists such a case that thread 1 finds the left fork is not in use, then scheduler
        * switches to thread 2, and thread 2 also finds that left fork is not in use. then thread 2
        * is swapped by thread 1, and thread 1 acquires the fork, while after that, thread 2 is swithed
        * in, and thread 2 still thinks the left fork is not in use, so it tries to acquire the fork.
        */
        /*
        * the even philosopher acquires left fork firstly, and odd philosopher acquires right fork firstly
        * this method solves the dead-lock problem
        */
        if(this.get_index() % 2 == 0){
            synchronized(left_fork){
                //if the left fork is not in use, then the current thread(philosopher) can take it
                if(left_fork.get_state() == false){
                    left_fork.acquire(x, y);
                    left_fork.set_fork_holder(this.get_index());
                    yield();
                    //if the right fork is not in use, then the current thread(philosopher) can take it
                    synchronized(right_fork){
                        if(right_fork.get_state() == false){
                            right_fork.acquire(x, y);
                            right_fork.set_fork_holder(this.get_index());
                        }
                        else{
                            left_fork.release();
                        }
                    }
                }
            }
        }
        else{
            synchronized(right_fork){
                //if the left fork is not in use, then the current thread(philosopher) can take it
                if(right_fork.get_state() == false){
                    right_fork.acquire(x, y);
                    right_fork.set_fork_holder(this.get_index());
                    yield();
                    //if the right fork is not in use, then the current thread(philosopher) can take it
                    synchronized(left_fork){
                        if(left_fork.get_state() == false){
                            left_fork.acquire(x, y);
                            left_fork.set_fork_holder(this.get_index());
                        }
                        else{
                            right_fork.release();
                        }
                    }
                }
            }
        }
        //left_fork.acquire(x, y);
        //yield();    // you aren't allowed to remove this
        //right_fork.acquire(x, y);
    }

    private void eat() throws ResetException {

        //eat when both of the forks are available, and the forks are held by current philosopher
        if(left_fork.get_state() && right_fork.get_state() && 
           left_fork.get_fork_holder() == this.get_index() && right_fork.get_fork_holder() == this.get_index()){
             if(Dining.verbose){//checks if it needs to write state change
                	try{    
                              writer.write("Philosopher "+ this.get_index() + " eating\n");
                              writer.flush();
             	} catch (IOException e){
                }
            }
            if(this.flag)	
	      this.counter++;
	    else
	       this.counter--;
	    if(this.counter == 5 && this.flag)
	       this.flag = false;
	    if(this.counter == 0 && !this.flag)
	       this.flag = true;    
	    color = EAT_COLOR;
            t.repaint();
            delay(EAT_TIME);
            left_fork.release();
            yield();    // you aren't allowed to remove this
            right_fork.release();
        }
    }
}

// Graphics panel in which philosophers and forks appear.
//
class Table extends JPanel {
    private static final int NUM_PHILS = 5;

    // following fields are set by construcctor:
    private final Coordinator c;
    private Fork[] forks;
    private Philosopher[] philosophers;

    public void pause() {
        c.pause();
        // force philosophers to notice change in coordinator state:
        for (int i = 0; i < NUM_PHILS; i++) {
            philosophers[i].interrupt();
        }
    }

    // Called by the UI when it wants to start over.
    //
    public void reset() {
        c.reset();
        // force philosophers to notice change in coordinator state:
        for (int i = 0; i < NUM_PHILS; i++) {
            philosophers[i].interrupt();
        }
        for (int i = 0; i < NUM_PHILS; i++) {
            forks[i].reset();
        }
    }

    // The following method is called automatically by the graphics
    // system when it thinks the Table canvas needs to be re-displayed.
    // This can happen because code elsewhere in this program called
    // repaint(), or because of hiding/revealing or open/close
    // operations in the surrounding window system.
    //
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        for (int i = 0; i < NUM_PHILS; i++) {
            forks[i].draw(g);
            philosophers[i].draw(g);
        }
        g.setColor(Color.black);
        g.drawRect(0, 0, getWidth()-1, getHeight()-1);
    }

    // Constructor
    //
    // Note that angles are measured in radians, not degrees.
    // The origin is the upper left corner of the frame.
    //
    public Table(Coordinator C, int CANVAS_SIZE) {    // constructor
        c = C;
        forks = new Fork[NUM_PHILS];
        philosophers = new Philosopher[NUM_PHILS];
        setPreferredSize(new Dimension(CANVAS_SIZE, CANVAS_SIZE));

        for (int i = 0; i < NUM_PHILS; i++) {
            double angle = Math.PI/2 + 2*Math.PI/NUM_PHILS*(i-0.5);
            forks[i] = new Fork(this,
                (int) (CANVAS_SIZE/2.0 + CANVAS_SIZE/6.0 * Math.cos(angle)),
                (int) (CANVAS_SIZE/2.0 - CANVAS_SIZE/6.0 * Math.sin(angle)));
        }
        for (int i = 0; i < NUM_PHILS; i++) {
            double angle = Math.PI/2 + 2*Math.PI/NUM_PHILS*i;
            philosophers[i] = new Philosopher(this,
            (int) (CANVAS_SIZE/2.0 + CANVAS_SIZE/3.0 * Math.cos(angle)),
            (int) (CANVAS_SIZE/2.0 - CANVAS_SIZE/3.0 * Math.sin(angle)),
            forks[i],
            forks[(i+1) % NUM_PHILS],
            c);
            philosophers[i].set_index(i+1);
            philosophers[i].start();
        }
    }
}

class ResetException extends Exception { };

// The Coordinator serves to slow down execution, so that behavior is
// visible on the screen, and to notify all running threads when the user
// wants them to reset.
//
class Coordinator {
    public enum State { PAUSED, RUNNING, RESET }
    private State state = State.PAUSED;

    public synchronized boolean isPaused() {
        return (state == State.PAUSED);
    }

    public synchronized void pause() {
        state = State.PAUSED;
    }

    public synchronized boolean isReset() {
        return (state == State.RESET);
    }

    public synchronized void reset() {
        state = State.RESET;
    }

    public synchronized void resume() {
        state = State.RUNNING;
        notifyAll();        // wake up all waiting threads
    }

    // Return true if we were forced to wait because the coordinator was
    // paused or reset.
    //
    public synchronized boolean gate() throws ResetException {
        if (state == State.PAUSED || state == State.RESET) {
            try {
                wait();
            } catch(InterruptedException e) {
                if (isReset()) {
                    throw new ResetException();
                }
            }
            return true;        // waited
        }
        return false;           // didn't wait
    }
}

// Class UI is the user interface.  It displays a Table canvas above
// a row of buttons.  Actions (event handlers) are defined for each of
// the buttons.  Depending on the state of the UI, either the "run" or
// the "pause" button is the default (highlighted in most window
// systems); it will often self-push if you hit carriage return.
//
class UI extends JPanel {
    private final Coordinator c;
    private final Table t;

    private final JRootPane root;
    private static final int externalBorder = 6;

    private static final int stopped = 0;
    private static final int running = 1;
    private static final int paused = 2;

    private int state = stopped;

    // Constructor
    //
    public UI(RootPaneContainer pane, Coordinator C, Table T) {
        final UI u = this;
        c = C;
        t = T;

        final JPanel b = new JPanel();   // button panel

        final JButton runButton = new JButton("Run");
        final JButton pauseButton = new JButton("Pause");
        final JButton resetButton = new JButton("Reset");
        final JButton quitButton = new JButton("Quit");

        runButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                c.resume();
                root.setDefaultButton(pauseButton);
            }
        });
        pauseButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                t.pause();
                root.setDefaultButton(runButton);
            }
        });
        resetButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                t.reset();
                root.setDefaultButton(runButton);
            }
        });
        quitButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                System.exit(0);
            }
        });

        // put the buttons into the button panel:
        b.setLayout(new FlowLayout());
        b.add(runButton);
        b.add(pauseButton);
        b.add(resetButton);
        b.add(quitButton);

        // put the Table canvas and the button panel into the UI:
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(
            externalBorder, externalBorder, externalBorder, externalBorder));
        add(t);
        add(b);

        // put the UI into the Frame
        pane.getContentPane().add(this);
        root = getRootPane();
        root.setDefaultButton(runButton);
    }
}
