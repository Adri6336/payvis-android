package com.example.payvis

import com.google.gson.Gson
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class Time(val initialTime: LocalDateTime){
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    val startTime = initialTime.format(formatter)
    var timeAsSeconds: Double = 0.0  // This will be replaced by a value after init

    // The following values are slices from startTime string. I can think of a better way
    // to do it, but this is already here and it works so it'll stay
    val year = startTime.slice(0..3).toInt()  // here just in case I need it
    val month = startTime.slice(5..6).toInt()
    val day = startTime.slice(8..9).toInt()
    val hour = startTime.slice(11..12).toInt()
    val minute = startTime.slice(14..15).toInt()
    val second = startTime.slice(17..18).toInt()

    val dbEntryTag = startTime.split(" ")[0]  // Grabs the year, month, and day

    init {
        // To get the time as seconds, total up the num sec in month, day, ... sec
        val monthSec = this.month.toDouble() * 2628002.88
        val daySec = this.day.toDouble() * 86400.0
        val hourSec = this.hour * 3600.0
        val minSec = this.minute * 60.0
        val secSec = this.second.toDouble()

        this.timeAsSeconds = (monthSec + daySec + hourSec + minSec + secSec)
    }

    fun getElapsedTimeSec(endTime: LocalDateTime): Double{
        // This returns the elapsed time between the initialTime and endTime LocalDateTime

        val end = Time(endTime).timeAsSeconds
        val difference = end - this.timeAsSeconds
        println("DIFFERENCE: $difference")

        if (difference > 0.0){return difference}
        else {return 0.0}
    }

    fun statement(timePassed: Number, timeUnit: String): String{

        val singular = when (timeUnit){
            "hour" -> "hour"
            "min" -> "minute"
            "sec" -> "second"
            else -> "$timeUnit"
        }

        val plural = when(timeUnit){
            "hour" -> "hours"
            "min" -> "minutes"
            "sec" -> "seconds"
            else -> "${timeUnit}s"
        }

        var timeText: String

        if (timePassed.toInt() > 1) {timeText = "$timePassed $plural"}  // If there's more than one, it should be plural
        else if (timePassed.toInt() == 1) {timeText = "$timePassed $singular"}
        else {timeText = ""}

        return timeText
    }

    fun getElapsedTimeAsString(endTime: LocalDateTime, elapSec: Double = -404.4): String{
        // Returns the time that elapsed between start and endTime as string

        // 1. Get the hour and minute count of time passed
        val elapsed = when(elapSec){  // If we passed a value to elapSec, use it instead of endTime
            -404.4 -> this.getElapsedTimeSec(endTime)
            else -> elapSec
        }

        // 1.01  Get around bug that occurs with time < 1 min
        if (elapsed < 60 && elapsed > 0){
            return "${this.statement(elapsed.toInt(), "sec")} worked"
        }
        else if (elapsed < 1 && elapsed > -1){  // Catch infinitesimals
            return "No time passed"
        }

        var timeElapsed = ((elapsed / 60.0) / 60.0).toString().split(".")  //place 0 = hours, 1 = minutes
        val hours =  timeElapsed[0].toInt()
        var minutes = ("." + timeElapsed[1]).toDouble() * 60  // re-add the period before converting to d

        // 1.1 Format minutes to have at most 2 decimal places
        minutes = String.format("%.02f", minutes).toDouble()

        // 1.2 Make minutes int
        val minSeparated = minutes.toString().split(".")  // place 0 = minutes, 1 = seconds
        var minutesI = minSeparated[0].toInt()
        var seconds = (elapsed % 60).toInt() // Determine how many seconds have passed

        // 2. Create two strings with the hour and minute data
        var hourText: String
        var minText: String
        var secText: String

        hourText = this.statement(hours, "hour")
        minText = this.statement(minutesI, "min")
        secText = this.statement(seconds, "sec")

        // 3. Compile final string
        if (hours == 0 && minutesI == 0 && seconds == 0){return ""}
        else if (hours == 0 && minutesI > 0 && seconds > 0){return "$minText, $secText worked"}
        else if (hours == 0 && minutesI > 0 && seconds == 0){return "$minText worked"}
        else if (hours > 0 && minutesI == 0 && seconds > 0) {return "$hourText, $secText worked"}
        else if (hours > 0 && minutes > 0 && seconds == 0){return "$hourText, $minText worked"}
        else if (hours > 0 && minutesI == 0 && seconds == 0){return "$hourText"}
        else {return "$hourText, $minText, and $secText worked"}
    }
}


data class Clock(val startTime: Time){
    var totalSeconds: Double = 0.0
    var sessionSeconds: Double = 0.0
    var active = false
    var dataBase: MutableMap<String, Map<String, String>> = mutableMapOf()  // day : total sec, pay, start, and end times
    var rate: Double = 0.0
    var pay: Double = 0.0

    fun update(): LocalDateTime {
        val now = LocalDateTime.now()
        this.sessionSeconds = startTime.getElapsedTimeSec(now)
        return now
    }

    fun stop(){
        this.active = false
    }

    fun calculatePay(payRate: Double, secondsWorked: Double): Double{
        val payPerSec = ((payRate / 60.0) / 60.0)
        return (payPerSec * secondsWorked)
    }

    fun createDBEntry(payRate: Double, endTime: Time, reset: Boolean = false){
        /*
        This will create a map entry containing time worked and pay earned

        Note: the clock must be fully updated before this is called,
        or the database will receive an inaccurate entry
        */
        // 1. Get entry key
        val day = startTime.dbEntryTag

        // 1.2. Reset if desired
        if (reset){  // Resets day
            val data: Map<String, String> = mapOf(
                "seconds" to 0.0.toString(),
                "pay" to 0.0.toString(),
                "start" to this.startTime.startTime,
                "finish" to this.startTime.startTime
            )

            this.dataBase.put(day, data)
            return
        }

        // 2. Create entry value
        val end = endTime
        val pay = this.calculatePay(payRate, this.totalSeconds)
        val data: Map<String, String> = mapOf(
            "seconds" to this.totalSeconds.toString(),  // Total seconds should be updated before fun called
            "pay" to pay.toString(),  // This allows pay to accumulate over sessions
            "start" to this.startTime.startTime,
            "finish" to end.startTime
        )

        // 3. Create entry in db
        println("[i] DB BEFORE ADD: ${this.dataBase}")
        this.dataBase.put(day, data)
        println("[i] DB WITH ADD: ${this.dataBase}")
    }

    fun makeJsonString(): String{
        // Creates a json string of the database mutable map
        val gson = Gson()
        val dbString: String = gson.toJson(this.dataBase, this.dataBase.javaClass)
        return dbString
    }

    fun loadJsonString(data: String){
        val gson = Gson()
        val db = gson.fromJson(data, this.dataBase.javaClass)
        this.dataBase = db
    }
}