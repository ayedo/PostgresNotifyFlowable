import ch.sponty.backend.common.database.postgres.PostgresNotifyFlowable
import io.reactivex.schedulers.Schedulers
import org.junit.jupiter.api.Test
import org.postgresql.jdbc.PgConnection
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager
import java.util.concurrent.CountDownLatch

class DatabaseContainer(imageName: String) : PostgreSQLContainer<DatabaseContainer>(imageName)

@Testcontainers
class PostgresFlowableTest {

    @Container
    private val db = DatabaseContainer("postgres:12")

    @Test
    fun basicFunctionality() {

        val connection =
            DriverManager.getConnection(db.jdbcUrl, db.username, db.password).unwrap(PgConnection::class.java)

        val channels = PostgresNotifyFlowable.forChannels(
            jdbcUrl = db.jdbcUrl,
            user = db.username,
            password = db.password,
            pollingPeriodMs = 1000,
            channels = listOf("test"),
            backpressureStrategy = BUFFER
        )

        val latch = CountDownLatch(4)

        val disposable = channels
            .subscribeOn(Schedulers.newThread())
            .filter({it.name == "test"})
            .subscribe({
                latch.countDown()
            })

        Thread.sleep(1000)

        sendNotify(connection, channel = "test", payload = "testPayload")
        sendNotify(connection, channel = "test", payload = "testPayload1")
        sendNotify(connection, channel = "test", payload = "testPayload2")
        sendNotify(connection, channel = "test", payload = "testPayload3")

        latch.await()

        disposable.dispose()

    }

    private fun sendNotify(connection: PgConnection, channel: String, payload: String) {
        connection.createStatement().use { statement ->
            statement.execute("notify $channel, '$payload';")
        }
    }
}
