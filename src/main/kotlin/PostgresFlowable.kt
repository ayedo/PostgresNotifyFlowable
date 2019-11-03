package ch.sponty.backend.common.database.postgres

import io.reactivex.BackpressureStrategy
import io.reactivex.BackpressureStrategy.*
import io.reactivex.Flowable
import io.reactivex.Flowable.create
import org.postgresql.PGNotification
import org.postgresql.jdbc.PgConnection
import org.slf4j.LoggerFactory
import java.sql.DriverManager
import java.util.concurrent.TimeUnit.MILLISECONDS

object PostgresNotifyFlowable {

    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     *  Returns a [Flowable] which for every NOTIFY send on any of the channels in [channels] will emit an event.
     *  The connection will be polled every [pollingPeriodMs]. If a database call is unsuccessful, a reconnection will
     *  be initiated. In between trying to reconnect, it is going to wait [reconnectionTimeoutMs].
     *
     *  CAUTION: every call to this will create a new connection to the database, but multiple subscriptions to the
     *  returned Flowable will _not_ create new connections. The idea is to use this for as many channels as possible,
     *  and then using `.filter()` on the returned Flowable to only subscribe to Notifications from particular channels.
     *
     * @param jdbcUrl The JDBC url to use to connect to Postgres
     * @param user The user name to use to connect to Postgres
     * @param password The password to use to connect to Postgres
     * @param pollingPeriodMs Number of milliseconds to wait before sending the next query to the database to get new NOTIFY messages
     * @param reconnectionTimeoutMs Number of milliseconds to wait before trying to reconnect after
     * @param channels A list of channels for which to LISTEN to, and for which events are going to be emitted for by the returned Flowable.
     * @param backpressureStrategy See https://github.com/ReactiveX/RxJava/wiki/Backpressure-(2.0)
     *
     * @return A [Flowable] of [PGNotification] which for every NOTIFY send on any of the channels in [channels] will emit an event.
     */
    fun forChannels(
        jdbcUrl: String,
        user: String,
        password: String,
        pollingPeriodMs: Long = 1000,
        reconnectionTimeoutMs: Long = 5000,
        channels: List<String>,
        backpressureStrategy: BackpressureStrategy = BUFFER
    ): Flowable<PGNotification> {

        require(!channels.any({ it.isBlank() })) { "Channel name cannot be blank." }

        val notifications = create<PGNotification>({ emitter ->

            try {

                val connection = DriverManager.getConnection(jdbcUrl, user, password).unwrap(PgConnection::class.java)

                emitter.setCancellable({ connection.close() })

                connection.createStatement().use { statement ->

                    channels.forEach({ channel ->
                        statement.execute("listen $channel")
                    })

                    logger.info("Listening on channels: ${channels.joinToString(", ")}")

                }

                while (!emitter.isCancelled) {

                    connection.createStatement().use { statement ->
                        statement.executeQuery("select 1").close()
                    }

                    val notifications = connection.notifications.orEmpty()

                    notifications.forEach { emitter.onNext(it) }

                    try {
                        Thread.sleep(pollingPeriodMs)
                    } catch (ie: InterruptedException) {
                        break
                    }
                }

            } catch (ex: Exception) {
                emitter.onError(ex)
            }

        }, backpressureStrategy)

        val retryingObservable = notifications
            .doOnError({
                logger.error(
                    "Exception while retrieving notifications from database. Will retry in ${reconnectionTimeoutMs}ms",
                    it
                )
            })
            .retryWhen({ throwable ->
                throwable.delay(reconnectionTimeoutMs, MILLISECONDS)
            })

        return retryingObservable.share()
    }

}
