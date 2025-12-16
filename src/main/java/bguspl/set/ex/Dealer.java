package bguspl.set.ex;

import bguspl.set.Env;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;
    private int timer;
    //public int playerUpdate; // A
    public final Object DealerLock = new Object(); // B

    private final List<Integer> shuffeledSlots;


    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        shuffeledSlots = IntStream.range(0, env.config.tableSize).boxed().collect(Collectors.toList());
        terminate = false;
        Collections.shuffle(deck);
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        for (Player player : players) {
            Thread playerThread = new Thread(player);
            playerThread.start();
        }
        while (!shouldFinish()) {
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        for (Player player : players)
            player.terminate();
        env.ui.dispose();
        terminate = true;
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        if (terminate || env.util.findSets(deck, 1).isEmpty()) {
            return true;
        }
        return false;

    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        while (!table.playerUpdateQ.isEmpty())  // A
        {
            Integer player = table.playerUpdateQ.remove(0);
            if (player == null) {
                continue;
            }
            synchronized (players[player].playerLock) {
                int[] cards = new int[3];
                if (players[player].tokens[0] == -1 || players[player].tokens[1] == -1 || players[player].tokens[2] == -1) {
                    continue;
                }
                if (table.slotToCard[players[player].tokens[0]] == null || table.slotToCard[players[player].tokens[1]] == null || table.slotToCard[players[player].tokens[2]] == null) {
                    continue;
                } else {
                    for (int i = 0; i < cards.length; i++) {
                        cards[i] = table.slotToCard[players[player].tokens[i]];
                    }
                }
                if (!env.util.testSet(cards)) {
                    players[player].penalty();
                } else {
                    players[player].point();
                    int[] slots = new int[3];
                    for (int i = 0; i < slots.length; i++) {
                        slots[i] = table.cardToSlot[cards[i]];
                    }
                    for (Player value : players) {
                        value.removeToken(slots);
                    }
                    for (int slot : slots) {
                        env.ui.removeTokens(slot);
                        table.removeCard(slot);
                    }
                }
                players[player].playerLock.notify();
            }
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        if (!deck.isEmpty()) {
            Collections.shuffle(shuffeledSlots);
            for (int i = 0; i < env.config.tableSize; i++) {
                if (deck.isEmpty()) {
                    break;
                }
                int slot = shuffeledSlots.remove(0);
                if (table.slotToCard[slot] == null) {
                    int card = deck.remove(0);
                    table.placeCard(card, slot);
                    updateTimerDisplay(true);
                }
                shuffeledSlots.add(slot);
            }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        try {
            synchronized (DealerLock) {
                DealerLock.wait(900);
            }
        } catch (InterruptedException ignored) {
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) { // ADD
        if (!terminate && reset) {
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), false);
        } else {
            boolean warm = (reshuffleTime - System.currentTimeMillis()) <= 5100;
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), warm);
        }

    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        env.ui.setCountdown(env.config.turnTimeoutMillis, false);
        Collections.shuffle(shuffeledSlots);
        for (Player p : players) {
            p.clearTokens();
        }
        for (int i = 0; i < env.config.tableSize; i++) {
            int slot = shuffeledSlots.remove(0);
            if (table.slotToCard[slot] != null) {
                deck.add(table.slotToCard[slot]);
                table.removeCard(slot);
            }
            shuffeledSlots.add(slot);
        }
        Collections.shuffle(deck);
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int maxScore = 0, winers = 0, j = 0;
        for (Player player : players) {
            if (player.score() > maxScore) {
                maxScore = player.score();
                winers = 1;
            } else if (player.score() == maxScore) winers++;
        }
        int[] winnerId = new int[winers];
        for (Player player : players) {
            if (player.score() == maxScore) {
                winnerId[j++] = player.id;
            }
        }
        env.ui.announceWinner(winnerId);
        try {
            Thread.sleep(env.config.endGamePauseMillies);
        } catch (InterruptedException ignored) {
        }
    }
}
