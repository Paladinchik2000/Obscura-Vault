package com.obscura.security

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.room.withTransaction
import com.obscura.data.local.VaultDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.crypto.SecretKey
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Thrown when vault data is requested while the vault is locked. */
class VaultLockedException(cause: Throwable? = null) : IllegalStateException("Vault is locked", cause)

/**
 * Holds the unwrapped DEK, the open vault database and the coroutine scope that owns
 * every database access for the lifetime of an unlocked session.
 * Deliberately process-scoped and never written to disk.
 *
 * Locking cancels the session scope, waits for all of its work to finish and only then
 * closes the database, so no query ever runs against a closed instance.
 */
object VaultSession {

    private const val TAG = "VaultSession"

    private class Session(val dek: SecretKey, val database: VaultDatabase) {
        val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.IO + SessionElement(this) +
                CoroutineExceptionHandler { _, e -> Log.e(TAG, "Vault session task failed", e) }
        )
    }

    /** Marks coroutines running in a session scope and remembers which session they belong to. */
    private class SessionElement(val session: Session) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<SessionElement>
    }

    @Volatile private var session: Session? = null

    /** Serializes unlock/lock so two sessions never have the database file open at once. */
    private val transitions = Mutex()

    /** Runs lock requests from non-suspending callers. Never cancelled. */
    private val controlScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var lastActivityAt: Long = 0L

    /**
     * Set while the app itself has a system screen open for a result (a SAF picker). That screen
     * is a separate activity, so ours is stopped while it is up; locking then would throw the
     * result away. 0 means nothing of ours is open.
     */
    @Volatile private var awaitingOwnResultSince: Long = 0L

    /** Locks once the grace below runs out, so an abandoned picker cannot hold the vault open. */
    private var graceLockJob: Job? = null

    /** How long a system screen we started may hold off the auto-lock. */
    @VisibleForTesting
    internal var ownResultGraceMs: Long = 2 * 60 * 1000L

    /** Auto-lock after this long in the background; set from AutoLockSettings. */
    @Volatile
    var idleTimeoutMs: Long = 2 * 60 * 1000L

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    // ---------------------------------------------------------------- lifecycle

    suspend fun unlock(context: Context, key: SecretKey) {
        checkNotInSession("unlock")
        // Room only builds the instance here; the file is opened lazily, inside the session.
        unlock(key, VaultDatabase.open(context, key))
    }

    @VisibleForTesting
    internal suspend fun unlock(key: SecretKey, database: VaultDatabase) {
        checkNotInSession("unlock")
        withContext(NonCancellable) {
            transitions.withLock {
                closeSession()
                session = Session(key, database)
                touch()
                _isUnlocked.value = true
            }
        }
    }

    /**
     * Rejects new work, cancels the session scope, waits for everything running in it
     * to finish and only then closes the database. Completes even if the caller is
     * cancelled midway. Must not be called from inside the session scope — use
     * [requestLock] there.
     */
    suspend fun lock() {
        checkNotInSession("lock")
        withContext(NonCancellable) {
            transitions.withLock { closeSession() }
        }
    }

    /** Fire-and-forget [lock] for UI callbacks and lifecycle observers. */
    fun requestLock(): Job = controlScope.launch { lock() }

    fun requireKey(): SecretKey =
        session?.dek ?: throw VaultLockedException()

    fun touch() {
        lastActivityAt = System.currentTimeMillis()
    }

    /**
     * Call right before launching a system screen for a result, and again from the result
     * callback — including when the user cancels — via [finishedOwnActivityResult].
     */
    fun startedOwnActivityResult() {
        awaitingOwnResultSince = System.currentTimeMillis()
    }

    fun finishedOwnActivityResult() {
        awaitingOwnResultSince = 0L
        graceLockJob?.cancel()
        graceLockJob = null
        touch()
    }

    /** Milliseconds left of the grace, or 0 when nothing of ours is open or it has run out. */
    private fun ownResultGraceRemaining(): Long {
        val since = awaitingOwnResultSince
        if (since == 0L) return 0L
        return (since + ownResultGraceMs - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    /** With the "immediately" setting there is no grace period: lock as the app leaves the screen. */
    fun lockIfImmediate() {
        if (session == null || idleTimeoutMs > 0L) return

        val remaining = ownResultGraceRemaining()
        if (remaining == 0L) {
            requestLock()
            return
        }

        // Our own picker is open: hold the lock until it returns, but never indefinitely.
        graceLockJob?.cancel()
        graceLockJob = controlScope.launch {
            delay(remaining)
            if (ownResultGraceRemaining() == 0L && awaitingOwnResultSince != 0L) {
                awaitingOwnResultSince = 0L
                lock()
            }
        }
    }

    fun lockIfIdle() {
        if (session == null) return
        // Coming back from our own picker is not idleness, whatever the timeout is.
        if (ownResultGraceRemaining() > 0L) {
            touch()
            return
        }
        if (System.currentTimeMillis() - lastActivityAt > idleTimeoutMs) requestLock()
    }

    /** Caller holds [transitions]. */
    private suspend fun closeSession() {
        awaitingOwnResultSince = 0L
        graceLockJob?.cancel()
        graceLockJob = null
        val current = session ?: return
        // New work is refused from here on; work already running keeps its own session reference.
        session = null
        _isUnlocked.value = false
        withContext(Dispatchers.IO) {
            current.scope.coroutineContext.job.cancelAndJoin()
            current.database.close()
        }
        // SecretKeySpec.encoded returns a copy, so the DEK bytes can't be zeroed here.
        // Keep the session short instead; that's the real mitigation.
    }

    // -------------------------------------------------------------- data access

    /**
     * Database of the session the calling coroutine belongs to. Only callable from work
     * started via [runInSession], [runInTransaction], [launchInSession] or [observe], so
     * that [lock] can wait for it before closing the database.
     */
    suspend fun requireDatabase(): VaultDatabase {
        val context = currentCoroutineContext()
        val owner = context[SessionElement]
            ?: throw IllegalStateException(
                "Vault database accessed outside the session scope; use VaultSession.runInSession"
            )
        context.ensureActive()
        return owner.session.database
    }

    /**
     * Runs [block] in the session scope and returns its result. The work belongs to the
     * session, not to the caller: cancelling the caller doesn't stop it, locking does.
     *
     * @throws VaultLockedException if the vault is locked, or locks before [block] completes.
     */
    suspend fun <T> runInSession(block: suspend CoroutineScope.() -> T): T {
        val current = session ?: throw VaultLockedException()
        val result = current.scope.async(block = block)
        return try {
            result.await()
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive() // our caller was cancelled: propagate that
            if (current.scope.coroutineContext.job.isCancelled) throw VaultLockedException(e)
            throw e
        }
    }

    /**
     * Runs [block] as a single Room transaction in the session scope. Use it for every write
     * that takes more than one statement. Any exception — including the cancellation caused
     * by a lock between steps — rolls the whole transaction back, and [lock] waits for the
     * rollback before closing the database (Room runs the transaction as a child coroutine).
     *
     * @throws VaultLockedException if the vault is locked, or locks before the commit.
     */
    suspend fun <T> runInTransaction(block: suspend () -> T): T =
        runInSession { requireDatabase().withTransaction(block) }

    /**
     * Starts [block] in the session scope without waiting for it.
     *
     * @throws VaultLockedException if the vault is locked.
     */
    fun launchInSession(block: suspend CoroutineScope.() -> Unit): Job {
        val current = session ?: throw VaultLockedException()
        return current.scope.launch(block = block)
    }

    /**
     * A flow whose [upstream] (typically a Room query flow) is collected inside the session
     * scope. It completes when the vault locks, and completes without emitting if the vault
     * is already locked.
     */
    fun <T> observe(upstream: suspend () -> Flow<T>): Flow<T> = channelFlow {
        val current = session ?: return@channelFlow
        val producer = current.scope.launch {
            upstream().collect { send(it) }
        }
        // Lock cancels the producer, which ends the flow normally; real failures reach the collector.
        producer.invokeOnCompletion { cause -> close(cause?.takeUnless { it is CancellationException }) }
        awaitClose { producer.cancel() }
    }

    private suspend fun checkNotInSession(operation: String) {
        check(currentCoroutineContext()[SessionElement] == null) {
            "$operation() must not be called from inside the vault session scope: it would wait for itself"
        }
    }
}
