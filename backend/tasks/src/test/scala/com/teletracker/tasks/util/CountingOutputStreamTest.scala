package com.teletracker.tasks.util

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import java.io.OutputStream

class CountingOutputStreamTest
    extends AnyFlatSpec
    with Matchers
    with ScalaCheckPropertyChecks {
  it should "count bytes" in {
    forAll { (arr: Array[Byte]) =>
      val os = new CountingOutputStream(NullOutputStream)
      os.write(arr)
      os.getCount shouldEqual arr.length
    }
  }

  it should "count single bytes" in {
    forAll { (byte: Byte) =>
      val os = new CountingOutputStream(NullOutputStream)
      os.write(byte)
      os.getCount shouldEqual 1
    }
  }
}

object NullOutputStream extends OutputStream {
  override def write(b: Int): Unit = {}
}
