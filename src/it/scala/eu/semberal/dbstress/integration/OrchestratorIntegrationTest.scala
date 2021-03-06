package eu.semberal.dbstress.integration

import java.io.{File, FilenameFilter, InputStreamReader}
import java.lang.System.currentTimeMillis
import java.nio.file.Files.createTempDirectory

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import eu.semberal.dbstress.Orchestrator
import eu.semberal.dbstress.config.ConfigParser.parseConfigurationYaml
import org.scalatest.{BeforeAndAfterAll, Matchers, FlatSpecLike}
import play.api.libs.json.{JsArray, JsNumber, JsObject, Json}
import resource.managed

import scala.concurrent.duration.DurationLong
import scala.io.Source

class OrchestratorIntegrationTest
  extends TestKit(ActorSystem())
  with FlatSpecLike
  with Matchers
  with ImplicitSender
  with BeforeAndAfterAll {

  override protected def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  def withTempDir(testCode: File => Unit): Unit = {
    val file = createTempDirectory(s"dbstress_OrchestratorTest_${currentTimeMillis()}_").toFile
    try {
      testCode(file)
    } finally {
      //      file.deleteRecursively() match {
      //        case Left(msg) => logger.warn(s"Unable to delete temporary test directory ${file.getAbsolutePath}: $msg")
      //        case _ =>
      //      }
    }
  }

  "Orchestrator" should "successfully launch the application and check results" in withTempDir { tmpDir =>
    val reader = new InputStreamReader(getClass.getClassLoader.getResourceAsStream("config1.yaml"))
    val config = parseConfigurationYaml(reader).right.get
    new Orchestrator(tmpDir).run(config, system)
    system.awaitTermination(20.seconds)

    /* Test generated JSON */
    val jsonFiles = tmpDir.listFiles(new FilenameFilter {
      override def accept(dir: File, name: String): Boolean = name.endsWith(".json")
    })
    jsonFiles should have size 1

    managed(Source.fromFile(jsonFiles.head)) foreach { source =>
      val json = Json.parse(source.mkString).asInstanceOf[JsObject]
      val unitResults = (json \ "scenarioResult" \ "unitResults").asInstanceOf[JsArray].value.map(_.as[JsObject])
      unitResults should have size 6
      (json \\ "configuration") should have size 6
      (json \\ "unitSummary") should have size 6
      (json \\ "connectionInit") should have size 60

      val m = unitResults.map(x =>
        (x \ "configuration" \ "name").as[String] -> (x \ "unitSummary").as[JsObject]
      ).toMap

      m.get("unit1").get \ "expectedDbCalls" should be(JsNumber(540))
      m.get("unit1").get \ "dbCalls" \ "executed" \ "count" should be(JsNumber(540))
      m.get("unit1").get \ "dbCalls" \ "executed" \ "successful" \ "count" should be(JsNumber(540))

    }

    /* Test generated CSV file*/
    val csvFiles = tmpDir.listFiles(new FilenameFilter {
      override def accept(dir: File, name: String): Boolean = name.endsWith(".csv")
    })

    csvFiles should have size 1

    managed(Source.fromFile(csvFiles.head)) foreach { source =>
      val lines = source.getLines().toList
      lines should have size 7
      lines.tail.foreach { line =>
        line should startWith regex "\"unit[1-6]{1}\"".r
      }
    }
  }
}
