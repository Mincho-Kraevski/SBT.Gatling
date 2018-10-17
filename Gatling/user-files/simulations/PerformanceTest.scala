import com.typesafe.config._
import io.gatling.core.Predef._
import io.gatling.core.feeder.SourceFeederBuilder
import io.gatling.core.structure.{ChainBuilder, ScenarioBuilder}
import io.gatling.http.Predef._
import io.gatling.http.protocol.HttpProtocolBuilder

import scala.concurrent.duration._
import scala.util.Try

/* CALCULATE BET */
object CalculateBets {
  val resourceUrl = "/betslip/calculateBets"

  val calculateBets: ChainBuilder = exec(http("Calculate Bets")
    .post(resourceUrl)
    .body(RawFileBody("calculate-bets-request.json"))
    .headers(Map(
      "CLAIM-PlayerId" -> "${PlayerId}",
      "CLAIM-SiteId" -> "${SiteId}",
      "CLAIM-exp" -> "${Expiration}"))
  )
    .pause(1)
}

/* PLACE BETS */
object PlaceBets {
  val resourceUrl = "/betting/placeBets"

  val placeBets: ChainBuilder = exec(http("Place Bets")
    .post(resourceUrl)
    .body(RawFileBody("place-bets-request.json"))
    .headers(Map(
      "CLAIM-PlayerId" -> "${PlayerId}",
      "CLAIM-SiteId" -> "${SiteId}",
      "CLAIM-exp" -> "${Expiration}")))
    .pause(1)
}

/* TEST APP */
/*object TestApp {
  def main(args: Array[String]): Unit = {

  }
}*/

class PerformanceTest extends Simulation {
  /* Settings */
  val configurationSettingsEntry = "settings"
  val settings: Config = ConfigFactory.load().getConfig(configurationSettingsEntry)
  val baseUrl: String = settings.getString("baseUrl")
  val rampRatio: Double = settings.getDouble("rampRatio")
  val httpProtocol: HttpProtocolBuilder = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json, text/plain, */*")
    .doNotTrackHeader("1")
    .acceptLanguageHeader("en")
    .acceptEncodingHeader("gzip, deflate")
    .userAgentHeader("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.92 Safari/537.36")
    .contentTypeHeader("application/json")

  /* Environment Variables (JAVA_OPTS) */
  /* Scenario Mode */
  val a: String = System.getProperty("scenario")
  print("well..")
  print(a)

  val scenarioMode: Int = int.tryParse(System.getProperty("scenario"), 0) // -Dscenario

  /* Calculate Bets Ratio */
  val requestRatio: String = System.getProperty("requestRatio") // -DrequestRatio
  var calculateBetsRatio: Int = int.tryParse(requestRatio, 1)
  var placeBetsRatio: Int = 1

  /* Users */
  val concurrentUsers: Int = int.tryParse(System.getProperty("concurrentUsers"), 1) // -DconcurrentUsers

  /* Simulation Duration */
  val durationStr: String = System.getProperty("duration") // -Dduration
  val durationInt: Int = int.tryParse(durationStr, 1)
  val overallDuration: FiniteDuration = Duration.apply(durationStr + " minutes").asInstanceOf[FiniteDuration]
  val rampPeriod: FiniteDuration = simulationDuration.getRampPeriod(overallDuration, rampRatio).asInstanceOf[FiniteDuration]
  val simulationPeriod: FiniteDuration = overallDuration - rampPeriod

  /* FEEDERS */
  /* Request Headers */
  val requestHeadersFeederFile = "request-headers.json"
  val requestHeadersFeeder: SourceFeederBuilder[Any] = jsonFile(requestHeadersFeederFile).circular

  /* Scenario */
  var scn: ScenarioBuilder = scenario("default")

  scenarioMode match {
    case 1 => scn = scenario("CalculateBets")
      .exec(
        feed(requestHeadersFeeder)
          .repeat(calculateBetsRatio) {
            CalculateBets.calculateBets
          })

    case 2 => scn = scenario("PlaceBets")
      .exec(
        feed(requestHeadersFeeder)
          .repeat(placeBetsRatio) {
            PlaceBets.placeBets
          })

    case 0 | _ => scn = scenario("CalculateAndPlaceBets")
      .exec(feed(requestHeadersFeeder)
        .repeat(calculateBetsRatio) {
          CalculateBets.calculateBets
        }
        .repeat(placeBetsRatio) {
          PlaceBets.placeBets
        })
  }

  /* Users Injection Mode */
  setUp(
    scn.inject(
      rampConcurrentUsers(0) to concurrentUsers during rampPeriod,
      constantConcurrentUsers(concurrentUsers) during simulationPeriod)
      .protocols(httpProtocol))
    .maxDuration(overallDuration)
}

object simulationDuration {
  def getRampPeriod(duration: Duration, rampRatio: Double): Duration = {
    val length: Double = duration.length * rampRatio
    Duration.apply(length, duration.unit)
  }
}

object int {
  def tryParse(s: String, default: Int = 0): Int = Try(s.toInt).toOption.getOrElse(default)
}