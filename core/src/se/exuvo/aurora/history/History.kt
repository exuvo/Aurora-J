package se.exuvo.aurora.history

import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.Pool.Poolable
import org.apache.log4j.Logger
import org.sqlite.SQLiteConfig
import org.sqlite.javax.SQLiteConnectionPoolDataSource
import se.exuvo.aurora.galactic.Galaxy
import se.exuvo.aurora.planetarysystems.components.EntityUUID
import se.exuvo.aurora.planetarysystems.components.UUIDComponent
import se.exuvo.aurora.utils.GameServices
import se.unlogic.standardutils.dao.AnnotatedDAO
import se.unlogic.standardutils.dao.SimpleAnnotatedDAOFactory
import se.unlogic.standardutils.dao.annotations.DAOManaged
import se.unlogic.standardutils.dao.annotations.Key
import se.unlogic.standardutils.dao.annotations.Table
import se.unlogic.standardutils.db.DBUtils
import se.unlogic.standardutils.db.tableversionhandler.TableVersionHandler
import se.unlogic.standardutils.db.tableversionhandler.UpgradeResult
import se.unlogic.standardutils.db.tableversionhandler.XMLDBScriptProvider
import java.sql.Connection
import java.sql.SQLException
import java.sql.Statement
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import com.artemis.Entity
import com.artemis.ComponentMapper
import com.artemis.World

class History : Disposable {

	val log = Logger.getLogger(this.javaClass)

	val daoFactory: SimpleAnnotatedDAOFactory
	val dataSource: SQLiteConnectionPoolDataSource
	val connectionPool: MiniConnectionPoolManager

	val historyEntityEventDAO: AnnotatedDAO<HistoryEntityEvent>

	private val executorService = Executors.newSingleThreadExecutor()

	private val galaxy by lazy (LazyThreadSafetyMode.NONE) { GameServices[Galaxy::class] }

	init {
		log.info("Opening history DB")
		Class.forName("org.sqlite.JDBC");

		val config = SQLiteConfig()
		config.setPragma(SQLiteConfig.Pragma.FOREIGN_KEYS, "ON")
		config.setJournalMode(SQLiteConfig.JournalMode.WAL)
		config.setBusyTimeout("10000")

		dataSource = SQLiteConnectionPoolDataSource(config);
		dataSource.setUrl("jdbc:sqlite:history.db")
//		dataSource.setUrl("jdbc:sqlite::memory")
		dataSource.setDatabaseName("history")
		connectionPool = MiniConnectionPoolManager(dataSource, 2)

		daoFactory = SimpleAnnotatedDAOFactory(connectionPool);

		val upgradeResult: UpgradeResult = TableVersionHandler.upgradeDBTables(dataSource, this.javaClass.getName(), XMLDBScriptProvider(this.javaClass.getResourceAsStream("DB script.xml")));

		if (upgradeResult.isUpgrade()) {

			log.info(upgradeResult.toString());
		}

		historyEntityEventDAO = daoFactory.getDAO(HistoryEntityEvent::class.java)
	}

	override fun dispose() {
		log.info("Closing history DB")

		executorService.shutdown()
		executorService.awaitTermination(1000, TimeUnit.MILLISECONDS)

		// Run full checkpoint operation
		var connection: Connection? = null
		var statement: Statement? = null;
		
		try {
			connection = connectionPool.getConnection()
			statement = connection.createStatement()
			val result = statement.executeQuery("PRAGMA wal_checkpoint(FULL);")
			
			val blocked = result.getInt(1) != 0
			val walPages = result.getInt(2)
			val movedPages = result.getInt(3)
			
			if (blocked) {
				log.info("Checkpoint blocked, moved $movedPages pages of $walPages")
				
			} else {
				log.info("Checkpoint ok, moved $movedPages pages of $walPages")
			}

		} catch (e: SQLException) {
			log.error("", e);

		} finally {

			try {
				if (statement != null) {
					statement.close();
				}
			} catch (e: SQLException) {
			}

			DBUtils.closeConnection(connection);
		}

		connectionPool.dispose()
	}

	private fun execute(f: () -> Unit) {
		executorService.execute(object : Runnable {
			override fun run() {
				f()
			}
		})
	}

	fun entityCreated(entityID: Int, world: World) {
		val time = galaxy.time
		val uuid = ComponentMapper.getFor(UUIDComponent::class.java, world).get(entityID).uuid
		
		execute({
			historyEntityEventDAO.add(HistoryEntityEvent(time, uuid, EntityEvent.CREATE))
		})
	}

	fun entityDestroyed(entityID: Int, world: World) {
		val time = galaxy.time
		val uuid = ComponentMapper.getFor(UUIDComponent::class.java, world).get(entityID).uuid
		
		execute({
			historyEntityEventDAO.add(HistoryEntityEvent(time, uuid, EntityEvent.DESTROY))
		})
	}
}

abstract class HistoryEvent() {

	@DAOManaged
	@Key
	var eventID: Long? = null

	@DAOManaged
	var time: Long? = null
}

@Table(name = "entities")
class HistoryEntityEvent() : HistoryEvent() {

	@DAOManaged
	var planetarySystemID: Int? = null

	@DAOManaged
	var empireID: Int? = null

	@DAOManaged
	var shipID: Long? = null

	@DAOManaged
	var eventType: EntityEvent? = null

	constructor (time: Long, uuid: EntityUUID, eventType: EntityEvent) : this() {
		this.time = time
		planetarySystemID = uuid.planetarySystemID
		empireID = uuid.empireID
		shipID = uuid.entityUID
		this.eventType = eventType
	}
}

enum class EntityEvent {
	CREATE,
	DESTROY,
}
