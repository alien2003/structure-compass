package dev.alien2003.structurecompass.server;

import dev.alien2003.structurecompass.StructureCompass;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One background search: runs on the locator worker thread, publishes one result per structure type into a
 * lock-free queue that the server thread drains on its tick.
 */
final class ScanJob implements Runnable {
    record TypeLocated(Holder<Structure> type, List<StructureLocator.Located> located) {
    }

    final StructureLocator locator;
    final int scanId;
    final int centerX;
    final int centerZ;
    final int radius;
    final int perType;
    final List<? extends Holder<Structure>> types;
    final ConcurrentLinkedQueue<TypeLocated> output = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private volatile boolean finished;
    private volatile long elapsedMillis;

    ScanJob(StructureLocator locator, int scanId, int centerX, int centerZ, int radius, int perType,
            List<? extends Holder<Structure>> types) {
        this.locator = locator;
        this.scanId = scanId;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = radius;
        this.perType = perType;
        this.types = types;
    }

    @Override
    public void run() {
        long start = System.nanoTime();
        try {
            for (Holder<Structure> type : types) {
                if (cancelled.get()) {
                    return;
                }
                List<StructureLocator.Located> located;
                try {
                    located = locator.locate(type, centerX, centerZ, radius, perType, cancelled::get);
                } catch (RuntimeException | LinkageError | StackOverflowError e) {
                    // One broken (usually modded) structure must not stop the search for all the others.
                    StructureCompass.LOGGER.warn("Structure Compass: search for {} failed: {}",
                            type.unwrapKey().map(k -> k.identifier().toString()).orElse("?"), e.toString());
                    located = List.of();
                }
                if (cancelled.get()) {
                    return;
                }
                output.add(new TypeLocated(type, located));
            }
        } catch (Throwable t) {
            StructureCompass.LOGGER.error("Structure Compass: structure search failed", t);
        } finally {
            elapsedMillis = (System.nanoTime() - start) / 1_000_000L;
            finished = true;
        }
    }

    void cancel() {
        cancelled.set(true);
    }

    boolean isCancelled() {
        return cancelled.get();
    }

    boolean isFinished() {
        return finished;
    }

    long elapsedMillis() {
        return elapsedMillis;
    }
}
