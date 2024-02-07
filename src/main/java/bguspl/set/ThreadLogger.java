<<<<<<< HEAD
package bguspl.set;

import java.util.logging.Logger;

public class ThreadLogger extends Thread {

    final Logger logger;

    public ThreadLogger(Runnable target, String name, Logger logger) {
        super(target, name);
        this.logger = logger;
    }

    public void startWithLog() {
        logStart(logger, getName());
        super.start();
    }

    public void joinWithLog() throws InterruptedException {
        try {
            join();
        } finally {
            logStop(logger, getName());
        }
    }

    public static void logStart(Logger logger, String name) {
        logger.info("thread " + name + " starting.");
    }

    public static void logStop(Logger logger, String name) {
        logger.info("thread " + name + " terminated.");
    }
}
=======
package bguspl.set;

import java.util.logging.Logger;

public class ThreadLogger extends Thread {

    final Logger logger;

    public ThreadLogger(Runnable target, String name, Logger logger) {
        super(target, name);
        this.logger = logger;
    }

    public void startWithLog() {
        logStart(logger, getName());
        super.start();
    }

    public void joinWithLog() throws InterruptedException {
        try {
            join();
        } finally {
            logStop(logger, getName());
        }
    }

    public static void logStart(Logger logger, String name) {
        logger.info("thread " + name + " starting.");
    }

    public static void logStop(Logger logger, String name) {
        logger.info("thread " + name + " terminated.");
    }
}
>>>>>>> edfb15b469c60d5f3f02af3cd4a1ced60e208dd3
