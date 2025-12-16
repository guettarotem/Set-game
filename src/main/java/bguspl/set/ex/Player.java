package bguspl.set.ex;

import bguspl.set.Env;

import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    public static final int nullValue = -1;
    public static final int tokenSize = 3;


    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Dealer dealer;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    public int slotPressed;


    public long endFreezeTime;

    public int[] tokens;
    //private Queue<Integer> actions;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    protected final Object playerLock;

    /**
     * The current score of the player.
     */
    private int score;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.dealer = dealer;
        this.table = table;
        this.id = id;
        this.human = human;
        this.playerLock = new Object();
        this.score = 0;
        this.slotPressed = nullValue;
        this.tokens = new int[tokenSize];
        this.tokens[0] = nullValue;
        this.tokens[1] = nullValue;
        this.tokens[2] = nullValue;

    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();
        while (!terminate) {
            synchronized(this){
                while (slotPressed == nullValue) {
                    try {
                        this.wait();

                    } catch (InterruptedException ignored) {}
                    try{
                        if (System.currentTimeMillis() < endFreezeTime || table.slotToCard[slotPressed] == null) {
                            slotPressed = nullValue;
                            this.notifyAll();
                        }
                    } catch (ArrayIndexOutOfBoundsException ignored){}
                }
                boolean flag = false;
                    for (int i = 0; i < tokenSize; i++) {
                        if (tokens[i] == slotPressed) {
                            tokens[i] = nullValue;
                            this.table.removeToken(id, slotPressed);
                            flag = true;
                        }
                    }
                    if (!flag) {
                        for (int i = 0; i < tokenSize; i++) {
                            if (tokens[i] == nullValue) {
                                tokens[i] = slotPressed;
                                this.table.placeToken(id, slotPressed);
                                break;
                            }
                        }
                        if (tokens[1] != nullValue && tokens[2] != nullValue && tokens[0] != nullValue) {
                            table.playerUpdateQ.add(id);
                            synchronized (dealer.DealerLock){
                                dealer.DealerLock.notify();
                            }
                        }
                    }
                    slotPressed = nullValue;
                    this.notifyAll();
            }
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            Random random = new Random();
            while (!terminate) {
                int randomSlot = random.nextInt(env.config.tableSize);
                if (table.slotToCard[randomSlot] != null){
                    this.keyPressed(randomSlot);
                }
                try {
                        Thread.sleep(2);
                } catch (InterruptedException ignored) {}

            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
        if (!human)
            aiThread.interrupt();
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        synchronized(this)
        {
            try {
            while (slotPressed != nullValue) {
                this.wait();
            }
            if(table.slotToCard[slot] != null) {
                slotPressed = slot;
            }
            this.notifyAll();
            } catch (InterruptedException ignored) {
            }
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
        endFreezeTime = System.currentTimeMillis()+env.config.pointFreezeMillis;
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public synchronized void penalty() {
        endFreezeTime = System.currentTimeMillis()+env.config.penaltyFreezeMillis;
        for(int token=0 ; token<tokenSize;token++){
            if(tokens[token]!=nullValue) {
                table.removeToken(id, tokens[token]);
            }
            tokens[token] = nullValue;
        }
    }

    public int score() {
        return score;
    }

    public synchronized void clearTokens(){
        for(int i=0;i<tokenSize;i++){
            if(tokens[i] != nullValue) {
                table.removeToken(id, tokens[i]);
                tokens[i] = nullValue;
            }
        }
    }

    public synchronized void removeToken(int[] slots){
        for (int i = 0; i < 3; i++) {
            if (tokens[i] == slots[0] || tokens[i] == slots[1] || tokens[i] == slots[2]) {
                table.removeToken(id, tokens[i]);
                tokens[i] = -1;
                table.removePlayer(id);
            }
        }
    }
}
