package io.github.chainsync.spring;

import io.github.chainsync.engine.ChainSynchronizer;
import org.springframework.context.SmartLifecycle;

/**
 * Bridges {@link ChainSynchronizer} into the Spring application lifecycle so synchronization starts
 * once the context is fully initialized and stops cleanly on shutdown.
 */
public class ChainsyncLifecycle implements SmartLifecycle {

    private final ChainSynchronizer synchronizer;

    public ChainsyncLifecycle(ChainSynchronizer synchronizer) {
        this.synchronizer = synchronizer;
    }

    @Override
    public void start() {
        synchronizer.start();
    }

    @Override
    public void stop() {
        synchronizer.stop();
    }

    @Override
    public boolean isRunning() {
        return synchronizer.isStarted();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}
