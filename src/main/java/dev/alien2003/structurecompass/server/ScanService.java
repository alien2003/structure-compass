package dev.alien2003.structurecompass.server;

import dev.alien2003.structurecompass.StructureCompass;
import dev.alien2003.structurecompass.common.StructureEntry;
import dev.alien2003.structurecompass.common.TypeResult;
import dev.alien2003.structurecompass.network.ScanRequestPayload;
import dev.alien2003.structurecompass.network.ScanResultsPayload;
import dev.alien2003.structurecompass.network.ScanStartPayload;
import dev.alien2003.structurecompass.network.ServerHelloPayload;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Owns the structure search for one running (integrated) server.
 *
 * <p>Threading: every method of this class runs on the server thread, except {@link ScanJob#run()} which runs on a
 * single low-priority worker thread and only touches the immutable {@link StructureLocator} snapshot plus the job's
 * concurrent output queue. The server thread never waits for the worker.
 */
public final class ScanService {
    public static final int MIN_RADIUS = 64;
    public static final int MAX_RADIUS = 32_000;
    public static final int MAX_PER_TYPE = 10;
    public static final int MIN_RESCAN_DISTANCE = 16;
    /** Max number of per-type results converted and sent per player per tick (keeps ticks cheap). */
    private static final int MAX_RESULTS_PER_TICK = 48;
    /** Rough payload budget per packet (vanilla limit for custom payloads is 1 MiB). */
    private static final int PAYLOAD_BUDGET_BYTES = 256 * 1024;
    private static final int REFRESH_INTERVAL_TICKS = 20;
    private static final int LOADED_SCAN_INTERVAL_TICKS = 100;
    private static final int LOADED_SCAN_CHUNK_RADIUS = 12;

    private static @Nullable ScanService instance;

    private final MinecraftServer server;
    private final boolean enabled;
    private final ExecutorService worker;
    private final Map<ResourceKey<Level>, StructureLocator> locators = new HashMap<>();
    private final Map<UUID, PlayerState> players = new HashMap<>();
    private int nextScanId = 1;
    private long tickCount;

    /** One structure instance as tracked on the server for a player's current-dimension results. */
    private static final class Tracked {
        final int setIndex;
        StructureEntry entry;
        double distanceSqr;

        Tracked(int setIndex, StructureEntry entry, double distanceSqr) {
            this.setIndex = setIndex;
            this.entry = entry;
            this.distanceSqr = distanceSqr;
        }
    }

    private static final class PlayerState {
        boolean configured;
        int radius = 4000;
        int perType = 3;
        int rescanDistance = 128;
        @Nullable ScanJob autoJob;
        boolean autoDoneSent;
        @Nullable ScanJob browseJob;
        @Nullable ResourceKey<Level> scanDimension;
        double scanX;
        double scanZ;
        boolean scanCenterValid;
        final Map<Identifier, List<Tracked>> results = new LinkedHashMap<>();
        final Map<Identifier, Holder<Structure>> holders = new HashMap<>();
        /** Types whose predictions turned out wrong and need a fresh search once the current job is drained. */
        final Set<Holder<Structure>> pendingRelocate = new LinkedHashSet<>();
    }

    private ScanService(MinecraftServer server) {
        this.server = server;
        this.enabled = !server.isDedicatedServer();
        this.worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "StructureCompass-Locator");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY + 1);
            thread.setUncaughtExceptionHandler((t, e) -> StructureCompass.LOGGER.error("Structure Compass worker crashed", e));
            return thread;
        });
    }

    public static @Nullable ScanService get() {
        return instance;
    }

    public static void start(MinecraftServer server) {
        stop();
        instance = new ScanService(server);
        if (!instance.enabled) {
            StructureCompass.LOGGER.info("Structure Compass: dedicated server detected, structure search is disabled (single-player mod)");
        }
    }

    public static void stop() {
        ScanService old = instance;
        instance = null;
        if (old != null) {
            old.shutdown();
        }
    }

    private void shutdown() {
        for (PlayerState state : players.values()) {
            cancel(state.autoJob);
            cancel(state.browseJob);
        }
        players.clear();
        locators.clear();
        worker.shutdownNow();
        try {
            worker.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Events (server thread)

    public void onPlayerLoggedIn(ServerPlayer player) {
        if (player.connection.hasChannel(ServerHelloPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, new ServerHelloPayload(enabled, MAX_RADIUS));
        }
    }

    public void onPlayerLoggedOut(ServerPlayer player) {
        PlayerState state = players.remove(player.getUUID());
        if (state != null) {
            cancel(state.autoJob);
            cancel(state.browseJob);
        }
    }

    /**
     * Datapack reload ({@code /reload}): biome tags and structure templates may have changed, so every cached
     * prediction is dropped and all players are re-scanned on the next tick.
     */
    public void onDataReloaded() {
        locators.clear();
        for (PlayerState state : players.values()) {
            cancel(state.autoJob);
            cancel(state.browseJob);
            state.autoJob = null;
            state.browseJob = null;
            state.pendingRelocate.clear();
            state.results.clear();
            state.scanDimension = null; // forces a fresh auto scan
        }
    }

    public void handleRequest(ServerPlayer player, ScanRequestPayload request) {
        if (!enabled) {
            return;
        }
        PlayerState state = players.computeIfAbsent(player.getUUID(), id -> new PlayerState());
        int radius = clamp(request.radius(), MIN_RADIUS, MAX_RADIUS);
        int perType = clamp(request.perType(), 1, MAX_PER_TYPE);
        int rescan = clamp(request.rescanDistance(), MIN_RESCAN_DISTANCE, MAX_RADIUS);
        boolean settingsChanged = !state.configured || radius != state.radius || perType != state.perType;
        state.configured = true;
        state.radius = radius;
        state.perType = perType;
        state.rescanDistance = rescan;

        ServerLevel current = player.level();
        if (request.dimension().isPresent() && request.dimension().get() != current.dimension()) {
            ServerLevel target = server.getLevel(request.dimension().get());
            if (target != null) {
                startBrowseScan(player, state, current, target);
            }
            return;
        }
        if (request.force() || settingsChanged || state.scanDimension != current.dimension()) {
            startAutoScan(player, state, current);
        }
    }

    public void tick() {
        if (!enabled || players.isEmpty()) {
            return;
        }
        try {
            tickPlayers();
        } catch (RuntimeException e) {
            // Never take the server tick down with us.
            StructureCompass.LOGGER.error("Structure Compass: server tick failed", e);
        }
    }

    private void tickPlayers() {
        tickCount++;
        Iterator<Map.Entry<UUID, PlayerState>> it = players.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, PlayerState> e = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player == null) {
                cancel(e.getValue().autoJob);
                cancel(e.getValue().browseJob);
                it.remove();
                continue;
            }
            tickPlayer(player, e.getValue());
        }
    }

    // ---------------------------------------------------------------------------------------------------------

    private void tickPlayer(ServerPlayer player, PlayerState state) {
        if (!state.configured) {
            return;
        }
        ServerLevel level = player.level();
        if (tickCount % 10 == 0) {
            boolean dimensionChanged = state.scanDimension != level.dimension();
            boolean moved = state.scanCenterValid && horizontalDistanceSqr(player.getX(), player.getZ(), state.scanX, state.scanZ)
                    > (double) state.rescanDistance * state.rescanDistance;
            if (dimensionChanged || moved) {
                startAutoScan(player, state, level);
            }
        }
        drainAuto(player, state);
        drainBrowse(player, state);
        ScanJob current = state.autoJob;
        if (!state.pendingRelocate.isEmpty() && current != null && current.isFinished() && current.output.isEmpty()
                && state.scanDimension == level.dimension()) {
            List<Holder<Structure>> types = new ArrayList<>(state.pendingRelocate);
            state.pendingRelocate.clear();
            relocateTypes(state, level, types);
        }
        if (state.scanDimension == level.dimension()) {
            if (tickCount % REFRESH_INTERVAL_TICKS == 0) {
                refreshGenerated(player, state, level);
            }
            if (tickCount % LOADED_SCAN_INTERVAL_TICKS == 7) {
                scanLoadedChunks(player, state, level);
            }
        }
    }

    private StructureLocator locator(ServerLevel level) {
        return locators.computeIfAbsent(level.dimension(), key -> StructureLocator.create(level));
    }

    private void startAutoScan(ServerPlayer player, PlayerState state, ServerLevel level) {
        cancel(state.autoJob);
        StructureLocator locator = locator(level);
        int scanId = nextScanId++;
        int cx = (int) Math.floor(player.getX());
        int cz = (int) Math.floor(player.getZ());
        state.scanDimension = level.dimension();
        state.scanX = player.getX();
        state.scanZ = player.getZ();
        state.scanCenterValid = true;
        state.results.clear();
        state.holders.clear();
        state.pendingRelocate.clear();
        for (Holder.Reference<Structure> ref : locator.structures()) {
            state.holders.put(ref.key().identifier(), ref);
        }
        ScanJob job = new ScanJob(locator, scanId, cx, cz, state.radius, state.perType, locator.structures());
        state.autoJob = job;
        state.autoDoneSent = false;
        PacketDistributor.sendToPlayer(player, new ScanStartPayload(level.dimension(), scanId, cx, cz, state.radius,
                locator.structureIds()));
        worker.execute(job);
    }

    private void startBrowseScan(ServerPlayer player, PlayerState state, ServerLevel from, ServerLevel target) {
        cancel(state.browseJob);
        StructureLocator locator = locator(target);
        double scale = from.dimensionType().coordinateScale() / target.dimensionType().coordinateScale();
        var border = target.getWorldBorder();
        int cx = (int) Math.floor(Math.max(border.getMinX(), Math.min(border.getMaxX() - 1, player.getX() * scale)));
        int cz = (int) Math.floor(Math.max(border.getMinZ(), Math.min(border.getMaxZ() - 1, player.getZ() * scale)));
        int scanId = nextScanId++;
        ScanJob job = new ScanJob(locator, scanId, cx, cz, state.radius, state.perType, locator.structures());
        state.browseJob = job;
        PacketDistributor.sendToPlayer(player, new ScanStartPayload(target.dimension(), scanId, cx, cz, state.radius,
                locator.structureIds()));
        worker.execute(job);
    }

    /** Re-locates only some types of the current auto scan (after a prediction turned out wrong). */
    private void relocateTypes(PlayerState state, ServerLevel level, List<Holder<Structure>> types) {
        if (types.isEmpty() || state.autoJob == null) {
            return;
        }
        if (!state.autoJob.isFinished() || !state.autoJob.output.isEmpty()) {
            state.pendingRelocate.addAll(types); // retried by tickPlayer once the current job is drained
            return;
        }
        StructureLocator locator = locator(level);
        ScanJob job = new ScanJob(locator, state.autoJob.scanId, (int) Math.floor(state.scanX), (int) Math.floor(state.scanZ),
                state.radius, state.perType, List.copyOf(types));
        state.autoJob = job;
        state.autoDoneSent = false;
        worker.execute(job);
    }

    private void drainAuto(ServerPlayer player, PlayerState state) {
        ScanJob job = state.autoJob;
        if (job == null) {
            return;
        }
        ServerLevel level = server.getLevel(job.locator.dimension());
        if (level == null) {
            return;
        }
        List<TypeResult> batch = new ArrayList<>();
        int processed = 0;
        ScanJob.TypeLocated located;
        while (processed < MAX_RESULTS_PER_TICK && (located = job.output.poll()) != null) {
            processed++;
            Identifier id = located.type().unwrapKey().map(ResourceKey::identifier).orElse(null);
            if (id == null) {
                continue;
            }
            List<Tracked> tracked = new ArrayList<>(located.located().size());
            for (StructureLocator.Located loc : located.located()) {
                Tracked t = resolve(level, job.locator, loc, job.centerX, job.centerZ);
                if (t != null) {
                    tracked.add(t);
                } else {
                    state.pendingRelocate.add(located.type()); // a loaded chunk disproved it: search that type again
                }
            }
            // Real boxes move the target from the generation point to the box centre: keep "nearest first".
            tracked.sort((a, b) -> Double.compare(a.distanceSqr, b.distanceSqr));
            state.results.put(id, tracked);
            batch.add(toTypeResult(id, tracked));
        }
        boolean done = job.isFinished() && job.output.isEmpty();
        if (!batch.isEmpty() || (done && !state.autoDoneSent)) {
            send(player, job.locator.dimension(), job.scanId, done, batch);
        }
        if (done && !state.autoDoneSent) {
            // The finished job stays referenced (its scan id is reused for incremental updates).
            state.autoDoneSent = true;
            StructureCompass.LOGGER.debug("Structure Compass: scan {} of {} finished in {} ms",
                    job.scanId, job.locator.dimension().identifier(), job.elapsedMillis());
        }
    }

    private void drainBrowse(ServerPlayer player, PlayerState state) {
        ScanJob job = state.browseJob;
        if (job == null) {
            return;
        }
        ServerLevel level = server.getLevel(job.locator.dimension());
        if (level == null) {
            state.browseJob = null;
            return;
        }
        List<TypeResult> batch = new ArrayList<>();
        int processed = 0;
        ScanJob.TypeLocated located;
        while (processed < MAX_RESULTS_PER_TICK && (located = job.output.poll()) != null) {
            processed++;
            Identifier id = located.type().unwrapKey().map(ResourceKey::identifier).orElse(null);
            if (id == null) {
                continue;
            }
            List<Tracked> tracked = new ArrayList<>(located.located().size());
            for (StructureLocator.Located loc : located.located()) {
                Tracked t = resolve(level, job.locator, loc, job.centerX, job.centerZ);
                if (t != null) {
                    tracked.add(t);
                }
            }
            tracked.sort((a, b) -> Double.compare(a.distanceSqr, b.distanceSqr));
            batch.add(toTypeResult(id, tracked));
        }
        boolean done = job.isFinished() && job.output.isEmpty();
        if (!batch.isEmpty() || done) {
            send(player, job.locator.dimension(), job.scanId, done, batch);
        }
        if (done) {
            state.browseJob = null;
        }
    }

    /** Turns a prediction into an entry, using the real start when its chunk is loaded. */
    private @Nullable Tracked resolve(ServerLevel level, StructureLocator locator, StructureLocator.Located loc,
                                      int centerX, int centerZ) {
        GeneratedStructures.Check check = GeneratedStructures.check(level, loc.structure().value(), loc.chunkX(), loc.chunkZ());
        return switch (check.status()) {
            case ABSENT -> {
                locator.invalidate(loc.setIndex(), loc.chunkX(), loc.chunkZ());
                yield null;
            }
            case PRESENT -> {
                StructureEntry entry = check.entry();
                double dx = entry.x() - centerX;
                double dz = entry.z() - centerZ;
                yield new Tracked(loc.setIndex(), entry, dx * dx + dz * dz);
            }
            case NOT_LOADED -> new Tracked(loc.setIndex(),
                    StructureEntry.predicted(loc.chunkX(), loc.chunkZ(), loc.x(), loc.z()), loc.distanceSqr());
        };
    }

    /** Upgrades predicted entries whose start chunk got loaded since; drops wrong predictions. */
    private void refreshGenerated(ServerPlayer player, PlayerState state, ServerLevel level) {
        ScanJob job = state.autoJob;
        if (job == null) {
            return;
        }
        List<TypeResult> batch = new ArrayList<>();
        List<Holder<Structure>> relocate = new ArrayList<>();
        StructureLocator locator = locator(level);
        for (Map.Entry<Identifier, List<Tracked>> e : state.results.entrySet()) {
            Holder<Structure> holder = state.holders.get(e.getKey());
            if (holder == null) {
                continue;
            }
            boolean changed = false;
            boolean removed = false;
            Iterator<Tracked> it = e.getValue().iterator();
            while (it.hasNext()) {
                Tracked t = it.next();
                if (t.entry.generated()) {
                    continue;
                }
                GeneratedStructures.Check check = GeneratedStructures.check(level, holder.value(),
                        t.entry.startChunkX(), t.entry.startChunkZ());
                if (check.status() == GeneratedStructures.Status.PRESENT) {
                    t.entry = check.entry();
                    double dx = t.entry.x() - state.scanX;
                    double dz = t.entry.z() - state.scanZ;
                    t.distanceSqr = dx * dx + dz * dz;
                    changed = true;
                } else if (check.status() == GeneratedStructures.Status.ABSENT) {
                    locator.invalidate(t.setIndex, t.entry.startChunkX(), t.entry.startChunkZ());
                    it.remove();
                    changed = true;
                    removed = true;
                }
            }
            if (changed) {
                e.getValue().sort((a, b) -> Double.compare(a.distanceSqr, b.distanceSqr));
                batch.add(toTypeResult(e.getKey(), e.getValue()));
            }
            if (removed) {
                relocate.add(holder);
            }
        }
        if (!batch.isEmpty()) {
            send(player, level.dimension(), job.scanId, false, batch);
        }
        relocateTypes(state, level, relocate);
    }

    /**
     * Adds structures that really exist in loaded chunks around the player but were not predicted (e.g. placed with
     * /place, or by a modded placement type the predictor cannot evaluate).
     */
    private void scanLoadedChunks(ServerPlayer player, PlayerState state, ServerLevel level) {
        ScanJob job = state.autoJob;
        if (job == null) {
            return;
        }
        int pcx = player.chunkPosition().x;
        int pcz = player.chunkPosition().z;
        Set<Identifier> changed = new LinkedHashSet<>();
        var structureRegistry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        for (int dx = -LOADED_SCAN_CHUNK_RADIUS; dx <= LOADED_SCAN_CHUNK_RADIUS; dx++) {
            for (int dz = -LOADED_SCAN_CHUNK_RADIUS; dz <= LOADED_SCAN_CHUNK_RADIUS; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(pcx + dx, pcz + dz);
                if (chunk == null) {
                    continue;
                }
                for (Map.Entry<Structure, StructureStart> s : chunk.getAllStarts().entrySet()) {
                    StructureStart start = s.getValue();
                    if (start == null || !start.isValid()) {
                        continue;
                    }
                    Identifier id = structureRegistry.getKey(s.getKey());
                    if (id == null) {
                        continue;
                    }
                    List<Tracked> list = state.results.get(id);
                    if (list == null) {
                        continue; // type not part of this scan (or not delivered yet)
                    }
                    int scx = start.getChunkPos().x;
                    int scz = start.getChunkPos().z;
                    boolean known = false;
                    for (Tracked t : list) {
                        if (t.entry.startChunkX() == scx && t.entry.startChunkZ() == scz) {
                            known = true;
                            break;
                        }
                    }
                    if (known) {
                        continue;
                    }
                    StructureEntry entry = GeneratedStructures.toEntry(start);
                    double ex = entry.x() - state.scanX;
                    double ez = entry.z() - state.scanZ;
                    double d2 = ex * ex + ez * ez;
                    if (d2 > (double) state.radius * state.radius) {
                        continue;
                    }
                    if (list.size() >= state.perType && list.get(list.size() - 1).distanceSqr <= d2) {
                        continue;
                    }
                    list.add(new Tracked(-1, entry, d2));
                    list.sort((a, b) -> Double.compare(a.distanceSqr, b.distanceSqr));
                    while (list.size() > state.perType) {
                        list.remove(list.size() - 1);
                    }
                    changed.add(id);
                }
            }
        }
        if (!changed.isEmpty()) {
            List<TypeResult> batch = new ArrayList<>(changed.size());
            for (Identifier id : changed) {
                batch.add(toTypeResult(id, state.results.get(id)));
            }
            send(player, level.dimension(), job.scanId, false, batch);
        }
    }

    private static TypeResult toTypeResult(Identifier id, List<Tracked> tracked) {
        List<StructureEntry> entries = new ArrayList<>(tracked.size());
        for (Tracked t : tracked) {
            entries.add(t.entry);
        }
        return new TypeResult(id, List.copyOf(entries));
    }

    /** Sends results, split into several packets when they would get large. */
    private static void send(ServerPlayer player, ResourceKey<Level> dimension, int scanId, boolean done,
                             List<TypeResult> results) {
        List<TypeResult> chunk = new ArrayList<>();
        int bytes = 0;
        for (int i = 0; i < results.size(); i++) {
            TypeResult r = results.get(i);
            int size = estimateSize(r);
            if (!chunk.isEmpty() && bytes + size > PAYLOAD_BUDGET_BYTES) {
                PacketDistributor.sendToPlayer(player, new ScanResultsPayload(dimension, scanId, false, List.copyOf(chunk)));
                chunk.clear();
                bytes = 0;
            }
            chunk.add(r);
            bytes += size;
        }
        PacketDistributor.sendToPlayer(player, new ScanResultsPayload(dimension, scanId, done, List.copyOf(chunk)));
    }

    private static int estimateSize(TypeResult r) {
        int size = 16 + r.type().toString().length();
        for (StructureEntry e : r.entries()) {
            size += 32 + e.pieces().size() * 30;
        }
        return size;
    }

    private static void cancel(@Nullable ScanJob job) {
        if (job != null) {
            job.cancel();
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double horizontalDistanceSqr(double x1, double z1, double x2, double z2) {
        double dx = x1 - x2;
        double dz = z1 - z2;
        return dx * dx + dz * dz;
    }
}
