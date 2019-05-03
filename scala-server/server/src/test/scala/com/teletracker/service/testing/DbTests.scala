package com.teletracker.service.testing

import com.teletracker.service.db.UsersDbAccess
import com.teletracker.service.db.model.{User, UserRow}
import com.teletracker.service.testing.framework.BaseSpec
import scala.concurrent.Await
import scala.concurrent.duration._

class DbTests extends BaseSpec {
  "DB" should "turn on" in {
    val access = injector.getInstance(classOf[UsersDbAccess])
    val userId = Await.result(access.newUser("Christian", "chrisbenincasa", "test@test.com", "password"), Duration.Inf)

    val result = Await.result(access.findUserAndLists(userId), Duration.Inf).head

    inside(result) {
      case User(`userId`, "Christian", "chrisbenincasa", "test@test.com", _, _, _, _, _) => succeed
    }
  }
}
