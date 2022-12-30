package part4_techniques

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.dispatch.MessageDispatcher
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout

import java.util.Date
import scala.concurrent.Future

object IntegratingWithExternalServices extends App {
  implicit val system = ActorSystem("IntegratingWithExternalServices")
  implicit val materializer = ActorMaterializer()

  //  import system.dispatcher // not recommended in practice for mapAsync

  implicit val dispatcher: MessageDispatcher = system.dispatchers.lookup("dedicated-dispatcher")

  def genericExtService[A, B](element: A): Future[B] = ???

  // example: simplified PagerDuty
  case class PagerEvent(application: String, description: String, date: Date)

  val eventSource = Source(
    List(
      PagerEvent("AkkaInfra", "Infrastructure broke", new Date()),
      PagerEvent("FastDataPipeline", "Illegal elements in the data pipeline", new Date()),
      PagerEvent("AkkaInfra", "A service stopped responding", new Date()),
      PagerEvent("SuperFrontEnd", "A button doesn't work", new Date())
    )
  )

  object PagerService {
    private val engineers = List("Daniel", "John", "Lady Gaga")
    private val emails = Map(
      "Daniel" -> "daniel@rockthejvm.com",
      "John" -> "john@rockthejvm.com",
      "Lady Gaga" -> "ladygaga@rtjvm.com"
    )

    def processEvent(pagerEvent: PagerEvent): Future[String] = Future {
      val engineerIndex = (pagerEvent.date.toInstant.getEpochSecond / (24 * 3600)) % engineers.length
      val engineer = engineers(engineerIndex.toInt)
      val engineerEmail = emails(engineer)

      println(s"Sending engineer $engineerEmail a high priority notification: $pagerEvent")
      Thread.sleep(1000)
      engineerEmail
    }
  }

  val infraEvents = eventSource.filter(_.application == "AkkaInfra")
  //
  // now we need to send these infra events to our engineers.
  // the problem is that we cannot use regular Flow/map or component
  // because the return type of the service is a Future.
  //
  // therefore, instead of using infraEvents.map, we will use
  // infraEvents.mapAsync.
  //
  val pagedEngineerEmails = infraEvents
    // the following code contains parallelism = 4
    // because it decides how many futures can be
    // executed at the same time.
    // meaning, PagerService.processEvent function.
    // however, the futures are running in parallel,
    // the output stream is always to be in relative order.
    // also if any of the 4 futures fail, the whole stream will fail.
    //
    // guarantees the relative order of elements in the output
    // if you do not want this guarantee, then you can use
    // mapAsyncUnordered
    //
    .mapAsync(parallelism = 4)(event => PagerService.processEvent(event))

  val pagedEmailsSink = Sink.foreach[String](email => println(s"Successfully sent notification to $email"))

  //  pagedEngineerEmails.to(pagedEmailsSink).run()

  class PagerActor extends Actor with ActorLogging {
    private val engineers = List("Daniel", "John", "Lady Gaga")
    private val emails = Map(
      "Daniel" -> "daniel@rockthejvm.com",
      "John" -> "john@rockthejvm.com",
      "Lady Gaga" -> "ladygaga@rtjvm.com"
    )

    def processEvent(pagerEvent: PagerEvent): String = {
      val engineerIndex = (pagerEvent.date.toInstant.getEpochSecond / (24 * 3600)) % engineers.length
      val engineer = engineers(engineerIndex.toInt)
      val engineerEmail = emails(engineer)

      println(s"Sending engineer $engineerEmail a high priority notification: $pagerEvent")
      Thread.sleep(1000)
      engineerEmail
    }

    override def receive: Receive = {
      case pagerEvent: PagerEvent =>
        sender() ! processEvent(pagerEvent)
    }
  }

  import akka.pattern.ask
  import scala.concurrent.duration._

  implicit val timeout = Timeout(3 seconds)
  val pagerActor = system.actorOf(Props[PagerActor], "pagerActor")
  val alternativePagedEngineerEmails = infraEvents.mapAsync(parallelism = 4)(event => (pagerActor ? event).mapTo[String])
  alternativePagedEngineerEmails.to(pagedEmailsSink).run()

  // do not confuse mapAsync with async (ASYNC boundry)

}
