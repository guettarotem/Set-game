package bguspl.set.ex;

import bguspl.set.Env;

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
    //public int[] set;

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

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        while (!shouldFinish()) {
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
        terminate = true;
        // TODO implement
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        
        if(table.playerUpdate != -1)
        {
            synchronized(table){
            int card1 = table.slotToCard[players[table.playerUpdate].tokens[0]];
            int card2 = table.slotToCard[players[table.playerUpdate].tokens[1]];
            int card3 = table.slotToCard[players[table.playerUpdate].tokens[2]];
            if(!Check(card1,card2,card3))
            {
                players[table.playerUpdate].penalty();
            }
            else{
                players[table.playerUpdate].point();
                for (int i =0;i<players.length;i++)
                {
                    for(int j=0;j<3;j++)
                    {
                        if(players[i].tokens[j] == table.cardToSlot[card1]||players[i].tokens[j] == table.cardToSlot[card2]||players[i].tokens[j] == table.cardToSlot[card3])
                        {
                            players[i].tokens[j] = -1;
                            table.removeToken(i,players[i].tokens[j]);
                        }
                        
                    }
                }
                table.removeCard(table.cardToSlot[card1]);
                table.removeCard(table.cardToSlot[card2]);
                table.removeCard(table.cardToSlot[card3]);
            }
            table.playerUpdate = -1;
            notifyAll();
        }
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        // TODO sync??
        if(!deck.isEmpty())
        {
            for(i = 0; i < table.slotToCard.length;i++)
            {
                if(table.slotToCard[i] == null)
                {
                    int card = deck.getFirst();
                    deck.removeFirst();
                    table.placeCard(card, i);
                }
            }
        }
        // TODO test
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        synchronized(table)
        {
            wait(1000);
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO SYNC???
        env.ui.setCountdown(env.config.turnTimeoutMillis, reset);
        // TODO test
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // TODO sync
        for(int i = 0;i < env.config.tableSize;i++)
        {
            table.removeCard(i);
        }
        // TODO test
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int maxScore = 0,winers = 0,j = 0;
        for (int i = 0;i<players.length;i++){
            if(players[i].score()>maxScore)
            {
                maxScore = players[i].score();
                winers = 1;
            }
            else if(players[i].score() == maxScore)winers++;
        }
        int[] winerId = new int[winers];
        for(int i = 0;i<players.length;i++)
        {
            if(players[i].score() == maxScore)
            {
                winerId[j++] = players[i].id;
            }
        }
        env.ui.announceWinner(winerId);
        try {
            Thread.sleep(env.config.endGamePauseMillies);
        } catch (InterruptedException ignored) {}
        // TODO test
    }

    private boolean Check(int card1, int card2, int card3){
        while (card1>0 || card2 > 0 || card3 >0) {
            if(!((card1%3 == card2%3 && card2%3 == card3%3)||(card1%3 != card2%3 && card2%3 != card3%3&& card1%3 != card3%3))){
                return false;
            }
            card1 = card1 /3;
            card2 = card2 /3;
            card3 = card3 /3;
        }
        return true;
    }
    
}

