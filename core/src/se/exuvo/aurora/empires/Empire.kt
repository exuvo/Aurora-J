package se.exuvo.aurora.empires

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntityListener
import com.badlogic.ashley.core.PooledEngine
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.utils.Pool.Poolable
import org.apache.log4j.Logger
import se.exuvo.aurora.Assets
import se.exuvo.aurora.empires.components.EmpireFundsComponent
import se.exuvo.aurora.planetarysystems.components.ApproachType
import se.exuvo.aurora.planetarysystems.components.CircleComponent
import se.exuvo.aurora.planetarysystems.components.GalacticPositionComponent
import se.exuvo.aurora.planetarysystems.components.MassComponent
import se.exuvo.aurora.planetarysystems.components.MoveToEntityComponent
import se.exuvo.aurora.planetarysystems.components.NameComponent
import se.exuvo.aurora.planetarysystems.components.OrbitComponent
import se.exuvo.aurora.planetarysystems.components.PlanetarySystemComponent
import se.exuvo.aurora.planetarysystems.components.PositionComponent
import se.exuvo.aurora.planetarysystems.components.RenderComponent
import se.exuvo.aurora.planetarysystems.components.SolarIrradianceComponent
import se.exuvo.aurora.planetarysystems.components.StrategicIconComponent
import se.exuvo.aurora.planetarysystems.components.SunComponent
import se.exuvo.aurora.planetarysystems.components.TagComponent
import se.exuvo.aurora.planetarysystems.components.ThrustComponent
import se.exuvo.aurora.planetarysystems.components.TintComponent
import se.exuvo.aurora.planetarysystems.components.VelocityComponent
import se.exuvo.aurora.planetarysystems.systems.GroupSystem
import se.exuvo.aurora.planetarysystems.systems.MovementSystem
import se.exuvo.aurora.planetarysystems.systems.OrbitSystem
import se.exuvo.aurora.planetarysystems.systems.RenderSystem
import se.exuvo.aurora.planetarysystems.systems.ShipSystem
import se.exuvo.aurora.planetarysystems.systems.SolarIrradianceSystem
import se.exuvo.aurora.planetarysystems.systems.TagSystem
import se.exuvo.aurora.utils.DummyReentrantReadWriteLock
import se.exuvo.aurora.utils.Vector2L
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write



class Empire(val initName: String, val initFunds: Double = 0.0) : Entity()/*, EntityListener */{

    val log = Logger.getLogger(this.javaClass)
    val lock = ReentrantReadWriteLock()
    val engine = PooledEngine()

    val name:String = initName
    val colonies:List<Entity> = mutableListOf()

    fun init() {
        //engine.addEntityListener(this)
        add(EmpireFundsComponent(initFunds))
        this.name = initName;
    }

}