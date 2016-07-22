package mesosphere.marathon.core.storage.repository.impl

import akka.Done
import akka.http.scaladsl.marshalling.Marshaller
import akka.http.scaladsl.unmarshalling.Unmarshaller
import mesosphere.marathon.core.storage.repository.{ FrameworkIdRepository, TaskFailureRepository }
import mesosphere.marathon.core.storage.store.{ IdResolver, PersistenceStore }
import mesosphere.marathon.core.task.Task
import mesosphere.marathon.state.TaskFailure
import mesosphere.util.state.FrameworkId

import scala.concurrent.Future
// scalastyle:off
import mesosphere.marathon.core.storage.repository.{ AppRepository, DeploymentRepository, TaskRepository }
// scalastyle:on
import mesosphere.marathon.state.{ AppDefinition, PathId }
import mesosphere.marathon.upgrade.DeploymentPlan

class AppRepositoryImpl[K, C, S](persistenceStore: PersistenceStore[K, C, S])(implicit
  ir: IdResolver[PathId, AppDefinition, C, K],
  marhaller: Marshaller[AppDefinition, S],
  unmarshaller: Unmarshaller[S, AppDefinition])
    extends PersistenceStoreVersionedRepository[PathId, AppDefinition, K, C, S](
      persistenceStore,
      _.id,
      _.version.toOffsetDateTime)
    with AppRepository

class TaskRepositoryImpl[K, C, S](persistenceStore: PersistenceStore[K, C, S])(implicit
  ir: IdResolver[Task.Id, Task, C, K],
  marshaller: Marshaller[Task, S],
  unmarshaller: Unmarshaller[S, Task])
    extends PersistenceStoreRepository[Task.Id, Task, K, C, S](persistenceStore, _.taskId)
    with TaskRepository

class DeploymentRepositoryImpl[K, C, S](persistenceStore: PersistenceStore[K, C, S])(implicit
  ir: IdResolver[String, DeploymentPlan, C, K],
  marshaller: Marshaller[DeploymentPlan, S],
  unmarshaller: Unmarshaller[S, DeploymentPlan])
    extends PersistenceStoreRepository[String, DeploymentPlan, K, C, S](persistenceStore, _.id)
    with DeploymentRepository

class TaskFailureRepositoryImpl[K, C, S](persistenceStore: PersistenceStore[K, C, S])(
  implicit
  ir: IdResolver[PathId, TaskFailure, C, K],
  marshaller: Marshaller[TaskFailure, S],
  unmarshaller: Unmarshaller[S, TaskFailure]
) extends PersistenceStoreVersionedRepository[PathId, TaskFailure, K, C, S](
  persistenceStore,
  _.appId,
  _.version.toOffsetDateTime) with TaskFailureRepository

class FrameworkIdRepositoryImpl[K, C, S](persistenceStore: PersistenceStore[K, C, S])(
    implicit
    ir: IdResolver[String, FrameworkId, C, K],
    marshaller: Marshaller[FrameworkId, S],
    unmarshaller: Unmarshaller[S, FrameworkId]
) extends FrameworkIdRepository {
  private val ID = "id"
  private val repo = new PersistenceStoreRepository[String, FrameworkId, K, C, S](persistenceStore, _ => ID)
  override def get(): Future[Option[FrameworkId]] = repo.get(ID)
  override def store(v: FrameworkId): Future[Done] = repo.store(v)
  override def delete(): Future[Done] = repo.delete(ID)
}
