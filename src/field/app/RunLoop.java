package field.app;

import field.graphics.Scene;
import fieldbox.execution.Errors;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by marc on 3/25/14.
 */
public class RunLoop {

	static public final RunLoop main = new RunLoop();
	static public final ReentrantLock lock = new ReentrantLock(true);
	static public final ExecutorService workerPool = Executors.newFixedThreadPool(Runtime.getRuntime()
											     .availableProcessors() + 2);
	static public long tick = 0;
	protected final Thread shutdownThread;
	public Scene mainLoop = new Scene();
	public Set<Object> shouldSleep = Collections.synchronizedSet(new LinkedHashSet<>());
	Thread mainThread = null;
	List<Runnable> onExit = new LinkedList<>();
	AtomicBoolean exitStarted = new AtomicBoolean(false);

	protected RunLoop() {
		Runtime.getRuntime()
		       .addShutdownHook(shutdownThread = new Thread(() -> exit()));
	}

	public Scene getLoop() {
		return mainLoop;
	}

	public boolean isMainThread() {
		return Thread.currentThread() == mainThread;
	}

	long getLock;
	long hasLock;
	long service;
	long mainloop;
	long locksMissed;
	long sleepsTaken;

	long interval = 100;
	long intervalIn = 0;

	static public boolean printTelemetry = false;

	public void enterMainLoop() {
		mainThread = Thread.currentThread();

		while (true) {
			try {
				tick++;

				long a = System.nanoTime();
				if (lock.tryLock(1, TimeUnit.DAYS)) {
					long b = System.nanoTime();
					mainLoop.updateAll();
					long c = System.nanoTime();
					ThreadSync.get()
						  .serviceAndCull();
					long d = System.nanoTime();

					getLock += b-a;
					hasLock += d-b;
					service += d-c;
					mainloop += c-b;
				} else {
					locksMissed++;
				}
				if (shouldSleep.size() == 0)
				{
					Thread.sleep(2);
					sleepsTaken++;
				}

				if (tick%interval==0)
				{

					if (printTelemetry) {
						System.out.println(
							    " a" + (getLock / (double) interval) + " b" + (hasLock / (double) interval) + " c" + (service / (double) interval) + " d" + (mainloop / (double) interval) + " m" + locksMissed + " s" + sleepsTaken);
						System.out.println(" f" + (System.nanoTime() - intervalIn) / interval);
					}
						getLock = 0;
						hasLock = 0;
						service = 0;
						mainloop = 0;
						locksMissed = 0;
						sleepsTaken = 0;
						intervalIn = System.nanoTime();
				}

			} catch (Throwable t) {
				System.err.println(" exception thrown in main loop");
				t.printStackTrace();
			} finally {
				RunLoop.lock.unlock();
			}

		}
	}

	public void once(Runnable r) {
		mainLoop.attach(i -> {
			try {
				r.run();
			}
			catch(Throwable t)
			{
				t.printStackTrace();
			}
			finally {
				return false;
			}

		});
	}

	public void nTimes(Runnable p0, int n) {
		mainLoop.attach(new Scene.Perform() {
			int t = 0;

			@Override
			public boolean perform(int pass) {
				p0.run();
				return t++ < n;
			}

			Errors.ErrorConsumer ec = Errors.errors.get();

			@Override
			public void setErrorConsumer(Errors.ErrorConsumer c) {
				this.ec = ec;
			}

			@Override
			public Errors.ErrorConsumer getErrorConsumer() {
				if (p0 instanceof Errors.ErrorConsumer)
					return ((Errors.ErrorConsumer)p0);
				if (p0 instanceof Errors.SavesErrorConsumer)
					return ((Errors.SavesErrorConsumer)p0).getErrorConsumer();
				return ec;
			}
		});
	}

	public void delay(Runnable p0, int ms) {
		long now = System.currentTimeMillis();

		mainLoop.attach(new Scene.Perform() {
			int t = 0;

			@Override
			public boolean perform(int pass) {
				if (System.currentTimeMillis() - now > ms) {
					p0.run();
					return false;
				}
				return true;
			}

			Errors.ErrorConsumer ec = Errors.errors.get();

			@Override
			public void setErrorConsumer(Errors.ErrorConsumer c) {
				this.ec = ec;
			}

			@Override
			public Errors.ErrorConsumer getErrorConsumer() {
				if (p0 instanceof Errors.ErrorConsumer)
					return ((Errors.ErrorConsumer)p0);
				if (p0 instanceof Errors.SavesErrorConsumer)
					return ((Errors.SavesErrorConsumer)p0).getErrorConsumer();
				return ec;
			}
		});
	}

	public void delayTicks(Runnable p0, int ticks) {

		mainLoop.attach(new Scene.Perform() {
			int t = 0;

			@Override
			public boolean perform(int pass) {
				if (t++ > ticks) {
					p0.run();
					return false;
				}
				return true;
			}

			Errors.ErrorConsumer ec = Errors.errors.get();

			@Override
			public void setErrorConsumer(Errors.ErrorConsumer c) {
				this.ec = ec;
			}

			@Override
			public Errors.ErrorConsumer getErrorConsumer() {
				if (p0 instanceof Errors.ErrorConsumer)
					return ((Errors.ErrorConsumer)p0);
				if (p0 instanceof Errors.SavesErrorConsumer)
					return ((Errors.SavesErrorConsumer)p0).getErrorConsumer();
				return ec;
			}
		});
	}

	public void exit() {
		try {
			if (exitStarted.compareAndSet(false, true)) {
				for (Runnable r : onExit) {
					try {
						r.run();
					} catch (Throwable t) {
						System.err.println(" exception thrown during exit (will continue on regardless)");
						t.printStackTrace();
					}
				}
				if (Thread.currentThread() != shutdownThread) System.exit(0);
			}
		} catch (Throwable t) {
			System.err.println(" unexpected exception thrown during exit ");
			t.printStackTrace();
		}
	}

	/**
	 * adds a Runnable to be executed on exit. This will run before anything else that's been added.
	 */
	public void onExit(Runnable r) {
		// we add this to the start of the list, it will be run before anything that's already there.
		onExit.add(0, r);
	}


}
