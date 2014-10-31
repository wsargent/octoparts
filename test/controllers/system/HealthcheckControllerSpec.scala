package controllers.system

import java.io.IOException

import com.m3.octoparts.cache.RawCache
import org.mockito.Mockito._
import org.scalatest.{ Matchers, FlatSpec, BeforeAndAfterEach }
import org.scalatest.mock.MockitoSugar

import com.m3.octoparts.hystrix.HystrixHealthReporter
import com.m3.octoparts.repository.ConfigsRepository
import com.m3.octoparts.model.config.HttpPartConfig
import org.scalatestplus.play.OneAppPerSuite
import play.api.libs.json.JsValue
import play.api.test.FakeRequest
import play.api.test.Helpers._
import shade.memcached.Codec
import scala.concurrent.Future

class HealthcheckControllerSpec
    extends FlatSpec
    with Matchers
    with MockitoSugar
    with BeforeAndAfterEach
    with JsonCheckSupport
    with OneAppPerSuite {

  val mockConfigs = Seq(mock[HttpPartConfig], mock[HttpPartConfig], mock[HttpPartConfig])
  val configsRepo = mock[ConfigsRepository]
  val hystrixHealthReporter = mock[HystrixHealthReporter]
  val cache = mock[RawCache]

  override def beforeEach() {
    reset(configsRepo, hystrixHealthReporter, cache)

    // Return healthy-looking results by default
    when(configsRepo.findAllConfigs()).thenReturn(Future.successful(mockConfigs))
    when(hystrixHealthReporter.getCommandKeysWithOpenCircuitBreakers).thenReturn(Seq())
    when(cache.get[String](org.mockito.Matchers.any(classOf[String]))(org.mockito.Matchers.any(classOf[Codec[String]])))
      .thenReturn(Future.successful(Some("pong")))
  }

  val controller = new HealthcheckController(configsRepo, hystrixHealthReporter, cache, SingleMemcachedCacheKeyToCheck)

  it should "return a 200 OK response" in {
    status(controller.healthcheck.apply(FakeRequest())) should be(200)
  }

  it should "be GREEN if DB is healthy, there are no open circuits and Memcached is healthy" in {
    checkJson(controller.healthcheck.apply(FakeRequest())) { implicit json =>
      colour should be("GREEN")
      dbOk should be(true)
      hystrixOk should be(true)
      cacheOk should be(true)
    }
  }

  it should "be YELLOW and show DB as not OK if there are no part configs" in {
    when(configsRepo.findAllConfigs()).thenReturn(Future.successful(Seq.empty))

    checkJson(controller.healthcheck.apply(FakeRequest())) { implicit json =>
      colour should be("YELLOW")
      dbOk should be(false)
    }
  }

  it should "be YELLOW and show DB as not OK if DB query throws an exception" in {
    when(configsRepo.findAllConfigs()).thenReturn(Future.failed(new RuntimeException("OH MY GOD!")))

    checkJson(controller.healthcheck.apply(FakeRequest())) { implicit json =>
      colour should be("YELLOW")
      dbOk should be(false)
      (json \ "statuses" \ "db" \ "message").as[String] should include("OH MY GOD!")
    }
  }

  it should "be YELLOW and show Hystrix as not OK if there are any open Hystrix circuits" in {
    when(hystrixHealthReporter.getCommandKeysWithOpenCircuitBreakers).thenReturn(Seq("mrkun", "career"))

    checkJson(controller.healthcheck.apply(FakeRequest())) { implicit json =>
      colour should be("YELLOW")
      hystrixOk should be(false)
    }
  }

  it should "be YELLOW and show Memcached as not OK if the Memcached GET returned a failure" in {
    when(cache.get[String](org.mockito.Matchers.any(classOf[String]))(org.mockito.Matchers.any(classOf[Codec[String]])))
      .thenReturn(Future.failed(new IOException("Memcached is down!")))

    checkJson(controller.healthcheck.apply(FakeRequest())) { implicit json =>
      colour should be("YELLOW")
      cacheOk should be(false)
    }
  }

  def colour(implicit json: JsValue) = (json \ "colour").as[String]
  def dbOk(implicit json: JsValue) = (json \ "statuses" \ "db" \ "ok").as[Boolean]
  def hystrixOk(implicit json: JsValue) = (json \ "statuses" \ "hystrix" \ "ok").as[Boolean]
  def cacheOk(implicit json: JsValue) = (json \ "statuses" \ "memcached" \ "ok").as[Boolean]
}
