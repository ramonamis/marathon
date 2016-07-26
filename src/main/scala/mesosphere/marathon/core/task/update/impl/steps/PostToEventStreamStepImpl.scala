package mesosphere.marathon.core.task.update.impl.steps

import akka.event.EventStream
import com.google.inject.Inject
import mesosphere.marathon.core.base.Clock
import mesosphere.marathon.core.task.bus.MarathonTaskStatus.{ Terminal, WithMesosStatus }
import mesosphere.marathon.core.task.bus.TaskChangeObservables.TaskChanged
import mesosphere.marathon.core.task.update.TaskUpdateStep
import mesosphere.marathon.core.task.{ EffectiveTaskStateChange, Task, TaskStateChange, TaskStateOp }
import mesosphere.marathon.core.event.MesosStatusUpdateEvent
import mesosphere.marathon.state.Timestamp
import org.apache.mesos.Protos.TaskStatus
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.collection.immutable.Seq

/**
  * Post this update to the internal event stream.
  */
class PostToEventStreamStepImpl @Inject() (eventBus: EventStream, clock: Clock) extends TaskUpdateStep {

  private[this] val log = LoggerFactory.getLogger(getClass)

  override def name: String = "postTaskStatusEvent"

  override def processUpdate(taskChanged: TaskChanged): Future[_] = {
    import TaskStateOp.MesosUpdate
    val taskState = inferTaskState(taskChanged)

    taskChanged match {
      // the task was updated or expunged due to a MesosStatusUpdate
      // In this case, we're interested in the mesosStatus
      case TaskChanged(MesosUpdate(_, WithMesosStatus(status), now), EffectiveTaskStateChange(task)) =>
        postEvent(clock.now(), taskState, Some(status), task)

      // The task was otherwise either expunged or updated.
      // We'll use the task's mesos status in this case
      case TaskChanged(_, EffectiveTaskStateChange(task)) =>
        postEvent(clock.now(), taskState, task.mesosStatus, task)

      case _ =>
        log.debug("Ignoring noop for {}", taskChanged.taskId)
    }

    Future.successful(())
  }

  private[this] def inferTaskState(taskChanged: TaskChanged): String = {
    (taskChanged.stateOp, taskChanged.stateChange) match {
      // TODO: A terminal MesosStatusUpdate for a resident transitions to state Reserved
      case (TaskStateOp.MesosUpdate(_, Terminal(status), _), TaskStateChange.Update(task, oldState)) =>
        status.mesosStatus.fold(MesosStatusUpdateEvent.OtherTerminalState)(_.getState.toString)
      case (TaskStateOp.MesosUpdate(_, WithMesosStatus(mesosStatus), _), _) => mesosStatus.getState.toString
      case (_, TaskStateChange.Expunge(task)) => MesosStatusUpdateEvent.OtherTerminalState
      case (_, TaskStateChange.Update(newState, maybeOldState)) => MesosStatusUpdateEvent.Created
    }
  }

  private[this] def postEvent(
    timestamp: Timestamp,
    taskStatus: String,
    maybeStatus: Option[TaskStatus],
    task: Task): Unit = {

    val taskId = task.taskId
    // TODO: Timestamp(0) is not good ...
    val version = task.launched.fold(Timestamp(0))(_.runSpecVersion).toString
    val slaveId = maybeStatus.fold("n/a")(_.getSlaveId.getValue)
    val message = maybeStatus.fold("")(status => if (status.hasMessage) status.getMessage else "")
    val host = task.agentInfo.host
    val ipAddresses = maybeStatus.flatMap(status => Task.MesosStatus.ipAddresses(status))
    val ports = task.launched.fold(Seq.empty[Int])(_.hostPorts)

    log.info("Sending event notification for {} of app [{}]: {}", taskId, taskId.runSpecId, taskStatus)
    eventBus.publish(
      MesosStatusUpdateEvent(
        slaveId,
        taskId,
        taskStatus,
        message,
        appId = taskId.runSpecId,
        host,
        ipAddresses,
        ports = ports,
        version = version,
        timestamp = timestamp.toString
      )
    )
  }

}
