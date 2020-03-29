package com.teletracker.tasks.elasticsearch

import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import javax.inject.Inject
import org.elasticsearch.action.admin.cluster.repositories.put.PutRepositoryRequest
import org.elasticsearch.client.{RequestOptions, RestHighLevelClient}
import org.elasticsearch.common.settings.Settings

class MakeSnapshotRepository @Inject()(client: RestHighLevelClient)
    extends TeletrackerTaskWithDefaultArgs {
  override protected def runInternal(args: Args): Unit = {
    client
      .snapshot()
      .createRepository(
        new PutRepositoryRequest("manual-snapshots-s3")
          .`type`("s3")
          .settings(
            Settings
              .builder()
              .put("bucket", "teletracker-es-snapshots-us-west-2")
              .put("region", "us-west-2")
              .put("role_arn", "arn:aws:iam::302782651551:role/EsSnapshotRole")
          ),
        RequestOptions.DEFAULT
      )
  }
}
