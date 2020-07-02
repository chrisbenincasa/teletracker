package com.teletracker.common.db.dynamo

import com.teletracker.common.db.dynamo.model.{
  MetadataFields,
  MetadataType,
  StoredGenre,
  StoredGenreReference,
  StoredNetwork,
  StoredNetworkReference,
  StoredUserPreferences
}
import com.teletracker.common.db.dynamo.util.DynamoQueryUtil
import com.teletracker.common.db.dynamo.util.syntax._
import com.teletracker.common.db.model.ExternalSource
import javax.inject.Inject
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.{
  GetItemRequest,
  PutItemRequest,
  ResourceNotFoundException
}
import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.concurrent.{ExecutionContext, Future}

object MetadataDbAccess {
  // TODO: Config
  final private val TableName = "teletracker.qa.metadata"
}

class MetadataDbAccess @Inject()(
  dynamo: DynamoDbAsyncClient
)(implicit executionContext: ExecutionContext) {
  import MetadataDbAccess._

  def saveNetwork(storedNetwork: StoredNetwork): Future[StoredNetwork] = {
    dynamo
      .putItem(
        PutItemRequest
          .builder()
          .tableName(TableName)
          .item(storedNetwork.toDynamoItem)
          .build()
      )
      .toScala
      .map(_ => {
        storedNetwork
      })
  }

  def getNetworkById(id: Int): Future[Option[StoredNetwork]] = {
    dynamo
      .getItem(
        GetItemRequest
          .builder()
          .tableName(TableName)
          .key(StoredNetwork.getNetworkKey(id))
          .build()
      )
      .toScala
      .map(response => {
        if (response.item().isEmpty) {
          None
        } else {
          Some(StoredNetwork.fromRow(response.item()))
        }
      })
      .recover {
        case _: ResourceNotFoundException => None
      }
  }

  def getAllNetworks(): Future[List[StoredNetwork]] = {
    DynamoQueryUtil
      .queryLoop(
        dynamo,
        TableName,
        req =>
          req
            .keyConditionExpression("#t = :v1")
            .expressionAttributeNames(
              Map(
                "#t" -> MetadataFields.TypeField
              ).asJava
            )
            .expressionAttributeValues(
              Map(
                ":v1" -> MetadataType.NetworkType.toAttributeValue
              ).asJava
            )
      )
      .map(networks => {
        networks.map(StoredNetwork.fromRow)
      })
  }

  def saveNetworkReference(storedNetworkReference: StoredNetworkReference) = {
    dynamo
      .putItem(
        PutItemRequest
          .builder()
          .tableName(TableName)
          .item(storedNetworkReference.toDynamoItem)
          .build()
      )
      .toScala
      .map(_ => {
        storedNetworkReference
      })
  }

  def getNetworkByReference(
    externalSource: ExternalSource,
    externalId: String
  ) = {
    dynamo
      .getItem(
        GetItemRequest
          .builder()
          .tableName(TableName)
          .key(StoredNetworkReference.getKey(externalSource, externalId))
          .build()
      )
      .toScala
      .flatMap(response => {
        if (response.item.isEmpty) {
          Future.successful(None)
        } else {
          val reference = StoredNetworkReference.fromRow(response.item())

          getNetworkById(reference.networkId).map {
            case None          => None
            case Some(network) => Some(reference -> network)
          }
        }
      })
      .recover {
        case _: ResourceNotFoundException => None
      }
  }

  def saveGenre(storedGenre: StoredGenre): Future[StoredGenre] = {
    dynamo
      .putItem(
        PutItemRequest
          .builder()
          .tableName(TableName)
          .item(storedGenre.toDynamoItem)
          .build()
      )
      .toScala
      .map(_ => {
        storedGenre
      })
  }

  def saveGenreReference(storedGenreReference: StoredGenreReference) = {
    dynamo
      .putItem(
        PutItemRequest
          .builder()
          .tableName(TableName)
          .item(storedGenreReference.toDynamoItem)
          .build()
      )
      .toScala
      .map(_ => {
        storedGenreReference
      })
  }

  def getGenreById(id: Int): Future[Option[StoredGenre]] = {
    dynamo
      .getItem(
        GetItemRequest
          .builder()
          .tableName(TableName)
          .key(StoredGenre.getKey(id))
          .build()
      )
      .toScala
      .map(response => {
        if (response.item().isEmpty) {
          None
        } else {
          Some(StoredGenre.fromRow(response.item()))
        }
      })
      .recover {
        case _: ResourceNotFoundException => None
      }
  }

  def getAllGenres(): Future[List[StoredGenre]] = {
    DynamoQueryUtil
      .queryLoop(
        dynamo,
        TableName,
        req =>
          req
            .keyConditionExpression("#t = :v1")
            .expressionAttributeNames(
              Map(
                "#t" -> MetadataFields.TypeField
              ).asJava
            )
            .expressionAttributeValues(
              Map(
                ":v1" -> MetadataType.GenreType.toAttributeValue
              ).asJava
            )
      )
      .map(genres => {
        genres.map(StoredGenre.fromRow)
      })
  }

  def getGenreByReference(
    externalSource: ExternalSource,
    externalId: String
  ) = {
    dynamo
      .getItem(
        GetItemRequest
          .builder()
          .tableName(TableName)
          .key(StoredGenreReference.getKey(externalSource, externalId))
          .build()
      )
      .toScala
      .flatMap(response => {
        if (response.item().isEmpty) {
          Future.successful(None)
        } else {
          val reference = StoredGenreReference.fromRow(response.item())

          getGenreById(reference.genreId).map {
            case None        => None
            case Some(genre) => Some(reference -> genre)
          }
        }
      })
      .recover {
        case _: ResourceNotFoundException => None
      }
  }

  def getAllGenreReferences() = {
    DynamoQueryUtil
      .queryLoop(
        dynamo,
        TableName,
        req =>
          req
            .keyConditionExpression("#t = :v1")
            .expressionAttributeNames(
              Map(
                "#t" -> MetadataFields.TypeField
              ).asJava
            )
            .expressionAttributeValues(
              Map(
                ":v1" -> MetadataType.GenreReferenceType.toAttributeValue
              ).asJava
            )
      )
      .map(genres => {
        genres.map(StoredGenreReference.fromRow)
      })
  }

  def getUserPreferences(userId: String) = {
    dynamo
      .getItem(
        GetItemRequest
          .builder()
          .tableName(TableName)
          .key(StoredUserPreferences.getKey(userId))
          .build()
      )
      .toScala
      .map(response => {
        if (response.item().isEmpty) {
          None
        } else {
          Some(StoredUserPreferences.fromRow(response.item()))
        }
      })
      .recover {
        case _: ResourceNotFoundException => None
      }
  }

  def saveUserPreferences(storedUserPreferences: StoredUserPreferences) = {
    dynamo
      .putItem(
        PutItemRequest
          .builder()
          .tableName(TableName)
          .item(storedUserPreferences.toDynamoItem)
          .build()
      )
      .toScala
      .map(_ => {
        storedUserPreferences
      })
  }
}
