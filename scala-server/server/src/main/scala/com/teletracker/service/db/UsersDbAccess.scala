package com.teletracker.service.db

import com.teletracker.service.auth.PasswordHash
import com.teletracker.service.auth.jwt.JwtVendor
import com.teletracker.service.controllers.ListFilters
import com.teletracker.service.db.model.{
  Events,
  Things,
  Tokens,
  TrackedListThings,
  TrackedLists,
  UserCredentials,
  Users,
  _
}
import com.teletracker.service.inject.{DbImplicits, DbProvider}
import com.teletracker.service.util.{Field, FieldSelector}
import io.circe.Json
import javax.inject.Inject
import org.joda.time.DateTime
import java.sql.Timestamp
import scala.concurrent.{ExecutionContext, Future}

class UsersDbAccess @Inject()(
  val provider: DbProvider,
  val users: Users,
  val userNetworkPreferences: UserNetworkPreferences,
  val userCredentials: UserCredentials,
  val trackedLists: TrackedLists,
  val trackedListThings: TrackedListThings,
  val things: Things,
  val events: Events,
  val tokens: Tokens,
  val networks: Networks,
  val userThingTags: UserThingTags,
  dynamicListBuilder: DynamicListBuilder,
  jwtVendor: JwtVendor,
  dbImplicits: DbImplicits
)(implicit executionContext: ExecutionContext)
    extends DbAccess {
  import dbImplicits._
  import provider.driver.api._

  implicit private class UserExtensions[C[_]](
    q: Query[Users#UsersTable, UserRow, C]) {
    def withCredentials = q.join(userCredentials.query).on(_.id === _.userId)
  }

  def findById(id: Int): Future[Option[UserRow]] = {
    run {
      users.query.filter(_.id === id).result.headOption
    }
  }

  def findByEmail(email: String): Future[Option[UserRow]] = {
    run {
      users.query.filter(_.email === email).result.headOption
    }
  }

  def createUserAndToken(
    name: String,
    username: String,
    email: String,
    password: String
  ): Future[(Int, String)] = {
    for {
      userId <- newUser(name, username, email, password)
      token <- vendToken(email)
    } yield {
      (userId, token)
    }
  }

  def newUser(
    name: String,
    username: String,
    email: String,
    password: String
  )(implicit executionContext: ExecutionContext
  ): Future[Int] = {
    val now = System.currentTimeMillis()
    val timestamp = new java.sql.Timestamp(now)
    val hashed = PasswordHash.createHash(password)
    val user = UserRow(
      None,
      name,
      username,
      email,
      hashed,
      timestamp,
      timestamp,
      preferences = None
    )

    val query = (users.query returning users.query.map(_.id)) += user

    run {
      for {
        userId <- query
        _ <- (trackedLists.query returning trackedLists.query.map(_.id)) +=
          TrackedListRow(
            None,
            "Default List",
            isDefault = true,
            isPublic = false,
            userId
          )

        _ <- (trackedLists.query returning trackedLists.query.map(_.id)) +=
          TrackedListRow(
            None,
            "Watched",
            isDefault = true,
            isPublic = false,
            userId,
            isDynamic = true,
            Some(DynamicListRules.watched)
          )
      } yield userId
    }
  }

  def vendToken(email: String): Future[String] = {
    findByEmail(email).flatMap {
      case None => throw new IllegalArgumentException
      case Some(user) =>
        revokeAllTokens(user.id.get).flatMap(_ => {
          val token = jwtVendor.vend(email)
          val now = DateTime.now()
          val insert = tokens.query += TokenRow(
            None,
            user.id.get,
            token,
            now,
            now,
            None
          )
          run(insert).map(_ => token)
        })
    }
  }

  def revokeAllTokens(userId: Int): Future[Int] = {
    run {
      tokens.query
        .filter(t => t.userId === userId && t.revokedAt.isEmpty)
        .map(_.revokedAt)
        .update(Some(DateTime.now()))
    }
  }

  def revokeToken(
    userId: Int,
    token: String
  ): Future[Int] = {
    run {
      tokens.query
        .filter(t => t.userId === userId && t.token === token)
        .map(_.revokedAt)
        .update(Some(DateTime.now()))
    }
  }

  def getToken(token: String): Future[Option[TokenRow]] = {
    run {
      tokens.query.filter(_.token === token).result.headOption
    }
  }

  private def findUserAndListsQuery(userId: Int): Query[
    (
      (
        (users.UsersTable, Rep[Option[trackedLists.TrackedListsTable]]),
        Rep[Option[trackedListThings.TrackedListThingsTable]]
      ),
      Rep[Option[things.ThingsTableRaw]]
    ),
    (
      ((UserRow, Option[TrackedListRow]), Option[TrackedListThing]),
      Option[ThingRaw]
    ),
    Seq
  ] = {
    users.query.filter(_.id === userId) joinLeft
      trackedLists.query.filter(!_.isDynamic) on (_.id === _.userId) joinLeft
      trackedListThings.query on (_._2.map(_.id) === _.listId) joinLeft
      things.rawQuery on (_._2.map(_.thingId) === _.id)
  }

  def findListsForUser(userId: Int): Future[Seq[TrackedListRow]] = {
    run {
      trackedLists.query.filter(_.userId === userId).result
    }
  }

  def findUserAndLists(
    userId: Int,
    selectFields: Option[List[Field]] = None
  ): Future[Option[User]] = {
    val networkPrefsFut = run {
      (userNetworkPreferences.query.filter(_.userId === userId) joinLeft
        networks.query on (_.networkId === _.id)).result
    }

    val userAndListsFut = run {
      (for {
        (((user, list), _), thing) <- findUserAndListsQuery(userId)
      } yield {
        val meta =
          if (selectFields.isDefined) thing.flatMap(_.metadata)
          else Rep.None[Json]
        (
          user,
          list,
          thing.map(_.id),
          thing.map(_.name),
          thing.map(_.`type`),
          meta
        )
      }).result
    }.map(_.map {
      case (
          user,
          optList,
          thingIdOpt,
          thingNameOpt,
          thingTypeOpt,
          thingMetadata
          ) =>
        val newMeta = (for {
          metadata <- thingMetadata
          fields <- selectFields
        } yield {
          FieldSelector.filter(metadata, fields ::: defaultFields)
        }).orElse(thingMetadata)

        val thing = thingIdOpt.map(id => {
          val partialThing =
            PartialThing(Some(id), thingNameOpt, `type` = thingTypeOpt)
          newMeta.map(partialThing.withRawMetadata).getOrElse(partialThing)
        })

        (user, optList, thing)
    })

    val dynamicListsFut = {
      run {
        trackedLists.query
          .filter(tl => tl.userId === userId && tl.isDynamic)
          .result
          .flatMap(lists => {
            val actionsByList = lists.map(
              list => dynamicListBuilder.buildList(userId, list).map(list -> _)
            )

            DBIO.sequence(actionsByList)
          })
      }.map(_.map {
        case (list, things) =>
          val thingsWithMeta = things
            .map {
              case thing if selectFields.isEmpty => thing.copy(metadata = None)
              case thing if thing.metadata.isDefined =>
                val newMeta = FieldSelector.filter(
                  thing.metadata.get,
                  selectFields.get ::: defaultFields
                )
                thing.copy(metadata = Some(newMeta))
              case thing => thing
            }
            .map(_.asPartial)

          list.toFull.withThings(thingsWithMeta.toList)
      })
    }

    for {
      userAndLists <- userAndListsFut
      networkPrefs <- networkPrefsFut
      dynamicLists <- dynamicListsFut
    } yield {
      userAndLists.headOption
        .map(_._1)
        .map(user => {
          val lists = userAndLists
            .collect {
              case (_, Some(list), thingOpt) => (list, thingOpt)
            }
            .groupBy(_._1)
            .map {
              case (list, matches) =>
                list.toFull
                  .withThings(matches.flatMap(_._2).toList)
            }

          user.toFull
            .withNetworks(networkPrefs.flatMap(_._2).toList)
            .withLists((lists ++ dynamicLists).toList.sortBy(_.id))
        })
    }
  }

  def findUserAndList(
    userId: Int,
    listId: Int
  ): Future[Seq[(UserRow, TrackedListRow)]] = {
    run {
      trackedLists.query
        .filter(tl => tl.userId === userId && tl.id === listId)
        .take(1)
        .flatMap(list => {
          list.userId_fk.map(user => user -> list)
        })
        .result
    }
  }

  def findDefaultListForUser(userId: Int): Future[Option[TrackedListRow]] = {
    run {
      trackedLists.query
        .filter(tl => tl.userId === userId && tl.isDefault === true)
        .take(1)
        .result
        .headOption
    }
  }

  def updateUser(updatedUser: User): Future[Unit] = {
    val userUpdateQuery = users.query
      .filter(_.id === updatedUser.id)
      .map(u => {
        (u.name, u.username, u.lastUpdatedAt, u.preferences)
      })
      .update(
        (
          updatedUser.name,
          updatedUser.username,
          new Timestamp(System.currentTimeMillis()),
          Some(updatedUser.userPreferences)
        )
      )
      .map(_ => {})

    val updateNetworksQuery = userNetworkPreferences.query
      .filter(_.userId === updatedUser.id)
      .result
      .flatMap(prefs => {
        val networkIds = updatedUser.networkSubscriptions.flatMap(_.id).toSet
        val existingNetworkIds = prefs.map(_.networkId).toSet

        val toDelete = prefs.filter(pref => !networkIds(pref.networkId))
        val toAdd = updatedUser.networkSubscriptions
          .filter(net => net.id.isDefined && !existingNetworkIds(net.id.get))
          .map(network => {
            UserNetworkPreference(-1, updatedUser.id, network.id.get)
          })

        val deleteAction = if (toDelete.nonEmpty) {
          userNetworkPreferences.query
            .filter(_.id inSetBind toDelete.map(_.id))
            .delete
        } else {
          DBIO.successful(0)
        }

        val addAction = if (toAdd.nonEmpty) {
          userNetworkPreferences.query ++= toAdd
        } else {
          DBIO.successful(None)
        }

        DBIO.seq(
          deleteAction,
          addAction
        )
      })

    Future
      .sequence(
        run(userUpdateQuery) ::
          run(updateNetworksQuery) ::
          Nil
      )
      .map(_ => {})
  }

  def insertList(
    userId: Int,
    name: String
  ): Future[TrackedListRow] = {
    run {
      (trackedLists.query returning
        trackedLists.query.map(_.id) into
        ((l, id) => l.copy(id = Some(id)))) += TrackedListRow(
        None,
        name,
        isDefault = false,
        isPublic = false,
        userId
      )
    }
  }

  private val defaultFields = List(Field("id"))

  def findList(
    userId: Int,
    listId: Int,
    selectFields: Option[List[Field]],
    filters: Option[ListFilters] = None,
    isDynamicHint: Option[Boolean] = None
  ): Future[Option[(TrackedListRow, Seq[ThingRaw])]] = {
    val thingsQuery = filters.flatMap(_.itemTypes) match {
      case Some(types) => things.rawQuery.filter(_.`type` inSetBind types)
      case None        => things.rawQuery
    }

    val listAndThingsFut = isDynamicHint match {
      case Some(false) =>
        val listQuery = trackedLists.query.filter(
          tl => tl.userId === userId && tl.id === listId
        )

        val fullQuery = listQuery joinLeft
          trackedListThings.query on (_.id === _.listId) joinLeft
          thingsQuery on (_._2.map(_.thingId) === _.id)

        val listAndThingsQuery = fullQuery.map {
          case ((list, _), things) => list -> things
        }

        run(listAndThingsQuery.result).map {
          case listAndThings if listAndThings.isEmpty => None
          case listAndThings =>
            val (list, _) = listAndThings.head
            Some(list -> listAndThings.flatMap(_._2))
        }

      case _ =>
        run {
          trackedLists.query
            .filter(tl => tl.userId === userId && tl.id === listId)
            .result
            .headOption
        }.flatMap {
          case None =>
            Future.successful(None)

          case Some(list) if list.isDynamic =>
            run(
              dynamicListBuilder
                .buildList(userId, list)
                .map(list -> _)
                .map(Some(_))
            )

          case Some(list) =>
            val listThingsQuery = trackedListThings.query.filter(
              _.listId === list.id
            ) joinLeft
              thingsQuery on (_.thingId === _.id)

            run(
              listThingsQuery.result
                .map(_.flatMap(_._2))
                .map(list -> _)
                .map(Some(_))
            )
        }
    }

    listAndThingsFut.map {
      case Some((list, things)) if selectFields.isDefined =>
        val newThings = things.map(thing => {
          thing.metadata match {
            case Some(metadata) =>
              thing.copy(
                metadata = Some(
                  FieldSelector
                    .filter(metadata, selectFields.get ::: defaultFields)
                )
              )

            case None => thing
          }
        })

        Some(list -> newThings)

      case listAndThings => listAndThings
    }
  }

  def addThingToList(
    listId: Int,
    thingId: Int
  ): Future[Int] = {
    run {
      trackedListThings.query.insertOrUpdate(TrackedListThing(listId, thingId))
    }
  }

  def removeThingFromLists(
    listIds: Set[Int],
    thingId: Int
  ): Future[Int] = {
    if (listIds.isEmpty) {
      Future.successful(0)
    } else {
      run {
        trackedListThings.query
          .filter(_.listId inSetBind listIds)
          .filter(_.thingId === thingId)
          .delete
      }
    }
  }

  def getUserEvents(userId: Int): Future[Seq[EventWithTarget]] = {
    run {
      (for {
        (ev, thing) <- events.query
          .filter(_.userId === userId)
          .sortBy(_.timestamp.desc) joinLeft
          things.query on (
          (
            ev,
            t
          ) => ev.targetEntityId === t.id.asColumnOf[String]
        )
      } yield {
        (ev, thing.map(_.id), thing.map(_.name))
      }).result.map(_.map {
        case (event, tid @ Some(_), tname @ Some(_)) =>
          event.withTarget(PartialThing(tid, tname))
        case (event, _, _) =>
          EventWithTarget(event, None)
      })
    }
  }

  def addUserEvent(event: Event): Future[Int] = {
    run {
      (events.query returning events.query.map(_.id)) += event
    }
  }

  def insertOrUpdateAction(
    userId: Int,
    thingId: Int,
    action: UserThingTagType,
    value: Option[Double]
  ): Future[Int] = {
    run {
      userThingTags.query
        .filter(utt => {
          utt.userId === userId && utt.thingId === thingId && utt.action === action
        })
        .take(1)
        .result
        .headOption
        .flatMap {
          case Some(existing) =>
            userThingTags.query.update(existing.copy(value = value))

          case None =>
            userThingTags.query += UserThingTag(
              -1,
              userId,
              thingId,
              action,
              value
            )
        }
    }
  }

  def removeAction(
    userId: Int,
    thingId: Int,
    action: UserThingTagType
  ): Future[Int] = {
    run {
      userThingTags.query
        .filter(utt => {
          utt.userId === userId && utt.thingId === thingId && utt.action === action
        })
        .delete
    }
  }
}

case class SlickDBNoAvailableThreadsException(message: String)
    extends Exception(message)
