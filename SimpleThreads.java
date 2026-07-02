public class SimpleThreads {

    // Display a message, preceded by the name of the current thread
    static void threadMessage(String message) {
        String threadName = Thread.currentThread().getName();
        System.out.format("%s: %s%n", threadName, message);
    }

    private static class MessageLoop
        implements Runnable {
        public void run() {
            String importantInfo[] = {
                "Mares eat oats",
                "Does eat oats",
                "Little lambs eat ivy",
                "A kid will eat ivy too"
            };
            try {
                for (int i = 0; i < importantInfo.length; i++) {
                    // Pause for 4 seconds
                    Thread.sleep(4000);
                    // Print a message
                    threadMessage(importantInfo[i]);
                }
            } catch (InterruptedException e) {
                threadMessage("I wasn't done!");
            }
        }
    }

    // A CPU-intensive task that keeps searching for prime numbers.
    // Because it never blocks (no Thread.sleep / I/O), an interrupt does NOT
    // raise an InterruptedException on its own. To honour a cancellation
    // request we must poll Thread.currentThread().isInterrupted() inside the
    // computation loop and stop cooperatively when it becomes true.
    private static class PrimeSearchLoop
        implements Runnable {
        public void run() {
            long candidate = 2;
            long primesFound = 0;

            // Loop "forever" -- it only ends when we are interrupted
            while (!Thread.currentThread().isInterrupted()) {
                if (isPrime(candidate)) {
                    primesFound++;
                    // Report progress once in a while so the output stays readable
                    if (primesFound % 50000 == 0) {
                        threadMessage("Found " + primesFound
                                + " primes so far (last: " + candidate + ")");
                    }
                }
                candidate++;
            }

            // We reach this point only because someone called interrupt()
            threadMessage("Interrupted! I stopped after finding "
                    + primesFound + " primes.");
        }

        // Trial-division primality test -- deliberately CPU-heavy
        private boolean isPrime(long n) {
            if (n < 2) {
                return false;
            }
            for (long i = 2; i * i <= n; i++) {
                // Bail out promptly if we were asked to stop mid-check
                if (Thread.currentThread().isInterrupted()) {
                    return false;
                }
                if (n % i == 0) {
                    return false;
                }
            }
            return true;
        }
    }

    public static void main(String args[])
        throws InterruptedException {

        // Delay, in milliseconds before we interrupt MessageLoop thread (default one hour)
        long patience = 1000 * 60 * 60;

        // If command line argument present, gives patience in seconds
        if (args.length > 0) {
            try {
                patience = Long.parseLong(args[0]) * 1000;
            } catch (NumberFormatException e) {
                System.err.println("Argument must be an integer.");
                System.exit(1);
            }
        }

        threadMessage("Starting MessageLoop thread");
        long startTime = System.currentTimeMillis();
        Thread t = new Thread(new MessageLoop());

	// Put the MessageLoop thread to run
        t.start();

        threadMessage("Waiting for MessageLoop thread to finish");
	
        // loop until MessageLoop thread exits
        while (t.isAlive()) {
            threadMessage("Still waiting...");
            // Wait maximum of 1 second for MessageLoop thread to finish
            t.join(1000);
            if (((System.currentTimeMillis() - startTime) > patience) && t.isAlive()) {
                threadMessage("Tired of waiting!");
		// Force the interruption of the MainLoop thread
                t.interrupt();
                // ...and wait for it to finish -- shouldn't be long now
                t.join();
            }
        }
        threadMessage("Finally!");

        // ------------------------------------------------------------------
        // Second part: a CPU-intensive thread that is cancelled once it
        // exceeds a time limit. Unlike MessageLoop, this task never sleeps,
        // so interruption only works because the task itself polls
        // isInterrupted() while it computes.
        // ------------------------------------------------------------------

        // Maximum time we let the prime search run, in milliseconds (default 5 s)
        long cpuTimeLimit = 5 * 1000;
        if (args.length > 1) {
            try {
                cpuTimeLimit = Long.parseLong(args[1]) * 1000;
            } catch (NumberFormatException e) {
                System.err.println("Second argument must be an integer.");
                System.exit(1);
            }
        }

        threadMessage("Starting PrimeSearch (CPU-intensive) thread");
        long cpuStartTime = System.currentTimeMillis();
        Thread cpu = new Thread(new PrimeSearchLoop());
        cpu.start();

        // Watch the CPU thread and interrupt it once the limit is exceeded
        while (cpu.isAlive()) {
            // Wait up to 1 second for the CPU thread to finish on its own
            cpu.join(1000);
            if (((System.currentTimeMillis() - cpuStartTime) > cpuTimeLimit)
                    && cpu.isAlive()) {
                threadMessage("Time limit exceeded -- cancelling PrimeSearch!");
                // Request cancellation; the task checks isInterrupted() and stops
                cpu.interrupt();
                // Wait for it to wind down gracefully
                cpu.join();
            }
        }
        threadMessage("PrimeSearch thread finished. Done!");
    }
}
