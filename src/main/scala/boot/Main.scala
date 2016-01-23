package boot

import java.net.DatagramSocket

import actors.handlers.{RegSenz, RegistrationHandler}
import scala.concurrent.duration._
import akka.actor.{ActorSystem, Props}
import akka.util.Timeout
import crypto.RSAUtils
import db.SenzCassandraCluster
import utils.SenzUtils

/**
 * Created by eranga on 1/9/16.
 */
object Main extends App with SenzCassandraCluster {

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val system = ActorSystem("senz")

  // this is the datagram socket that uses to connect to senz switch
  val socket = new DatagramSocket()

  // first generate key pair if not already generated
  RSAUtils.initRSAKeys()

  // send registration senz
  val registrationSenz = SenzUtils.getRegistrationSenz()

  val regHandler = system.actorOf(Props(classOf[RegistrationHandler], socket), name = "RegistrationHandler")
  implicit val timeout = Timeout(5 seconds)
  regHandler ! RegSenz(registrationSenz, 0)

  // initialize actors
  //  val senzSender = system.actorOf(Props(classOf[SenzSender], datagramSocket), name = "SenzSender")
  //  val senzListener = system.actorOf(Props(classOf[SenzListener], datagramSocket), name = "SenzListener")
  //  val senzReader = system.actorOf(Props[SenzReader], name = "SenzReader")
  //  val pingSender = system.actorOf(Props[PingSender], name = "PingSender")
  //
  //  // init sender and wait until its success   
  //  implicit val timeout = Timeout(5 seconds)
  //  val future = senzSender ? InitSender
  //  future onComplete {
  //    case Success(result) =>
  //      // start listener, ping sender and reader
  //      senzListener ! InitListener
  //      //pingSender ! Ping
  //      senzReader ! InitReader
  //    case Failure(result) =>
  //      println("init fails")
  //  }

  // start actor to listen + send udp
  //  val address = new InetSocketAddress(InetAddress.getByName(switchHost), switchPort)
  //  val senzUdp = system.actorOf(Props(classOf[SenzUdp], address), name = "SenzUdp")
  //
  //  // actor to send periodic ping messages
  //  val pingSender = system.actorOf(Props[PingSender], name = "PingSender")
  //
  //  // actor to listen senz messages via terminal
  //  val senzReader = system.actorOf(Props[SenzReader], name = "SenzReader")
}
