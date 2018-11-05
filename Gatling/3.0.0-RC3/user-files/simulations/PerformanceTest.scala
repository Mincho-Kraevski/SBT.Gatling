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
  val typeOfRequest: String = System.getProperty("bettype") // -Dbettype
  var fileToExecute: String = "none"

  typeOfRequest match {
    )
    case "Single" => fileToExecute = "calculate-bets-request.json"
    case "Combo" => fileToExecute = "calculate-bets-request-combo.json"
    case "System3" => fileToExecute = "calculate-bets-request-system-3.json"
    case "System7" => fileToExecute = "calculate-bets-request-system-7.json"
    case "System14" => fileToExecute = "calculate-bets-request-system-14.json"
    case "System20" => fileToExecute = "calculate-bets-request-system-20.json"
    case "Cast" => fileToExecute = "calculate-bets-request-cast.json"
  }

  val calculateBets: ChainBuilder = exec(http(s"Calculate Bets {typeOfRequest}")
    .post(resourceUrl)
    .body(RawFileBody(fileToExecute))
    .headers(Map(
      "CLAIM-PlayerId" -> "${PlayerId}",
      "CLAIM-SiteId" -> "${SiteId}",
      "CLAIM-exp" -> "${Expiration}"))
  ).pause(1)
}

/* PLACE BETS */
object PlaceBets {
  val resourceUrl = "/betting/placeBets"
  val typeOfRequest: String = System.getProperty("bettype") // -Dbettype
  var fileToExecute: String = "none"

  typeOfRequest match {
    case "Single" => fileToExecute = "place-bets-request.json"
    case "Combo" => fileToExecute = "place-bets-request-combo.json"
    case "System3" => fileToExecute = "place-bets-request-system-3.json"
    case "System7" => fileToExecute = "place-bets-request-system-7.json"
    case "System14" => fileToExecute = "place-bets-request-system-14.json"
    case "System20" => fileToExecute = "place-bets-request-system-20.json"
    case "Cast" => fileToExecute = "place-bets-request-cast.json"
  }

  val placeBets: ChainBuilder = exec(http(s"Place Bets {typeOfRequest}")
    .post(resourceUrl)
    .body(RawFileBody(fileToExecute))
    .headers(Map(
      "CLAIM-PlayerId" -> "${PlayerId}",
      "CLAIM-SiteId" -> "${SiteId}",
      "CLAIM-exp" -> "${Expiration}"))
  ).pause(1)
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
  val scenarioMode: String = System.getProperty("scenario") // -Dscenario

  /* Calculate Bets Ratio */
  val requestRatio: String = System.getProperty("requestRatio") // -DrequestRatio
  val ratio: Array[java.lang.String] = requestRatio.split(":").map(_.trim)
  var calculateBetsRatio: Int = int.tryParse(ratio(0), 1)
  var placeBetsRatio: Int = int.tryParse(ratio(1), 1)

  /* Users */
  val concurrentUsers: Int = int.tryParse(System.getProperty("concurrentUsers"), 1) // -DconcurrentUsers

  /* Simulation Duration */
  val durationStr: String = System.getProperty("duration") // -Dduration
  val overallDuration: FiniteDuration = Duration.apply(durationStr).asInstanceOf[FiniteDuration]
  val rampPeriod: FiniteDuration = simulationDuration.getRampPeriod(overallDuration, rampRatio).asInstanceOf[FiniteDuration]
  val simulationPeriod: FiniteDuration = overallDuration - rampPeriod

  /* FEEDERS */
  /* Request Headers */
  val requestHeadersFeederFile = "request-headers.json"
  val requestHeadersFeeder: SourceFeederBuilder[Any] = jsonFile(requestHeadersFeederFile).circular

  /* Scenario */
  var scn: ScenarioBuilder = scenario("default")

  scenarioMode match {
    case "Calculate bets" => scn = scenario("CalculateBets")
      .exec(
        feed(requestHeadersFeeder)
          .repeat(calculateBetsRatio) {
            CalculateBets.calculateBets
          })

    case "Place bets" => scn = scenario("PlaceBets")
      .exec(
        feed(requestHeadersFeeder)
          .repeat(placeBetsRatio) {
            PlaceBets.placeBets
          })

    case "Calculate and place bets" | _ => scn = scenario("CalculateAndPlaceBets")
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