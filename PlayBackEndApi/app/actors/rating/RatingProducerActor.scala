package Actors.Rating

import Entities.Rating
import Kafka.Topics.RatingsTopics
import Serialization.JSONSerde
import akka.Done
import akka.actor.{Actor, PoisonPill}
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.{ActorMaterializer, KillSwitches}
import akka.stream.scaladsl.{Keep, MergeHub, Source}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringSerializer}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}


class RatingProducerActor(
    implicit materializer: ActorMaterializer,
    ec: ExecutionContext
) extends Actor {

  val jSONSerde = new JSONSerde[Rating]


  //TODO : Need to make this generic and also move stuff to config
  //TODO : Need to make this generic and also move stuff to config
  //TODO : Need to make this generic and also move stuff to config
  //TODO : Need to make this generic and also move stuff to config
  //TODO : Need to make this generic and also move stuff to config
  val ratingProducerSettings = ProducerSettings(
      context.system,
      new StringSerializer,
      new ByteArraySerializer)
    .withBootstrapServers("localhost:9092")

  val ((mergeHubSink, killswitch), kafkaSourceFuture) =
    MergeHub.source[Rating](perProducerBufferSize = 16)
      .map(rating => {
        val ratingBytes = jSONSerde.serializer().serialize("", rating)
        (rating, ratingBytes)
      })
      .map { ratingWithBytes =>
        val (rating, ratingBytes) = ratingWithBytes
        new ProducerRecord[String, Array[Byte]](
          RatingsTopics.RATING_SUBMIT_TOPIC, rating.toEmail, ratingBytes)
      }
      .viaMat(KillSwitches.single)(Keep.both)
      .toMat(Producer.plainSink(ratingProducerSettings))(Keep.both)
      .run()

  kafkaSourceFuture.onComplete {
    case Success(value) => println(s"Got the callback, value = $value")
    case Failure(e) => {
      self ! PoisonPill
    }
  }

  override def postStop(): Unit = {
    super.postStop()
    println(s"RatingProducerActor seen 'Done'")
    killswitch.shutdown()
  }

  override def receive: Receive = {
    case (rating: Rating) => {
      println(s"RatingProducerActor seen ${rating}")
      Source.single(rating).runWith(mergeHubSink)
    }
    case Done => {
      println(s"RatingProducerActor seen 'Done'")
      killswitch.shutdown()
      self ! PoisonPill
    }
  }
}
