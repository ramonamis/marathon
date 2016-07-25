package mesosphere.marathon.core.task.state

import org.apache.mesos

object MarathonTaskStatusMapping {

  // If we're disconnected at the time of a TASK_LOST event, we will only get the update during
  // a reconciliation. In that case, the specific reason will be shadowed by REASON_RECONCILIATION.
  // Since we don't know the original reason, we need to assume that the task might come back.
  val MightComeBack: Set[mesos.Protos.TaskStatus.Reason] = Set(
    mesos.Protos.TaskStatus.Reason.REASON_RECONCILIATION,
    mesos.Protos.TaskStatus.Reason.REASON_SLAVE_DISCONNECTED,
    mesos.Protos.TaskStatus.Reason.REASON_SLAVE_REMOVED
  )

  val WontComeBack: Set[mesos.Protos.TaskStatus.Reason] = {
    mesos.Protos.TaskStatus.Reason.values().toSet.diff(MightComeBack)
  }

  val Unknown: Set[mesos.Protos.TaskStatus.Reason] = Set(
    mesos.Protos.TaskStatus.Reason.REASON_TASK_UNKNOWN
  )

}