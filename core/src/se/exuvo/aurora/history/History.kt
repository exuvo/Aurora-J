package se.exuvo.aurora.history

import com.artemis.ComponentMapper
import com.artemis.World
import com.badlogic.gdx.utils.Disposable
import org.apache.logging.log4j.LogManager
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
import org.sqlite.SQLiteOpenMode
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.LinkedBlockingQueue
import org.sqlite.SQLiteConfig.LockingMode
import org.sqlite.SQLiteConfig.SynchronousMode
import se.unlogic.standardutils.threads.ThreadUtils
import se.unlogic.standardutils.io.FileUtils

class History : Disposable {

	val log = LogManager.getLogger(this.javaClass)

	val daoFactory: SimpleAnnotatedDAOFactory
	val dataSource: SQLiteConnectionPoolDataSource
	val connectionPool: MiniConnectionPoolManager

	val historyEntityEventDAO: AnnotatedDAO<HistoryEntityEvent>

	// Only seems to affects reads and not linearly
	private val executorService = ThreadPoolExecutor(5, 5, 10000L, TimeUnit.MILLISECONDS, LinkedBlockingQueue<Runnable>())

	private val galaxy by lazy (LazyThreadSafetyMode.NONE) { GameServices[Galaxy::class] }

	init {
		log.info("Opening history DB")
		Class.forName("org.sqlite.JDBC");
		
		FileUtils.deleteFile("history.db-shm")
		FileUtils.deleteFile("history.db-wal")
		FileUtils.deleteFile("history.db")

		val config = SQLiteConfig()
		config.setPragma(SQLiteConfig.Pragma.FOREIGN_KEYS, "true")
		config.setPragma(SQLiteConfig.Pragma.SYNCHRONOUS, SynchronousMode.OFF.getValue())
		config.setOpenMode(SQLiteOpenMode.NOMUTEX) // Should enable SQLITE_CONFIG_MULTITHREAD, see https://github.com/xerial/sqlite-jdbc/issues/369 and https://www.sqlite.org/threadsafe.html
		
		config.setJournalMode(SQLiteConfig.JournalMode.WAL)
//		config.setJournalMode(SQLiteConfig.JournalMode.MEMORY) // No benefit for small data as WAL already caches in memory
		
		config.setBusyTimeout(10000)

		dataSource = SQLiteConnectionPoolDataSource(config);
		dataSource.setUrl("jdbc:sqlite:history.db")
//		dataSource.setUrl("jdbc:sqlite::memory")
		dataSource.setDatabaseName("history")
		connectionPool = MiniConnectionPoolManager(dataSource, 5)

		daoFactory = SimpleAnnotatedDAOFactory(connectionPool);

		val upgradeResult: UpgradeResult = TableVersionHandler.upgradeDBTables(dataSource, this.javaClass.getName(), XMLDBScriptProvider(this.javaClass.getResourceAsStream("DB script.xml")));

		if (upgradeResult.isUpgrade()) {

			log.info(upgradeResult.toString());
		}

		historyEntityEventDAO = daoFactory.getDAO(HistoryEntityEvent::class.java)
		
//		val uuid = EntityUUID(1,2,3)
//		log.info("Write test..")
//		var time = kotlin.system.measureTimeMillis {
//			for(i in 0..100000L) {
//				execute({
//					historyEntityEventDAO.add(HistoryEntityEvent(i, uuid, EntityEvent.CREATE))
//				})
//			}
//			while (executorService.getTaskCount() - executorService.getCompletedTaskCount() > 0L) {
//				ThreadUtils.sleep(1)
//			}
//		}
//		log.info("Write in $time ms")
//		
//		log.info("Read test..")
//		time = kotlin.system.measureTimeMillis {
//			for(i in 0..100L) {
//				execute({
//					historyEntityEventDAO.getAll()
//				})
//			}
//			while (executorService.getTaskCount() - executorService.getCompletedTaskCount() > 0L) {
//				ThreadUtils.sleep(1)
//			}
//		}
//		log.info("Read in $time ms")
	}

	override fun dispose() {
		executorService.shutdown()
		
		var tasks = 0L
		
		while (executorService.isTerminating()) {
			val newTasks = executorService.getTaskCount() - executorService.getCompletedTaskCount()
			log.info("Waiting on History DB ${newTasks} queued tasks, ${tasks - newTasks} /s")
			tasks = newTasks
			executorService.awaitTermination(1, TimeUnit.SECONDS)
		}
		
		log.info("Closing history DB..")
		
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
		
		log.info("Closed history DB")
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
