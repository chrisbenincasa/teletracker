package com.chrisbenincasa.services.teletracker.testing

import com.chrisbenincasa.services.teletracker.db.UsersDbAccess
import com.chrisbenincasa.services.teletracker.db.model.UserRow
import com.chrisbenincasa.services.teletracker.testing.framework.BaseSpec
import scala.concurrent.Await
import scala.concurrent.duration._

class DbTests extends BaseSpec {
  "DB" should "turn on" in {
    val access = injector.getInstance(classOf[UsersDbAccess])
    val userId = Await.result(access.newUser("Christian", "chrisbenincasa", "test@test.com", "password"), Duration.Inf)

    val result = Await.result(access.findUserAndLists(userId), Duration.Inf).head._1

    inside(result) {
      case UserRow(Some(`userId`), "Christian", "chrisbenincasa", "test@test.com", _, _, _) => succeed
    }
  }
}
