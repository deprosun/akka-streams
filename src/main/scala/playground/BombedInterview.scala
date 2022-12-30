package playground

object BombedInterview {

  // To execute Scala code, please define an object named Solution that extends App

  /*
  We have been asked to create a tool to help users manage their calendars. Given an unordered list of times of day when someone is busy, write a function that tells us whether they're available during a specified period of time.

  Each time is expressed as an integer using 24-hour notation, such as 1200 (12:00), 1530 (15:30), or 800 (8:00).

  Sample input:
  appointments = [
    [1300, 1500],
    [ 845,  900],
    [1230, 1300]
  ]

  Expected output:
  isAvailable(appointments,  830,  845)  => true
  isAvailable(appointments, 1330, 1400)  => false
  isAvailable(appointments,  830,  930)  => false
  isAvailable(appointments,  855,  930)  => false
  isAvailable(appointments, 1500, 1600)  => true
  isAvailable(appointments,  845,  900)  => false
  isAvailable(appointments, 1229, 1231)  => false

  case 1:
  ---
      ------------

  case 2:
  ---------
      ----------

   case 3:
              ------
   --------

   case 4:
      ------
   ------

   case 5:
      ----
   ----------------

  */

  def isAvailable(appointments: List[(Int, Int)], startTime: Int, endTime: Int): Boolean = {

    appointments.forall { case (s, e) =>
      val case1 = startTime <= s && endTime <= e
      val case2 = startTime <= s && !(endTime > s && endTime <= e)
      val case3 = startTime >= e
      val case4 = !(startTime > s && startTime <= e)
      val case5 = !(startTime > s && startTime <= e && endTime >= s && endTime <= e)

      case1 && case2 && case3 && case4 && case5
    }

  }

}
