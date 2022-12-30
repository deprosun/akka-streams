package part3_graphs

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape}
import akka.stream.scaladsl.{Balance, Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source, Zip}

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.language.postfixOps

object GraphBasics extends App {

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val input = Source(1 to 1000)
  val incrementer: Flow[Int, Int, NotUsed] = Flow[Int].map(x => x + 1) // hard computation
  val multiplier: Flow[Int, Int, NotUsed] = Flow[Int].map(x => x * 10) // hard computation

  //the goal is to run these flow in parallel and merge back the result in tuples
  val output = Sink.foreach[(Int, Int)](println)

  //we need multiple components and need to construct a complex graph
  // with a FAN OUT operator which feeds the input (input) into multiple outputs (incrementer and multiplier)
  // and a FAN IN operator which feeds (incrementer and multiplier) to the (output)

  //step 1 - setting up the fundamentals for the graph
  val graph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] => // builder = MUTABLE data structure
      import GraphDSL.Implicits._ //brings some nice operators into scope

      //step 2 - add necessary components of this graph
      // Broadcast will accept an input of type Int and will duplicate the element and send it foreard to 2 outputs
      val broadcast = builder.add(Broadcast[Int](2)) //fan-out operator

      val zip = builder.add(Zip[Int, Int]) //fan-in operators

      //step 3 - tying up the components
      input ~> broadcast //input "feeds" it to broadcast

      broadcast.out(0) ~> incrementer ~> zip.in0
      broadcast.out(1) ~> multiplier ~> zip.in1

      zip.out ~> output

      //IMPORTANT: from implicit builder uptil we are return the ClosedShape
      // we are mutating the builder and when we return the shape we FREEZE the builder.
      // look at picture firstComplexGraph.png. 14:39 video

      //step 4
      ClosedShape //FREEZE the builder's shape so it becomes immutable and graph can be made

      //returns a shape
    } //graph
  ) //runnable graph

  //  graph.run()

  /**
    * exercise 1: feed a source into two different sinks at the same time (hint: use a broadcast)
    */

  def ex1(): RunnableGraph[NotUsed] = {

    val sink1: Sink[Int, Future[Done]] = Sink.foreach[Int](x => println(2 * x))
    val sink2: Sink[Int, Future[Done]] = Sink.foreach[Int](x => println(4 * x))

    RunnableGraph.fromGraph(
      GraphDSL.create() { implicit builder =>

        import GraphDSL.Implicits._

        //step 2
        val broadcast = builder.add(Broadcast[Int](2))

        //step 3 - tying up the components
        input ~> broadcast
        broadcast ~> sink1 //implicit port numbering
        broadcast ~> sink2

        //        broadcast.out(0) ~> sink1
        //        broadcast.out(1) ~> sink2

        //step 4
        ClosedShape

        //returns a shape
      } //graph
    )
  }

  //  ex1().run()

  /**
    * What the balance graph does is that it takes very different sources
    * emitting elements at very different speeds and it evens out the rate
    * of production of the elements between sources and splits them equally in between
    * sinks.
    *
    * So this is pretty useful in practice if you have different sources and you
    * dont know their rate of production but at the end of the graph you would like to
    * have steady flow of elements, this is the kind of thing you would have to do. (Merge them and balance them out)
    */
  def ex2BalanceGraph(): RunnableGraph[NotUsed] = {

    val fastSource = input.throttle(5, 1 second)
    val slowSource = input.throttle(2, 1 second)

    val sink1 = Sink.fold[Int, Int](0) { (count, _) => {
      println(s"Sink 1 number of elements: $count")
      count + 1
    }
    }

    val sink2 = Sink.fold[Int, Int](0) { (count, _) => {
      println(s"Sink 2 number of elements: $count")
      count + 1
    }
    }

    RunnableGraph.fromGraph(
      GraphDSL.create() { implicit builder =>

        import GraphDSL.Implicits._

        //step 2

        val merge = builder.add(Merge[Int](2))
        val balance = builder.add(Balance[Int](2))

        fastSource ~> merge
        slowSource ~> merge

        merge ~> balance

        balance ~> sink1
        balance ~> sink2

        ClosedShape

      }
    )
  }

  ex2BalanceGraph().run()

}
